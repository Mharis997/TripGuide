package com.tripguide.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

/** A live Google Maps result. Never stored: Google's terms forbid caching Places content. */
data class GPlace(
    val id: String, val name: String, val address: String, val type: String, val lat: Double, val lng: Double,
    val rating: Double?, val ratings: Int, val priceLevel: String?, val priceRange: String?,
    val photo: String?, val photoBy: String?, val mapsUri: String,
)

/** Google Places API (New) over REST. Key comes from local.properties PLACES_API_KEY at build time. */
object Google {
    val enabled get() = BuildConfig.PLACES_API_KEY.isNotBlank()
    val attractionTypes = listOf("tourist_attraction", "historical_landmark", "museum", "national_park", "park", "hiking_area")
    val stayTypes = listOf("lodging")
    private const val FIELDS = "places.id,places.displayName,places.formattedAddress,places.primaryTypeDisplayName,places.location," +
        "places.rating,places.userRatingCount,places.priceLevel,places.priceRange,places.photos,places.googleMapsUri"
    private val levels = mapOf("PRICE_LEVEL_FREE" to "Free", "PRICE_LEVEL_INEXPENSIVE" to "$", "PRICE_LEVEL_MODERATE" to "$$",
        "PRICE_LEVEL_EXPENSIVE" to "$$$", "PRICE_LEVEL_VERY_EXPENSIVE" to "$$$$")

    // shortcut: 10 results per call keeps usage inside Google's free monthly cap; raise once billing is budgeted.
    suspend fun nearby(ctx: Context, lat: Double, lng: Double, types: List<String>) = post(ctx, "searchNearby", JSONObject()
        .put("includedTypes", JSONArray(types)).put("maxResultCount", 10).put("rankPreference", "POPULARITY")
        .put("locationRestriction", JSONObject().put("circle", JSONObject().put("radius", 30_000.0)
            .put("center", JSONObject().put("latitude", lat).put("longitude", lng)))))

    suspend fun search(ctx: Context, query: String) = post(ctx, "searchText", JSONObject().put("textQuery", query).put("pageSize", 10))

    fun photoUrl(name: String) = "https://places.googleapis.com/v1/$name/media?maxWidthPx=800&key=${BuildConfig.PLACES_API_KEY}"

    private suspend fun post(ctx: Context, method: String, body: JSONObject): Result<List<GPlace>> = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("https://places.googleapis.com/v1/places:$method").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15_000; c.readTimeout = 15_000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("X-Goog-Api-Key", BuildConfig.PLACES_API_KEY)
            c.setRequestProperty("X-Goog-FieldMask", FIELDS)
            headers(ctx).forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode != 200) {
                val err = c.errorStream?.bufferedReader()?.readText().orEmpty()
                error(runCatching { JSONObject(err).getJSONObject("error").getString("message") }.getOrDefault("Google returned ${c.responseCode}"))
            }
            parse(c.inputStream.bufferedReader().readText())
        }
    }

    fun parse(json: String): List<GPlace> {
        val arr = JSONObject(json).optJSONArray("places") ?: return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val photo = o.optJSONArray("photos")?.optJSONObject(0)
            val loc = o.getJSONObject("location")
            GPlace(
                id = o.getString("id"), name = o.getJSONObject("displayName").getString("text"),
                address = o.optString("formattedAddress"), type = o.optJSONObject("primaryTypeDisplayName")?.optString("text").orEmpty(),
                lat = loc.getDouble("latitude"), lng = loc.getDouble("longitude"),
                rating = o.optDouble("rating").takeUnless { it.isNaN() }, ratings = o.optInt("userRatingCount"),
                priceLevel = levels[o.optString("priceLevel")], priceRange = o.optJSONObject("priceRange")?.let(::range),
                photo = photo?.optString("name"), photoBy = photo?.optJSONArray("authorAttributions")?.optJSONObject(0)?.optString("displayName"),
                mapsUri = o.optString("googleMapsUri"),
            )
        }
    }

    private fun range(r: JSONObject): String? {
        fun money(m: JSONObject?) = m?.let { "${it.optString("currencyCode")} ${"%,d".format(it.optString("units", "0").toLong())}" }
        val start = money(r.optJSONObject("startPrice")) ?: return null
        return money(r.optJSONObject("endPrice"))?.let { "$start – ${it.substringAfter(' ')}" } ?: "from $start"
    }

    /** Lets an Android-restricted key work over REST (Google checks package + signing cert). */
    private var cached: Map<String, String>? = null
    fun headers(ctx: Context): Map<String, String> = cached ?: run {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val sig = if (Build.VERSION.SDK_INT >= 28)
            pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo!!.apkContentsSigners[0]
        else pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures!![0]
        val sha1 = MessageDigest.getInstance("SHA-1").digest(sig.toByteArray()).joinToString("") { "%02X".format(it) }
        mapOf("X-Android-Package" to ctx.packageName, "X-Android-Cert" to sha1).also { cached = it }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    /** Live hotel prices are shown by Booking.com for the exact dates and group; Google sells no price feed to apps. */
    fun bookingUrl(where: String, checkIn: Long?, checkOut: Long?, adults: Int) = buildString {
        append("https://www.booking.com/searchresults.html?ss=${enc(where)}&group_adults=$adults&no_rooms=1")
        if (checkIn != null && checkOut != null) append("&checkin=${day(checkIn)}&checkout=${day(checkOut)}")
    }

    fun googleHotelsUrl(where: String) = "https://www.google.com/travel/search?q=${enc("hotels near $where")}"
}
