package com.tripguide.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

const val PRIVACY_URL = "https://mharis997.github.io/tripguide/privacy-policy.html"
const val RULES_URL = "https://mharis997.github.io/tripguide/community-rules.html"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    val version = remember { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }

    fun rescheduleAll() = scope.launch { App.dao.trips().first().forEach { Notify.scheduleTrip(ctx, it) } }
    fun useGps() = Util.lastLocation(ctx)?.let {
        Prefs.homeLat = it.latitude.toFloat(); Prefs.homeLng = it.longitude.toFloat(); Prefs.homeName = "My location"
    } ?: Toast.makeText(ctx, "Location not available yet – pick a city instead.", Toast.LENGTH_LONG).show()
    val locPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) useGps() }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Group("Profile")
            OutlinedTextField(Prefs.userName, { Prefs.userName = it.take(40) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text("Display name (shown on your updates)") }, singleLine = true)

            Group("Home location")
            Choice(Icons.Default.Home, "Home city", Prefs.homeName, Catalog.cities.map { it.name }) { n ->
                Catalog.cities.first { it.name == n }.let { Prefs.homeName = it.name; Prefs.homeLat = it.lat.toFloat(); Prefs.homeLng = it.lng.toFloat() }
            }
            Item(Icons.Default.MyLocation, "Use my current location", "Used for distances and travel estimates") {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) useGps()
                else locPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }

            Group("Appearance")
            val themes = listOf("light" to "Light", "dark" to "Dark", "system" to "System")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                themes.forEachIndexed { i, (value, label) ->
                    SegmentedButton(Prefs.theme == value, { Prefs.theme = value }, SegmentedButtonDefaults.itemShape(i, themes.size),
                        icon = { SegmentedButtonDefaults.Icon(Prefs.theme == value) {
                            Icon(listOf(Icons.Default.LightMode, Icons.Default.DarkMode, Icons.Default.BrightnessAuto)[i], null, Modifier.size(18.dp))
                        } }) { Text(label) }
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= 31) Toggle(Icons.Default.Palette, "Dynamic colours", "Match your wallpaper", Prefs.dynamicColor) { Prefs.dynamicColor = it }

            Group("Units")
            Choice(Icons.Default.Payments, "Currency", Prefs.currency, Catalog.currencies) { Prefs.currency = it }
            Toggle(Icons.Default.Straighten, "Use miles", "Instead of kilometres", Prefs.miles) { Prefs.miles = it }

            Group("Notifications")
            Toggle(Icons.Default.Campaign, "Place update alerts", "When a place on your upcoming trip gets an update", Prefs.notifyUpdates) { Prefs.notifyUpdates = it }
            Toggle(Icons.Default.Alarm, "Trip reminders", "Before departure", Prefs.notifyTrips) { Prefs.notifyTrips = it; rescheduleAll() }
            val leads = mapOf(1 to "1 hour", 6 to "6 hours", 24 to "1 day", 48 to "2 days", 72 to "3 days")
            Choice(Icons.Default.Schedule, "Remind me", "${leads[Prefs.reminderHours] ?: "${Prefs.reminderHours} h"} before", leads.values.toList()) { v ->
                Prefs.reminderHours = leads.entries.first { it.value == v }.key; rescheduleAll()
            }
            Item(Icons.Default.NotificationsActive, "System notification settings", null) {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }

            Group("Reading Indian languages")
            Text("Packs used to read on-screen text in videos and photos (offline). Captions and speech in any of these languages are recognised automatically.",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            var packsVersion by remember { mutableIntStateOf(0) } // re-read install state after downloads
            Extract.scripts.forEach { sc ->
                val on = sc.ocr in Prefs.ocrLangs.split(',')
                val have = remember(packsVersion) { Ocr.installed(ctx, sc.ocr) }
                Toggle(Icons.Default.Translate, sc.language, if (have) "Downloaded" else "Downloads when first needed (1–15 MB)", on) { enable ->
                    Prefs.ocrLangs = (Prefs.ocrLangs.split(',').filter { it.isNotBlank() && it != sc.ocr } + if (enable) listOf(sc.ocr) else emptyList()).joinToString(",")
                    if (enable && !have) scope.launch {
                        runCatching { Ocr.download(ctx, sc.ocr) }.onFailure { Toast.makeText(ctx, "Download failed: ${it.message}", Toast.LENGTH_LONG).show() }
                        packsVersion++
                    }
                    if (!enable) { Ocr.delete(ctx, sc.ocr); packsVersion++ }
                }
            }

            Group("Data")
            Item(Icons.Default.DeleteForever, "Delete all my data", "Trips, places, updates and photos") {
                confirm = "Delete everything? This cannot be undone." to {
                    App.dao.trips().first().forEach { Notify.cancelTrip(ctx, it.id) }
                    App.dao.clearAll(); File(ctx.filesDir, "photos").deleteRecursively()
                }
            }

            Group("About")
            Item(Icons.Default.Star, "Rate TripGuide", null) { Util.open(ctx, "market://details?id=${ctx.packageName}") }
            Item(Icons.Default.Policy, "Privacy policy", null) { Util.open(ctx, PRIVACY_URL) }
            Item(Icons.Default.Gavel, "Community rules", null) { Util.open(ctx, RULES_URL) }
            Item(Icons.Default.Groups, "Community: " + when {
                !Cloud.enabled -> "not set up in this build"
                Cloud.uid == null -> "connecting…"
                else -> "connected"
            }, "Reels and places you add are shared with all TripGuide users") {}
            Item(Icons.Default.Info, "Version $version", "Weather by Open-Meteo.com · Reels © their creators, shown via Instagram's embed") {}
        }
    }

    confirm?.let { (msg, action) ->
        AlertDialog({ confirm = null }, text = { Text(msg) },
            confirmButton = { TextButton({ scope.launch { action(); confirm = null; Toast.makeText(ctx, "Done", Toast.LENGTH_SHORT).show() } }) { Text("Confirm") } },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } })
    }
}

@Composable
private fun Group(title: String) = Text(title, Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

@Composable
private fun Item(icon: ImageVector, title: String, sub: String?, onClick: () -> Unit) =
    ListItem({ Text(title) }, Modifier.clickable(onClick = onClick),
        supportingContent = sub?.let { { Text(it) } }, leadingContent = { Icon(icon, null) })

@Composable
private fun Toggle(icon: ImageVector, title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) =
    ListItem({ Text(title) }, supportingContent = sub?.let { { Text(it) } }, leadingContent = { Icon(icon, null) },
        trailingContent = { Switch(value, onChange) })

@Composable
private fun Choice(icon: ImageVector, title: String, value: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Item(icon, title, value) { open = true }
        DropdownMenu(open, { open = false }) {
            options.forEach { o -> DropdownMenuItem({ Text(o) }, { onPick(o); open = false }) }
        }
    }
}
