package com.tripguide.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.core.net.toUri
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Reel(
    val code: String, val url: String, val placeKey: String, val placeName: String,
    val caption: String, val author: String, val summary: String, val tips: List<String>, val createdAt: Long,
)

data class PlaceSummary(
    val summary: String, val highlights: List<String>, val guide: String, val tips: List<String>, val costNotes: String,
    val bestTime: String, val howToReach: String, val warnings: List<String>, val reelCount: Int,
)

/** What the server read from a reel. [info] is null when Instagram withheld the caption. */
data class ReelAnalysis(val code: String, val url: String, val caption: String, val author: String, val info: JSONObject?, val error: String?)

/** First Instagram reel/post link inside shared text (Instagram's Share sends "url" or "caption url"). */
fun reelLink(text: String?): String? = text?.let { Regex("""https://(www\.)?instagram\.com/[\w./-]*?(reel|reels|p|tv)/[A-Za-z0-9_-]+/?\S*""").find(it)?.value }

/** Evenly spaced frame times (µs) across a video, skipping the very start and end (often black or titles). */
fun frameTimesUs(durationMs: Long, n: Int = 8): List<Long> =
    if (durationMs <= 0) listOf(0L) else List(n) { i -> durationMs * 1000 * (2 * i + 1) / (2 * n) }

/** Reads [n] frames from a local video on-device as small base64 JPEGs for the AI. Nothing else of the video leaves the phone. */
suspend fun videoFrames(ctx: Context, uri: android.net.Uri, n: Int = 8): List<String> = withContext(Dispatchers.IO) {
    val r = android.media.MediaMetadataRetriever()
    try {
        r.setDataSource(ctx, uri)
        val dur = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        frameTimesUs(dur, n).mapNotNull { t -> r.getFrameAtTime(t, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }.map { raw ->
            val scale = 768f / maxOf(raw.width, raw.height)
            val bmp = if (scale < 1) Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true) else raw
            Base64.encodeToString(ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray(), Base64.NO_WRAP)
        }
    } finally { r.release() }
}

/** Embed URL for in-app playback through Instagram's official embed. */
fun embedUrl(reelUrl: String) = reelUrl.substringBefore('?').trimEnd('/') + "/embed/"

/** Closest existing place for a name the AI read from a reel, or null. */
fun bestMatch(places: List<Place>, name: String): Place? {
    val n = name.lowercase().trim().ifEmpty { return null }
    fun words(s: String) = s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 }.toSet()
    return places.firstOrNull { it.name.lowercase() == n }
        ?: places.firstOrNull { val p = it.name.lowercase().substringBefore(" ("); n.contains(p) || p.contains(n) }
        ?: places.map { it to (words(it.name) intersect words(name)).size }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
}

/**
 * Shared community data on Firebase (Firestore + anonymous Auth) and the AI server.
 * Disabled (all calls no-op / fail politely) when FIREBASE_* build values are missing.
 */
object Cloud {
    private var app: FirebaseApp? = null
    val enabled get() = app != null
    val aiEnabled get() = enabled && BuildConfig.TRIPGUIDE_API_URL.isNotBlank()
    private val db get() = FirebaseFirestore.getInstance(app!!)
    private val auth get() = FirebaseAuth.getInstance(app!!)
    val uid get() = if (enabled) auth.currentUser?.uid else null
    /** Newest community reels, reported ones already hidden. */
    val reels = MutableStateFlow<List<Reel>>(emptyList())
    private const val HIDE_AT_REPORTS = 3

    fun init(ctx: Context) {
        if (BuildConfig.FIREBASE_PROJECT_ID.isBlank() || app != null) return
        app = FirebaseApp.initializeApp(ctx, FirebaseOptions.Builder().setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID).setApiKey(BuildConfig.FIREBASE_API_KEY).build(), "tripguide")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope.launch { runCatching { if (auth.currentUser == null) auth.signInAnonymously().await() } }
        db.collection("reels").orderBy("createdAt", Query.Direction.DESCENDING).limit(500).addSnapshotListener { snap, _ ->
            snap?.let { s -> reels.value = s.documents.filter { reports(it) < HIDE_AT_REPORTS }.map(::reel) }
        }
        db.collection("places").addSnapshotListener { snap, _ ->
            snap?.documentChanges?.let { changes -> scope.launch(Dispatchers.IO) { changes.forEach { runCatching { sync(ctx, it) } } } }
        }
    }

    private fun reports(d: DocumentSnapshot) = (d.get("reportedBy") as? List<*>)?.size ?: 0
    @Suppress("UNCHECKED_CAST")
    private fun strings(d: DocumentSnapshot, f: String) = (d.get(f) as? List<*>)?.filterIsInstance<String>().orEmpty()

    private fun reel(d: DocumentSnapshot) = Reel(
        code = d.id, url = d.getString("url").orEmpty(), placeKey = d.getString("placeKey").orEmpty(),
        placeName = d.getString("placeName").orEmpty(), caption = d.getString("caption").orEmpty(),
        author = d.getString("author").orEmpty(), summary = d.getString("summary").orEmpty(),
        tips = strings(d, "tips"), createdAt = d.getLong("createdAt") ?: 0,
    )

    /** Mirrors community places into Room so search, filters, planner and offline use work unchanged. */
    private suspend fun sync(ctx: Context, change: DocumentChange) {
        val d = change.document
        if (change.type == DocumentChange.Type.REMOVED || reports(d) >= HIDE_AT_REPORTS) return App.dao.deleteCommunityPlace(d.id)
        val old = App.dao.placeByKey(d.id)
        if (old?.userAdded == true) return // keep my local originals
        val dir = File(ctx.filesDir, "community").apply { mkdirs() }
        val photos = strings(d, "photos").mapIndexedNotNull { i, b64 ->
            runCatching { File(dir, "${d.id}_$i.jpg").apply { writeBytes(Base64.decode(b64, Base64.NO_WRAP)) }.toUri().toString() }.getOrNull()
        }
        App.dao.upsert(Place(
            id = old?.id ?: 0, key = d.id, saved = old?.saved.orEmpty(), userAdded = d.getString("addedBy") == uid,
            name = d.getString("name").orEmpty(), region = d.getString("region").orEmpty(),
            lat = d.getDouble("lat") ?: 0.0, lng = d.getDouble("lng") ?: 0.0,
            category = d.getString("category") ?: Catalog.categories.first(), vibes = strings(d, "vibes"),
            amenities = strings(d, "amenities"), transport = strings(d, "transport"),
            description = d.getString("description").orEmpty(), howToReach = d.getString("howToReach").orEmpty(),
            guide = d.getString("guide").orEmpty(), tips = d.getString("tips").orEmpty(), safety = d.getString("safety").orEmpty(),
            bestMonths = (d.get("bestMonths") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty(),
            dailyCost = d.getLong("dailyCost")?.toInt() ?: 0, minDays = d.getLong("minDays")?.toInt() ?: 1,
            photos = photos, photoCredits = "Shared by a TripGuide traveller", updatedAt = d.getLong("updatedAt") ?: 0,
        ))
    }

    // shortcut: photos go into the Firestore doc as small JPEGs because Firebase Storage now needs the paid Blaze plan;
    // move them to Storage if places grow past a few hundred.
    private fun compress(ctx: Context, uri: String): String? = runCatching {
        val bytes = ctx.contentResolver.openInputStream(uri.toUri())!!.use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 960) sample *= 2
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })!!
        val scale = 960f / maxOf(raw.width, raw.height)
        val bmp = if (scale < 1) Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true) else raw
        Base64.encodeToString(ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 60, it) }.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    /** Shares a place I created with all users; returns it with its community key set. No-op when Firebase isn't configured. */
    suspend fun publish(ctx: Context, p: Place): Place {
        val me = uid ?: return p
        val isNew = p.key.isEmpty()
        val key = p.key.ifEmpty { db.collection("places").document().id }
        val photos = withContext(Dispatchers.IO) { p.photos.take(3).mapNotNull { compress(ctx, it) } }
        val data = mapOf(
            "name" to p.name, "region" to p.region, "lat" to p.lat, "lng" to p.lng, "category" to p.category,
            "vibes" to p.vibes, "amenities" to p.amenities, "transport" to p.transport, "description" to p.description,
            "howToReach" to p.howToReach, "guide" to p.guide, "tips" to p.tips, "safety" to p.safety, "bestMonths" to p.bestMonths,
            "dailyCost" to p.dailyCost, "minDays" to p.minDays, "photos" to photos, "addedBy" to me, "updatedAt" to System.currentTimeMillis(),
        )
        val doc = db.collection("places").document(key)
        // Offline, Firestore queues the write and syncs later; don't block the save screen waiting for the server.
        withTimeoutOrNull(15_000) { (if (isNew) doc.set(data + ("reportedBy" to emptyList<String>())) else doc.update(data)).await() }
        return p.copy(key = key)
    }

    suspend fun deletePlace(key: String) { if (enabled && key.isNotEmpty()) runCatching { db.collection("places").document(key).delete().await() } }

    private suspend fun api(path: String, body: JSONObject): JSONObject {
        val token = auth.currentUser?.getIdToken(false)?.await()?.token ?: error("Not signed in yet – check your internet connection.")
        return withContext(Dispatchers.IO) {
            val c = URL(BuildConfig.TRIPGUIDE_API_URL.trimEnd('/') + path).openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 20_000; c.readTimeout = 120_000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Authorization", "Bearer $token")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val ok = c.responseCode in 200..299
            val o = JSONObject((if (ok) c.inputStream else c.errorStream).bufferedReader().use { it.readText() })
            if (!ok) error(o.optString("error", "Server error ${c.responseCode}"))
            o
        }
    }

    /** Reads a reel's caption and preview image on the server and extracts travel info with Claude. */
    suspend fun analyze(url: String?, caption: String = "", frames: List<String> = emptyList()): ReelAnalysis {
        val body = JSONObject().put("caption", caption).apply { url?.let { put("url", it) }; if (frames.isNotEmpty()) put("frames", JSONArray(frames)) }
        val o = api("/reel", body)
        return ReelAnalysis(o.optString("code"), o.optString("url"), o.optString("caption"), o.optString("author"),
            o.optJSONObject("info"), o.optString("error").ifEmpty { null })
    }

    /** Links a reel to a place for everyone, then rebuilds that place's AI guide from all its reels. */
    suspend fun addReel(a: ReelAnalysis, place: Place) {
        val me = uid ?: error("Not signed in yet – check your internet connection.")
        val doc = db.collection("reels").document(a.code)
        if (doc.get().await().exists()) error("This reel is already in TripGuide.")
        fun list(f: String) = a.info?.optJSONArray(f)?.let { arr -> List(arr.length()) { arr.getString(it) } }.orEmpty()
        doc.set(mapOf(
            "url" to a.url, "placeKey" to place.cloudKey, "placeName" to place.name, "caption" to a.caption.take(5000),
            "author" to a.author, "summary" to a.info?.optString("summary").orEmpty(), "tips" to list("tips"),
            "addedBy" to me, "createdAt" to System.currentTimeMillis(), "reportedBy" to emptyList<String>(),
        )).await()
        refreshSummary(place)
    }

    suspend fun refreshSummary(place: Place) {
        val docs = db.collection("reels").whereEqualTo("placeKey", place.cloudKey).get().await().documents.filter { reports(it) < HIDE_AT_REPORTS }
        if (docs.isEmpty()) return
        // Caption plus what the AI saw in the video when the reel was added (on-screen text, signboards).
        val reels = JSONArray(docs.map { d ->
            val seen = listOf(d.getString("summary").orEmpty()) + strings(d, "tips")
            JSONObject().put("author", d.getString("author")).put("caption", d.getString("caption") + "\n[Seen in video] " + seen.filter { it.isNotBlank() }.joinToString(" | "))
        })
        val s = api("/summarize", JSONObject().put("place", place.name).put("region", place.region).put("reels", reels))
        fun list(f: String) = s.optJSONArray(f)?.let { arr -> List(arr.length()) { arr.getString(it) } }.orEmpty()
        db.collection("summaries").document(place.cloudKey).set(mapOf(
            "summary" to s.optString("summary"), "highlights" to list("highlights"), "guide" to s.optString("guide"),
            "tips" to list("tips"), "costNotes" to s.optString("costNotes"), "bestTime" to s.optString("bestTime"),
            "howToReach" to s.optString("howToReach"), "warnings" to list("warnings"), "reelCount" to docs.size,
            "updatedAt" to System.currentTimeMillis(), "updatedBy" to uid,
        )).await()
    }

    fun summary(placeKey: String): Flow<PlaceSummary?> = if (!enabled) flowOf(null) else callbackFlow {
        val reg = db.collection("summaries").document(placeKey).addSnapshotListener { d, _ ->
            trySend(d?.takeIf { it.exists() }?.let {
                PlaceSummary(it.getString("summary").orEmpty(), strings(it, "highlights"), it.getString("guide").orEmpty(),
                    strings(it, "tips"), it.getString("costNotes").orEmpty(), it.getString("bestTime").orEmpty(),
                    it.getString("howToReach").orEmpty(), strings(it, "warnings"), it.getLong("reelCount")?.toInt() ?: 0)
            })
        }
        awaitClose { reg.remove() }
    }

    /** One report per user; content with 3+ reports is hidden for everyone. */
    suspend fun report(collection: String, id: String) {
        val me = uid ?: return
        db.collection(collection).document(id).update("reportedBy", FieldValue.arrayUnion(me)).await()
    }
}
