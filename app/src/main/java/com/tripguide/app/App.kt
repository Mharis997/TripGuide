package com.tripguide.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.room.Room
import androidx.work.*
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        db = Room.databaseBuilder(this, AppDb::class.java, "tripguide.db").addMigrations(MIGRATION_1_2).build()
        Notify.createChannels(this)
        Cloud.init(this)
        CoroutineScope(Dispatchers.IO).launch { Seed.load(this@App, db.dao()) }
    }

    // Wikimedia throttles generic HTTP-client user agents; identify the app.
    override fun newImageLoader() = ImageLoader.Builder(this).okHttpClient {
        OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "TripGuide/${BuildConfig.VERSION_NAME} (Android)").build())
        }.build()
    }.build()

    companion object {
        lateinit var db: AppDb
        val dao get() = db.dao()
    }
}

object Notify {
    private const val UPDATES = "updates"
    private const val TRIPS = "trips"

    fun createChannels(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
            NotificationChannel(UPDATES, "Place updates", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Alerts when a place on your upcoming trip gets a new update" },
            NotificationChannel(TRIPS, "Trip reminders", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Reminders before your departure" },
        ))
    }

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, channel: String, id: Int, title: String, text: String, route: String) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33) return
        val intent = Intent(ctx, MainActivity::class.java).putExtra("route", route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pi = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        NotificationManagerCompat.from(ctx).notify(id, NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(pi).setAutoCancel(true).build())
    }

    /**
     * Alerts everyone with an upcoming trip to the place.
     * shortcut: offline build only reaches trips on this device; with a backend, fan out via push (FCM topic per place).
     */
    suspend fun placeUpdated(ctx: Context, place: Place, u: PlaceUpdate) {
        if (!Prefs.notifyUpdates) return
        App.dao.upcomingTripsTo(place.id, System.currentTimeMillis()).forEach { t ->
            post(ctx, UPDATES, (u.id * 31 + t.id).toInt(), "${u.type}: ${place.name}", "Update for your trip \"${t.title}\": ${u.text}", "trip/${t.id}")
        }
    }

    fun scheduleTrip(ctx: Context, trip: Trip) {
        val wm = WorkManager.getInstance(ctx)
        val at = trip.departAt - Prefs.reminderHours * 3_600_000L
        if (!Prefs.notifyTrips || at < System.currentTimeMillis()) { cancelTrip(ctx, trip.id); return }
        wm.enqueueUniqueWork("trip_${trip.id}", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<TripReminder>()
            .setInitialDelay(at - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("tripId" to trip.id)).build())
    }

    fun cancelTrip(ctx: Context, tripId: Long) = WorkManager.getInstance(ctx).cancelUniqueWork("trip_$tripId")

    class TripReminder(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result {
            val trip = App.dao.getTrip(inputData.getLong("tripId", -1)) ?: return Result.success()
            val place = App.dao.getPlace(trip.placeId)
            post(applicationContext, TRIPS, trip.id.toInt(), "Trip coming up: ${trip.title}",
                "You leave for ${place?.name ?: "your trip"} soon. Check the packing list and latest updates.", "trip/${trip.id}")
            return Result.success()
        }
    }
}

object Util {
    /** Copies a picked image into app storage so it survives the picker's temporary permission. */
    // shortcut: stores the original file without resizing; add downscaling if storage use becomes a complaint.
    suspend fun importPhoto(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(ctx.filesDir, "photos").apply { mkdirs() }
            val out = File(dir, "${UUID.randomUUID()}.jpg")
            ctx.contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { i.copyTo(it) } }
            out.toUri().toString()
        }.getOrNull()
    }

    fun share(ctx: Context, text: String) =
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))

    /** Starts Google Maps turn-by-turn straight away; it hands over to Android Auto when the phone is connected. */
    fun navigate(ctx: Context, lat: Double, lng: Double) = runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, "google.navigation:q=$lat,$lng&mode=d".toUri()).setPackage("com.google.android.apps.maps"))
    }.recoverCatching { open(ctx, "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=driving&dir_action=navigate") }

    fun open(ctx: Context, url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }

    @SuppressLint("MissingPermission") // caller checks ACCESS_COARSE_LOCATION
    fun lastLocation(ctx: Context): android.location.Location? {
        val lm = ctx.getSystemService(LocationManager::class.java)
        return lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
    }

    data class Day(val date: String, val max: Double, val min: Double, val rain: Int, val code: Int)

    /** 7-day forecast from Open-Meteo (free, no API key). Empty list when offline. */
    suspend fun forecast(lat: Double, lng: Double): List<Day> = withContext(Dispatchers.IO) {
        runCatching {
            val d = JSONObject(URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=7").readText())
                .getJSONObject("daily")
            List(d.getJSONArray("time").length()) { i ->
                Day(d.getJSONArray("time").getString(i), d.getJSONArray("temperature_2m_max").optDouble(i),
                    d.getJSONArray("temperature_2m_min").optDouble(i), d.getJSONArray("precipitation_probability_max").optInt(i),
                    d.getJSONArray("weather_code").optInt(i))
            }
        }.getOrDefault(emptyList())
    }

    fun weatherEmoji(code: Int) = when (code) {
        0 -> "☀️"; in 1..3 -> "⛅"; 45, 48 -> "🌫️"; in 51..67, in 80..82 -> "🌧️"; in 71..77, 85, 86 -> "❄️"; in 95..99 -> "⛈️"; else -> "🌤️"
    }
}
