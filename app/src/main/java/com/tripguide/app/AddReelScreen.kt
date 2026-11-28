package com.tripguide.app

import android.Manifest
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.core.net.toUri
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Everything read from one reel/video, before it is saved. */
private class Findings(
    val code: String, val url: String, val author: String, val caption: String,
    val screenText: String, val speech: String, val frames: List<Bitmap>, val notes: List<String>,
)

/**
 * Reel link and/or a video/photo on the phone → caption, on-screen text (OCR) and speech are read on the phone →
 * offline rules extract details → optional "Ask my AI" → link to a place or create one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddReelScreen(nav: NavController, sharedUrl: String?, placeId: Long, sharedVideo: String? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val places by App.dao.places().collectAsState(emptyList())
    var url by rememberSaveable { mutableStateOf(sharedUrl.orEmpty()) }
    var media by rememberSaveable { mutableStateOf(sharedVideo) }
    var readScreen by rememberSaveable { mutableStateOf(true) }
    var listen by rememberSaveable { mutableStateOf(false) }
    var speechLang by rememberSaveable { mutableStateOf("ml-IN") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<Findings?>(null) }
    var info by remember { mutableStateOf(Extracted()) }
    var aiReply by rememberSaveable { mutableStateOf("") }
    var target by remember { mutableStateOf<Place?>(null) }
    var newPlace by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lng by remember { mutableStateOf("") }

    LaunchedEffect(placeId, places) { if (target == null && placeId >= 0) target = places.firstOrNull { it.id == placeId } }

    fun analyse() {
        val link = reelLink(url) ?: run { error = "Paste or share an Instagram reel link (instagram.com/reel/… or /share/…)."; return }
        busy = true; error = null; found = null
        scope.launch(Dispatchers.Main) {
            runCatching {
                val notes = mutableListOf<String>()
                val canon = Cloud.resolveReel(link.also { status = "Opening the link…" })
                    ?: error("Couldn't open that Instagram link. Check your internet connection, or open the reel in Instagram and use Share → TripGuide.")
                status = "Reading the reel…"
                val (cap, author) = Cloud.fetchCaption(canon.second)
                if (cap.isBlank() && media == null) error("Couldn't read this reel – it may be private or removed. Try another reel, or pick its saved video to read the text on screen.")
                if (cap.isBlank()) notes += "Couldn't read this reel's caption (private or removed); using the video only."
                val uri = media?.toUri()
                val frames = uri?.let { status = "Taking frames…"; mediaFrames(ctx, it) }.orEmpty()
                var screen = ""
                if (readScreen && frames.isNotEmpty()) runCatching {
                    val codes = (Prefs.ocrLangs.split(',') + Extract.scriptsIn(cap).map { it.ocr }).filter { it.isNotBlank() }.distinct()
                    codes.filterNot { Ocr.installed(ctx, it) }.forEach { code ->
                        status = "Downloading ${Extract.scripts.first { it.ocr == code }.language} reading pack (one time)…"
                        Ocr.download(ctx, code)
                    }
                    screen = Ocr.read(ctx, frames, codes) { status = "Reading on-screen text $it/${frames.size}…" }
                }.onFailure { notes += "Couldn't read on-screen text: ${it.message}" }
                var spoken = ""
                val isVideo = uri != null && ctx.contentResolver.getType(uri)?.startsWith("video/") == true
                if (listen && isVideo && Speech.supported) runCatching {
                    status = "Listening to the video…"
                    spoken = Speech.transcribe(ctx, uri!!, speechLang)
                    if (spoken.isBlank()) notes += "No speech was recognised."
                }.onFailure { notes += it.message ?: "Speech recognition failed." }
                status = "Finding details…"
                info = Extract.merge(listOf(cap, screen, spoken).filter { it.isNotBlank() }.map { Extract.from(it, places) })
                found = Findings(canon.first, canon.second, author, cap, screen, spoken, frames, notes)
                if (placeId < 0) {
                    target = places.firstOrNull { it.name == info.placeName }
                    newPlace = target == null
                    name = info.placeName
                    if (newPlace && name.isNotBlank()) {
                        status = "Finding $name on the map…"
                        Cloud.locate(ctx, name, info.region)?.let { a ->
                            lat = "%.5f".format(a.latitude); lng = "%.5f".format(a.longitude)
                            region = listOfNotNull(a.subAdminArea ?: a.locality, a.adminArea).distinct().joinToString(", ")
                        } ?: run { region = info.region }
                    }
                }
            }.onFailure { error = it.message }
            busy = false; status = ""
        }
    }
    LaunchedEffect(Unit) { if (sharedUrl != null || sharedVideo != null) analyse() }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { media = it.toString(); found = null } }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        listen = granted
        if (!granted) Toast.makeText(ctx, "Speech reading needs this permission. TripGuide only listens to the video, not your microphone.", Toast.LENGTH_LONG).show()
    }
    fun fillLocation() = Util.lastLocation(ctx)?.let { lat = "%.5f".format(it.latitude); lng = "%.5f".format(it.longitude) }
        ?: Toast.makeText(ctx, "Location not available yet – type the coordinates.", Toast.LENGTH_LONG).show()
    val locPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) fillLocation() }

    fun save() {
        val f = found ?: return
        busy = true; error = null
        scope.launch(Dispatchers.Main) {
            runCatching {
                check(Cloud.enabled) { "Community sharing isn't set up in this build, so reels can't be added (README → Community setup)." }
                val place = if (!newPlace) target ?: error("Choose the place this is about.") else {
                    val la = lat.toDoubleOrNull()?.takeIf { it in -90.0..90.0 }; val ln = lng.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
                    require(name.isNotBlank() && region.isNotBlank()) { "Enter the place name and region." }
                    require(la != null && ln != null) { "Enter valid coordinates (or use your location)." }
                    val facts = listOfNotNull(
                        info.prices.takeIf { it.isNotEmpty() }?.let { "Prices mentioned: ${it.joinToString()}" },
                        info.times.takeIf { it.isNotEmpty() }?.let { "Timings mentioned: ${it.joinToString()}" },
                        info.distances.takeIf { it.isNotEmpty() }?.let { "Distances mentioned: ${it.joinToString()}" },
                        info.phones.takeIf { it.isNotEmpty() }?.let { "Phone: ${it.joinToString()}" },
                    )
                    val draft = Place(
                        name = name.trim(), region = region.trim(), lat = la, lng = ln, userAdded = true,
                        category = info.category ?: Catalog.categories.first(), vibes = info.vibes, amenities = info.amenities,
                        bestMonths = info.months, description = info.summary.ifBlank { f.caption.take(300) },
                        guide = facts.joinToString("\n"), tips = info.tips.joinToString("\n") { "• $it" },
                    )
                    val shared = Cloud.publish(ctx, draft)
                    shared.copy(id = App.dao.upsert(shared))
                }
                Cloud.addReel(Reel(
                    code = f.code, url = f.url, placeKey = place.cloudKey, placeName = place.name, caption = f.caption, author = f.author,
                    summary = info.summary, tips = info.tips, createdAt = 0, prices = info.prices, times = info.times, months = info.months,
                    languages = info.languages, seen = listOf(f.screenText, f.speech).filter { it.isNotBlank() }.joinToString("\n"),
                ))
                place
            }.onSuccess { p ->
                Toast.makeText(ctx, "Reel added to ${p.name}", Toast.LENGTH_SHORT).show()
                nav.navigate("place/${p.id}") { popUpTo("addreel?url={url}&place={place}&video={video}") { inclusive = true } }
            }.onFailure { error = it.message }
            busy = false
        }
    }

    Scaffold(topBar = { BackBar("Add a reel", { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text("Share a reel from Instagram (Share → TripGuide) or paste its link. TripGuide reads the reel and finds the place, " +
                "tips, prices and timings – in Malayalam, Tamil, Hindi, English and other Indian languages. Places in TripGuide come only from reels.",
                style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true,
                label = { Text("Instagram reel link") }, leadingIcon = { Icon(Icons.Default.Link, null) },
                trailingIcon = {
                    IconButton({ ctx.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.let { url = it.toString() } }) {
                        Icon(Icons.Default.ContentPaste, "Paste")
                    }
                })
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton({ pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) {
                    Icon(Icons.Default.VideoLibrary, null); Text(if (media == null) " Also read its saved video (optional)" else " Change video")
                }
                if (media != null) {
                    Text("  Selected", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    IconButton({ media = null; found = null }) { Icon(Icons.Default.Close, "Remove") }
                }
            }
            if (media != null) {
                CheckRow("Read text on screen (signboards, overlays)", readScreen) { readScreen = it }
                if (Speech.supported) {
                    CheckRow("Listen to speech in the video", listen) { on ->
                        if (on && ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                            micPermission.launch(Manifest.permission.RECORD_AUDIO) else listen = on
                    }
                    if (listen) {
                        Text("Spoken language", style = MaterialTheme.typography.labelMedium)
                        ChipPicker(Extract.scripts.map { it.language }, listOf(Extract.scripts.first { it.speech == speechLang }.language), single = true) { sel ->
                            speechLang = Extract.scripts.first { it.language == sel.first() }.speech
                        }
                    }
                } else Text("Speech reading needs Android 13 or newer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Button(::analyse, Modifier.padding(top = 8.dp), enabled = !busy && url.isNotBlank()) {
                Icon(Icons.Default.DocumentScanner, null); Text(" Read reel")
            }
            Text("Works offline after a one-time language pack download. Nothing is sent anywhere unless you tap Ask my AI or Translate.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.error) }

            val f = found ?: return@Column
            f.notes.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            ReelEmbed(f.url, Modifier.fillMaxWidth().height(480.dp).padding(vertical = 8.dp))
            if (f.author.isNotBlank()) Text("@${f.author}", style = MaterialTheme.typography.labelLarge)

            Section("Details found")
            if (info.languages.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                info.languages.forEach { AssistChip({}, { Text(it) }, leadingIcon = { Icon(Icons.Default.Translate, null, Modifier.size(16.dp)) }) }
            }
            if (info.summary.isNotBlank()) Text(info.summary, Modifier.padding(vertical = 4.dp))
            listOf("Prices" to info.prices, "Timings" to info.times, "Distances" to info.distances, "Phone" to info.phones,
                "Best months" to info.months.map { Catalog.months[it - 1] }, "Category" to listOfNotNull(info.category),
                "Vibes" to info.vibes, "Amenities" to info.amenities).filter { it.second.isNotEmpty() }
                .forEach { (k, v) -> InfoRow(k, v.joinToString()) }
            if (info.tips.isNotEmpty()) { Text("Tips", style = MaterialTheme.typography.titleSmall); info.tips.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) } }
            if (info.tips.isEmpty() && info.prices.isEmpty() && info.summary.isBlank()) Text("Not much found – try Ask my AI below.")

            listOf("Caption" to f.caption, "Text on screen" to f.screenText, "Speech" to f.speech).filter { it.second.isNotBlank() }.forEach { (title, text) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Section(title, Modifier.weight(1f))
                    TextButton({ Share.translate(ctx, text) }) { Icon(Icons.Default.Translate, null); Text(" Translate") }
                }
                Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 12)
            }

            Section("Want a fuller guide?")
            Text("Ask my AI sends the caption, text and a few frames to the AI app you use (Gemini, ChatGPT, Claude…). Paste its answer back here.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton({ scope.launch { Share.toAi(ctx, aiPrompt(target?.name ?: name, f.url, f.caption, f.screenText, f.speech), f.frames) } }) {
                Icon(Icons.Default.AutoAwesome, null); Text(" Ask my AI")
            }
            OutlinedTextField(aiReply, { aiReply = it }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("Paste your AI's answer (optional)") })
            if (aiReply.isNotBlank()) TextButton({ info = Extract.merge(listOf(Extract.from(aiReply, places), info)) }) { Text("Use this answer") }

            Section("This is about")
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
                Text(if (lat.isNotBlank()) "Location found from the place name – check it's right." else "Couldn't find it on the map – in Google Maps, long-press the spot and copy the coordinates.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Button(::save, Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp), enabled = !busy && (newPlace || target != null)) {
                Icon(Icons.Default.Check, null); Text(" Add reel to TripGuide")
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Public, null, Modifier.size(16.dp), MaterialTheme.colorScheme.outline)
                Text(" Visible to all TripGuide users. By adding you agree to the community rules.", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                TextButton({ Util.open(ctx, RULES_URL) }) { Text("Rules") }
            }
        }
    }
}
