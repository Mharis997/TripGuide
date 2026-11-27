package com.tripguide.app

import android.Manifest
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

private fun JSONObject.list(f: String) = optJSONArray(f)?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()

/** Paste or share an Instagram reel → server reads it → link to a place (or create one) → guide is rebuilt. */
@Composable
fun AddReelScreen(nav: NavController, sharedUrl: String?, placeId: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val places by App.dao.places().collectAsState(emptyList())
    var url by rememberSaveable { mutableStateOf(sharedUrl.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ReelAnalysis?>(null) }
    var target by remember { mutableStateOf<Place?>(null) }
    var newPlace by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lng by remember { mutableStateOf("") }

    LaunchedEffect(placeId, places) { if (target == null && placeId >= 0) target = places.firstOrNull { it.id == placeId } }

    fun read() {
        val link = reelLink(url) ?: run { error = "Paste an Instagram reel link (instagram.com/reel/…)."; return }
        busy = true; error = null; result = null
        scope.launch(Dispatchers.Main) {
            runCatching { Cloud.analyze(link) }.onSuccess { a ->
                result = a; error = a.error
                val info = a.info
                if (info != null && placeId < 0) {
                    target = bestMatch(places, info.optString("placeName"))
                    newPlace = target == null && info.optBoolean("isTravelPlace")
                    name = info.optString("placeName"); region = info.optString("region")
                }
            }.onFailure { error = it.message }
            busy = false
        }
    }
    LaunchedEffect(Unit) { if (sharedUrl != null && Cloud.aiEnabled) read() }

    fun fillLocation() = Util.lastLocation(ctx)?.let { lat = "%.5f".format(it.latitude); lng = "%.5f".format(it.longitude) }
        ?: Toast.makeText(ctx, "Location not available yet – type the coordinates.", Toast.LENGTH_LONG).show()
    val locPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) fillLocation() }

    fun save() {
        val a = result ?: return
        val info = a.info ?: JSONObject()
        busy = true; error = null
        scope.launch(Dispatchers.Main) {
            runCatching {
                val place = if (!newPlace) target ?: error("Choose the place this reel is about.") else {
                    val la = lat.toDoubleOrNull()?.takeIf { it in -90.0..90.0 }; val ln = lng.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
                    require(name.isNotBlank() && region.isNotBlank()) { "Enter the place name and region." }
                    require(la != null && ln != null) { "Enter valid coordinates (or use your location)." }
                    val draft = Place(
                        name = name.trim(), region = region.trim(), lat = la, lng = ln, userAdded = true,
                        category = info.optString("category").takeIf { it in Catalog.categories } ?: Catalog.categories.first(),
                        vibes = info.list("vibes").filter { it in Catalog.vibes }, amenities = info.list("amenities").filter { it in Catalog.amenities },
                        description = info.optString("summary"), howToReach = info.optString("howToReach"),
                        guide = info.list("highlights").joinToString("\n") { "• $it" }, tips = info.list("tips").joinToString("\n") { "• $it" },
                    )
                    val shared = Cloud.publish(ctx, draft)
                    shared.copy(id = App.dao.upsert(shared))
                }
                Cloud.addReel(a, place)
                place
            }.onSuccess { p ->
                Toast.makeText(ctx, "Reel added to ${p.name}", Toast.LENGTH_SHORT).show()
                nav.navigate("place/${p.id}") { popUpTo("addreel?url={url}&place={place}") { inclusive = true } }
            }.onFailure { error = it.message }
            busy = false
        }
    }

    Scaffold(topBar = { BackBar("Add a reel", { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text("Found a reel about a place worth visiting? Share it from Instagram to TripGuide, or paste its link. " +
                "TripGuide reads the caption, adds the reel to the place and updates its guide for everyone.", style = MaterialTheme.typography.bodyMedium)
            if (!Cloud.aiEnabled) {
                Text("Reel reading isn't set up in this build (README → Community setup).", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error)
                return@Column
            }
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true,
                label = { Text("Instagram reel link") }, leadingIcon = { Icon(Icons.Default.Link, null) },
                trailingIcon = {
                    IconButton({ ctx.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.let { url = it.toString() } }) {
                        Icon(Icons.Default.ContentPaste, "Paste")
                    }
                })
            Button(::read, Modifier.padding(top = 8.dp), enabled = !busy && url.isNotBlank()) { Icon(Icons.Default.AutoAwesome, null); Text(" Read reel") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            error?.let { Text(it, Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.error) }

            val a = result ?: return@Column
            ReelEmbed(a.url, Modifier.fillMaxWidth().height(480.dp).padding(vertical = 8.dp))
            if (a.author.isNotBlank()) Text("@${a.author}", style = MaterialTheme.typography.labelLarge)
            a.info?.let { info ->
                if (!info.optBoolean("isTravelPlace")) Text("⚠ This doesn't look like a reel about a specific place.", color = MaterialTheme.colorScheme.error)
                Text(info.optString("summary"), Modifier.padding(vertical = 4.dp))
                info.list("tips").forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }

            Section("This reel is about")
            var menu by remember { mutableStateOf(false) }
            Box {
                OutlinedButton({ menu = true }, Modifier.fillMaxWidth()) {
                    Text(if (newPlace) "A new place" else target?.let { "${it.name}, ${it.region}" } ?: "Choose a place", Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(menu, { menu = false }, Modifier.heightIn(max = 400.dp)) {
                    DropdownMenuItem({ Text("➕ A new place", fontWeight = FontWeight.Bold) }, { newPlace = true; target = null; menu = false })
                    places.sortedBy { it.name }.forEach { p -> DropdownMenuItem({ Text("${p.name}, ${p.region}") }, { target = p; newPlace = false; menu = false }) }
                }
            }
            if (newPlace) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Place name *") }, singleLine = true)
                OutlinedTextField(region, { region = it }, Modifier.fillMaxWidth(), label = { Text("District / region *") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Latitude", lat, Modifier.weight(1f), decimal = true) { lat = it }
                    NumberField("Longitude", lng, Modifier.weight(1f), decimal = true) { lng = it }
                }
                TextButton({
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) fillLocation()
                    else locPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }) { Icon(Icons.Default.MyLocation, null); Text(" I'm there now – use my location") }
                Text("Tip: in Google Maps, long-press the spot and copy the coordinates.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Button(::save, Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp), enabled = !busy && (newPlace || target != null)) {
                Icon(Icons.Default.Check, null); Text(" Add reel to TripGuide")
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Public, null, Modifier.size(16.dp), MaterialTheme.colorScheme.outline)
                Text(" Visible to all TripGuide users. By adding you agree to the community rules.", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
                TextButton({ Util.open(ctx, RULES_URL) }) { Text("Rules") }
            }
        }
    }
}
