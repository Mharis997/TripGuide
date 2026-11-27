package com.tripguide.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** App settings backed by SharedPreferences and observable from Compose. */
object Prefs {
    private lateinit var sp: SharedPreferences
    fun init(ctx: Context) { sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    var userName by pref("userName", "Traveler")
    var theme by pref("theme", "system") // system | light | dark
    var dynamicColor by pref("dynamicColor", true)
    var currency by pref("currency", "INR")
    var miles by pref("miles", false)
    var homeName by pref("homeName", "Kochi")
    var homeLat by pref("homeLat", 9.9312f)
    var homeLng by pref("homeLng", 76.2673f)
    var notifyUpdates by pref("notifyUpdates", true)
    var notifyTrips by pref("notifyTrips", true)
    var reminderHours by pref("reminderHours", 24)
    var askedNotifications by pref("askedNotifications", false)
    /** Tesseract packs used for on-screen text, comma-separated (eng, mal, tam, hin…). */
    var ocrLangs by pref("ocrLangs", "eng,mal")

    @Suppress("UNCHECKED_CAST")
    private fun <T> pref(key: String, def: T) = object : ReadWriteProperty<Any?, T> {
        private val state by lazy {
            mutableStateOf(when (def) {
                is String -> sp.getString(key, def)
                is Boolean -> sp.getBoolean(key, def)
                is Int -> sp.getInt(key, def)
                is Float -> sp.getFloat(key, def)
                else -> error("unsupported $key")
            } as T)
        }
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            sp.edit().apply {
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Float -> putFloat(key, value)
                }
            }.apply()
            state.value = value
        }
    }

    fun money(v: Int) = "$currency ${"%,d".format(v)}"
    fun distance(km: Int) = if (miles) "${(km * 0.621371).toInt()} mi" else "$km km"
}
