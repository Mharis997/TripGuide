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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Reel(
    val code: String, val url: String, val placeKey: String, val placeName: String,
    val caption: String, val author: String, val summary: String, val tips: List<String>, val createdAt: Long,
    val prices: List<String> = emptyList(), val times: List<String> = emptyList(), val months: List<Int> = emptyList(),
    val languages: List<String> = emptyList(), val seen: String = "",
)

/** First Instagram reel/post link inside shared text (Instagram's Share sends "url" or "caption url"). */
// Covers /reel/ID, /p/ID, /user/reel/ID and Instagram's Share links /share/ID, /share/reel/ID.
fun reelLink(text: String?): String? = text?.let { Regex("""https://(www\.)?instagram\.com/[\w./-]*?(reel|reels|p|tv|share)/[A-Za-z0-9_-]+/?\S*""").find(it)?.value }

/** Embed URL for in-app playback through Instagram's official embed. */
fun embedUrl(reelUrl: String) = reelUrl.substringBefore('?').trimEnd('/') + "/embed/"

/**
 * Shared community data on Firebase (Firestore + anonymous Auth). Reel details are extracted on the phone (Extract.kt).
 * Disabled (all calls no-op / fail politely) when FIREBASE_* build values are missing.
 */
object Cloud {
    private var app: FirebaseApp? = null
    val enabled get() = app != null
    private val db get() = FirebaseFirestore.getInstance(app!!)
    private val auth get() = FirebaseAuth.getInstance(app!!)
    val uid get() = if (enabled) auth.currentUser?.uid else null
    /** Newest community reels, reported ones already hidden. */
    val reels = MutableStateFlow<List<Reel>>(emptyList())
    private const val HIDE_AT_REPORTS = 3

    fun init(ctx: Context) {
        // Unit tests (Robolectric) must never sign in to or write to the real project.
        if (BuildConfig.FIREBASE_PROJECT_ID.isBlank() || app != null || android.os.Build.FINGERPRINT == "robolectric") return
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
        prices = strings(d, "prices"), times = strings(d, "times"), languages = strings(d, "languages"), seen = d.getString("seen").orEmpty(),
        months = (d.get("months") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty(),
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

    /** Links a reel to a place for everyone, with what the phone extracted from its caption, screen text and speech. */
    suspend fun addReel(r: Reel) {
        val me = uid ?: error("Not signed in yet – check your internet connection.")
        val doc = db.collection("reels").document(r.code)
        if (doc.get().await().exists()) error("This reel is already in TripGuide.")
        doc.set(mapOf(
            "url" to r.url, "placeKey" to r.placeKey, "placeName" to r.placeName, "caption" to r.caption.take(5000), "author" to r.author,
            "summary" to r.summary.take(1000), "tips" to r.tips, "prices" to r.prices, "times" to r.times, "months" to r.months,
            "languages" to r.languages, "seen" to r.seen.take(5000),
            "addedBy" to me, "createdAt" to System.currentTimeMillis(), "reportedBy" to emptyList<String>(),
        )).await()
    }

    /**
     * Reads a public reel's caption and author on the phone: first from Instagram's official embed page (no login needed),
     * then from the reel page's preview tags. Empty when the reel is private or removed.
     */
    suspend fun fetchCaption(url: String): Pair<String, String> = withContext(Dispatchers.IO) {
        fun page(u: String) = runCatching {
            val c = open(u)
            // A redirect means "log in to see this"; that page's text is not the reel's caption.
            if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
        }.getOrNull()
        page(url.trimEnd('/') + "/embed/captioned/")?.let(ReelPage::embedCaption)
            ?: page(url)?.let { ReelPage.captionAndAuthor(ReelPage.tags(it)) }?.takeIf { it.first.isNotBlank() }
            ?: ("" to "")
    }

    /** Coordinates and region for a place name found in a reel, via Android's built-in geocoder (no key). */
    @Suppress("DEPRECATION") // the blocking call works on all API levels; we're already on a background thread
    suspend fun locate(ctx: Context, name: String, hint: String): android.location.Address? = withContext(Dispatchers.IO) {
        if (name.isBlank() || !android.location.Geocoder.isPresent()) return@withContext null
        runCatching {
            android.location.Geocoder(ctx, java.util.Locale("en", "IN")).getFromLocationName(listOf(name, hint, "India").filter { it.isNotBlank() }.joinToString(", "), 1)
                ?.firstOrNull()
        }.getOrNull()
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000; readTimeout = 15_000; instanceFollowRedirects = false
        setRequestProperty("User-Agent", "TripGuide/${BuildConfig.VERSION_NAME} (link preview)")
        setRequestProperty("Accept-Language", "en")
    }

    /** Instagram Share links (instagram.com/share/…) redirect to the reel; follows up to 3 hops to its canonical address. */
    suspend fun resolveReel(link: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        var url = link
        repeat(4) {
            ReelPage.canonical(url)?.let { return@withContext it }
            if (!ReelPage.isShareLink(url)) return@withContext null
            url = runCatching { open(url).run { getHeaderField("Location")?.let { URL(URL(url), it).toString() } } }.getOrNull() ?: return@withContext null
        }
        null
    }

    /** One report per user; content with 3+ reports is hidden for everyone. */
    suspend fun report(collection: String, id: String) {
        val me = uid ?: return
        db.collection(collection).document(id).update("reportedBy", FieldValue.arrayUnion(me)).await()
    }
}
