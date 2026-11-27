package com.tripguide.app

import android.Manifest
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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

/**
 * Reel link and/or a video on the phone (+ optional caption) → frames read on-device → AI extracts details →
 * link to a place (or create one) → guide is rebuilt.
 */
@Composable
fun AddReelScreen(nav: NavController, sharedUrl: String?, placeId: Long, sharedVideo: String? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val places by App.dao.places().collectAsState(emptyList())
    var url by rememberSaveable { mutableStateOf(sharedUrl.orEmpty()) }
    var video by rememberSaveable { mutableStateOf(sharedVideo) }
    var caption by rememberSaveable { mutableStateOf("") }
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
        val link = reelLink(url)
        if (url.isNotBlank() && link == null) { error = "That isn't an Instagram reel link (instagram.com/reel/…)."; return }
        if (link == null && video == null && caption.isBlank()) { error = "Add a reel link, pick a video, or paste the caption."; return }
        busy = true; error = null; result = null
        scope.launch(Dispatchers.Main) {
            runCatching {
                val frames = video?.let { v -> videoFrames(ctx, android.net.Uri.parse(v)).also { if (it.isEmpty()) error("Couldn't read frames from that video.") } }
                Cloud.analyze(link, caption.trim(), frames.orEmpty())
            }.onSuccess { a ->
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
    LaunchedEffect(Unit) { if ((sharedUrl != null || sharedVideo != null) && Cloud.aiEnabled) read() }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { video = it.toString(); result = null } }

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
                if (a.url.isNotEmpty()) Cloud.addReel(a, place)
                else require(newPlace) { "To add this video's details to ${place.name}'s guide, also add the reel's link." }
                place
            }.onSuccess { p ->
                Toast.makeText(ctx, if (result?.url.isNullOrEmpty()) "${p.name} added" else "Reel added to ${p.name}", Toast.LENGTH_SHORT).show()
                nav.navigate("place/${p.id}") { popUpTo("addreel?url={url}&place={place}&video={video}") { inclusive = true } }
            }.onFailure { error = it.message }
            busy = false
        }
    }

    Scaffold(topBar = { BackBar("Add a reel or video", { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text("Found a reel about a place worth visiting? Share it from Instagram to TripGuide or paste its link. " +
                "Add the video too (saved reel, screen recording or your own clip) and TripGuide also analyses what's shown in it – " +
                "scenery, signboards, on-screen text and prices.", style = MaterialTheme.typography.bodyMedium)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton({ pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }) {
                    Icon(Icons.Default.VideoLibrary, null); Text(if (video == null) " Pick video" else " Change video")
                }
                if (video != null) {
                    Text("  Video selected", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    IconButton({ video = null; result = null }) { Icon(Icons.Default.Close, "Remove video") }
                }
            }
            if (reelLink(url) == null) OutlinedTextField(caption, { caption = it }, Modifier.fillMaxWidth(), minLines = 2,
                label = { Text("Caption (optional – paste it if you have it)") })
            Button(::read, Modifier.padding(top = 8.dp), enabled = !busy && (url.isNotBlank() || video != null || caption.isNotBlank())) {
                Icon(Icons.Default.AutoAwesome, null); Text(if (video != null) " Analyse video" else " Read reel")
            }
            Text("Only a few still frames are sent for analysis; the video itself stays on your phone.", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            error?.let { Text(it, Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.error) }

            val a = result ?: return@Column
            if (a.url.isNotEmpty()) ReelEmbed(a.url, Modifier.fillMaxWidth().height(480.dp).padding(vertical = 8.dp))
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
                Icon(Icons.Default.Check, null); Text(if (a.url.isNotEmpty()) " Add reel to TripGuide" else " Add place to TripGuide")
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
