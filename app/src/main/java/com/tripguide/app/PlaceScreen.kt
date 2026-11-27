package com.tripguide.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.text.format.DateUtils

@Composable
fun PlaceScreen(nav: NavController, id: Long) {
    val place = App.dao.place(id).collectAsState(null).value ?: return
    val updates by App.dao.updates(id).collectAsState(emptyList())
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var posting by remember { mutableStateOf(false) }
    val reels = Cloud.reels.collectAsState().value.filter { it.placeKey == place.cloudKey }
    val summary by remember(place.cloudKey) { Cloud.summary(place.cloudKey) }.collectAsState(null)
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        BackBar(place.name, { nav.popBackStack() }) {
            SaveButton(place)
            IconButton({ Util.share(ctx, "${place.name} (${place.region})\n\n${place.description}\n\nhttps://maps.google.com/?q=${place.lat},${place.lng}\n— shared from TripGuide") }) {
                Icon(Icons.Default.Share, "Share")
            }
            IconButton({ nav.navigate("edit?id=${place.id}") }) { Icon(Icons.Default.Edit, "Edit") }
            if (place.userAdded) IconButton({ confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
            if (place.key.isNotEmpty() && !place.userAdded) IconButton({
                scope.launch { runCatching { Cloud.report("places", place.key) } }
                android.widget.Toast.makeText(ctx, "Reported. Places with 3 reports are hidden.", android.widget.Toast.LENGTH_SHORT).show()
            }) { Icon(Icons.Default.Flag, "Report this place") }
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { PhotoPager(place.photos, Modifier.fillMaxWidth().height(260.dp)) }
            item {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(place.name, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        StatusBadge(place.status)
                    }
                    Text("${place.region} · ${place.category}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ nav.navigate("plan?place=${place.id}") }, Modifier.weight(1f)) { Icon(Icons.Default.EventAvailable, null); Text(" Plan trip") }
                        GuideButton(place, Modifier.weight(1f))
                    }
                }
            }
            item {
                ScrollableTabRow(tab, edgePadding = 8.dp) {
                    listOf("Overview", "Reels (${reels.size})", "Guide & tips", "Updates (${updates.size})").forEachIndexed { i, t ->
                        Tab(tab == i, { tab = i }, text = { Text(t, maxLines = 1) })
                    }
                }
            }
            when (tab) {
                0 -> item { Overview(place, summary) }
                1 -> {
                    item {
                        Column(Modifier.padding(16.dp)) {
                            Text("Reels travellers shared about ${place.name}. Their captions build the guide on the Overview tab.", style = MaterialTheme.typography.bodyMedium)
                            Button({ nav.navigate("addreel?place=${place.id}") }, Modifier.padding(top = 8.dp)) { Icon(Icons.Default.AddLink, null); Text(" Add a reel") }
                            if (!Cloud.enabled) Text("Community reels need the Firebase setup (README → Community setup).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            else if (reels.isEmpty()) Text("No reels yet – be the first to add one.", Modifier.padding(top = 8.dp))
                        }
                    }
                    items(reels, key = { it.code }) { ReelCard(it) }
                }
                2 -> item { GuideTab(place) }
                else -> {
                    item {
                        Column(Modifier.padding(16.dp)) {
                            Text("Been there recently? Log what to expect – closures, road conditions, crowds. The status badge updates and upcoming trips here get an alert.",
                                style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            Button({ posting = true }) { Icon(Icons.Default.Campaign, null); Text(" Post an update") }
                        }
                    }
                    if (updates.isEmpty()) item { Text("No updates yet.", Modifier.padding(horizontal = 16.dp)) }
                    items(updates, key = { it.id }) { UpdateCard(it) }
                }
            }
        }
    }

    if (posting) PostUpdateDialog(place) { posting = false }
    if (confirmDelete) AlertDialog({ confirmDelete = false },
        title = { Text("Delete ${place.name}?") }, text = { Text("This removes the place and its updates.") },
        confirmButton = { TextButton({ scope.launch(Dispatchers.Main) { Cloud.deletePlace(place.key); App.dao.deletePlace(place.id); nav.popBackStack() } }) { Text("Delete") } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Overview(p: Place, s: PlaceSummary?) = Column(Modifier.padding(16.dp)) {
    Text(p.description, style = MaterialTheme.typography.bodyLarge)
    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        p.vibes.forEach { AssistChip({}, { Text(it) }) }
    }
    if (s != null) ReelGuide(s)
    Section("Quick facts")
    val km = Planner.km(Prefs.homeLat.toDouble(), Prefs.homeLng.toDouble(), p.lat, p.lng).toInt()
    InfoRow("Distance from ${Prefs.homeName}", "${Prefs.distance(km)} (straight line)")
    InfoRow("Best months", if (p.bestMonths.size == 12 || p.bestMonths.isEmpty()) "All year" else p.bestMonths.sorted().joinToString { Catalog.months[it - 1] })
    InfoRow("Recommended stay", "${p.minDays}+ day${if (p.minDays > 1) "s" else ""}")
    InfoRow("Est. stay & food", "${Prefs.money(p.dailyCost)} / person / day")
    InfoRow("Reachable by", p.transport.joinToString().ifEmpty { "—" })
    InfoRow("Coordinates", "%.4f, %.4f".format(p.lat, p.lng))
    Section("Amenities")
    Catalog.amenities.forEach { a ->
        val has = a in p.amenities
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (has) Icons.Default.CheckCircle else Icons.Default.RemoveCircleOutline, null, Modifier.size(18.dp),
                if (has) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            Text("  $a", style = MaterialTheme.typography.bodyMedium, color = if (has) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
        }
    }
    WeatherCard(p.lat, p.lng)
}

@Composable
private fun GuideTab(p: Place) = Column(Modifier.padding(16.dp)) {
    listOf("How to get there" to p.howToReach, "Guide & itinerary" to p.guide, "Tips" to p.tips, "Safety & emergency" to p.safety)
        .filter { it.second.isNotBlank() }
        .forEach { (title, body) -> Section(title); Text(body, style = MaterialTheme.typography.bodyMedium) }
    if (p.photoCredits.isNotBlank()) {
        Section("Photo credits")
        Text(p.photoCredits, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UpdateCard(u: PlaceUpdate) = OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
    Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val alert = u.type in setOf("Temporarily closed", "Road blocked", "Weather alert")
            Surface(color = if (alert) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
                Text(u.type, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.weight(1f))
            Text("${u.author} · ${DateUtils.getRelativeTimeSpanString(u.createdAt)}", style = MaterialTheme.typography.labelSmall)
        }
        Text(u.text, Modifier.padding(top = 6.dp))
        u.photo?.let { Photo(it, Modifier.padding(top = 8.dp).fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp))) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostUpdateDialog(place: Place, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf("General info") }
    var text by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { scope.launch { photo = Util.importPhoto(ctx, it) } }
    }
    ModalBottomSheet(onDone) {
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text("Update on ${place.name}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            ChipPicker(Catalog.updateTypes, listOf(type), single = true) { type = it.first() }
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 3,
                label = { Text("What did you find?") })
            TextButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Default.AddAPhoto, null); Text(if (photo == null) " Add photo" else " Change photo")
            }
            photo?.let { Photo(it, Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(8.dp))) }
            Button(enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp), onClick = {
                scope.launch {
                    val u = PlaceUpdate(placeId = place.id, author = Prefs.userName, type = type, text = text.trim(), photo = photo)
                    val saved = u.copy(id = App.dao.insert(u))
                    if (type in Catalog.statusTypes) App.dao.upsert(place.copy(status = type, updatedAt = saved.createdAt))
                    Notify.placeUpdated(ctx.applicationContext, place, saved)
                    onDone()
                }
            }) { Text("Post update") }
        }
    }
}

/** AI guide combined from every reel linked to the place. */
@Composable
private fun ReelGuide(s: PlaceSummary) = ElevatedCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
    Column(Modifier.padding(14.dp)) {
        Text("From ${s.reelCount} traveller reel${if (s.reelCount == 1) "" else "s"}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(s.summary, Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.bodyMedium)
        s.warnings.forEach { Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (s.highlights.isNotEmpty()) { Section("Highlights"); s.highlights.forEach { Text("• $it") } }
        if (s.guide.isNotBlank()) { Section("How to spend the visit"); Text(s.guide) }
        if (s.tips.isNotEmpty()) { Section("Tips from reels"); s.tips.forEach { Text("• $it") } }
        listOf("Getting there" to s.howToReach, "Best time" to s.bestTime, "Costs mentioned" to s.costNotes).filter { it.second.isNotBlank() }
            .forEach { (k, v) -> InfoRow(k, v) }
        Text("AI summary of reel captions – verify timings and prices before you go.", Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ReelCard(r: Reel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var reported by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        ReelEmbed(r.url, Modifier.fillMaxWidth().height(560.dp))
        Column(Modifier.padding(12.dp)) {
            if (r.author.isNotBlank()) Text("@${r.author}", style = MaterialTheme.typography.labelLarge)
            if (r.summary.isNotBlank()) Text(r.summary, style = MaterialTheme.typography.bodyMedium)
            r.tips.take(3).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            Row {
                TextButton({ Util.open(ctx, r.url) }) { Text("Open in Instagram") }
                TextButton(enabled = !reported, onClick = {
                    scope.launch {
                        runCatching { Cloud.report("reels", r.code) }
                        reported = true
                        android.widget.Toast.makeText(ctx, "Reported. Reels with 3 reports are hidden.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text(if (reported) "Reported" else "Report") }
            }
        }
    }
}
