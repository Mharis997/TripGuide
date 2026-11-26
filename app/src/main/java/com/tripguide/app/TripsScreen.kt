package com.tripguide.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun countdown(t: Trip): String {
    val now = System.currentTimeMillis()
    val days = ((t.departAt - now) / 86_400_000L).toInt()
    return when {
        now > t.returnAt -> "Completed"
        now >= t.departAt -> "On the trip now"
        days == 0 -> "Leaving today"
        else -> "In $days day${if (days == 1) "" else "s"}"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(nav: NavController) {
    val trips by App.dao.trips().collectAsState(null)
    val places by App.dao.places().collectAsState(emptyList())
    val byId = places.associateBy { it.id }
    val now = System.currentTimeMillis()
    val (past, upcoming) = trips.orEmpty().partition { it.returnAt < now }

    Scaffold(topBar = { TopAppBar(title = { Text("My trips") }) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (trips?.isEmpty() == true) item {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Luggage, null, Modifier.size(64.dp), MaterialTheme.colorScheme.outline)
                    Text("No trips yet", style = MaterialTheme.typography.titleMedium)
                    TextButton({ nav.navigate("plan") }) { Text("Plan your first trip") }
                }
            }
            listOf("Upcoming" to upcoming, "Past" to past.reversed()).forEach { (title, list) ->
                if (list.isNotEmpty()) item { Text(title, style = MaterialTheme.typography.titleMedium) }
                items(list, key = { it.id }) { t ->
                    val p = byId[t.placeId]
                    ElevatedCard(Modifier.fillMaxWidth().clickable { nav.navigate("trip/${t.id}") }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Photo(p?.photos?.firstOrNull(), Modifier.size(64.dp))
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(t.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${fmtDate(t.departAt)} – ${fmtDate(t.returnAt)}", style = MaterialTheme.typography.bodySmall)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(countdown(t), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp)); p?.let { StatusBadge(it.status) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TripScreen(nav: NavController, id: Long) {
    val trip = App.dao.trip(id).collectAsState(null).value ?: return
    val place = App.dao.place(trip.placeId).collectAsState(null).value
    val expenses by App.dao.expenses(id).collectAsState(emptyList())
    val items by App.dao.checklist(id).collectAsState(emptyList())
    val updates by App.dao.updates(trip.placeId).collectAsState(emptyList())
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var notes by remember(trip.id) { mutableStateOf(trip.notes) }
    val spent = expenses.sumOf { it.amount }

    Scaffold(topBar = {
        BackBar(trip.title, { nav.popBackStack() }) {
            IconButton({
                Util.share(ctx, buildString {
                    appendLine("🧳 ${trip.title}")
                    appendLine("📍 ${place?.name ?: ""}, ${place?.region ?: ""}")
                    appendLine("🗓 ${fmt(trip.departAt)} → ${fmt(trip.returnAt)}")
                    appendLine("👥 ${trip.people} · ${trip.transport} · Budget ${Prefs.money(trip.budget)}")
                    place?.let { appendLine("🗺 https://maps.google.com/?q=${it.lat},${it.lng}") }
                    append("— planned with TripGuide")
                })
            }) { Icon(Icons.Default.Share, "Share") }
            IconButton({ confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete trip") }
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp)) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(countdown(trip), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(place?.name ?: "Place removed", style = MaterialTheme.typography.titleMedium)
                        place?.let { StatusBadge(it.status) }
                        Spacer(Modifier.height(8.dp))
                        InfoRow("Depart", fmt(trip.departAt))
                        InfoRow("Return home", fmt(trip.returnAt))
                        InfoRow("Group", "${trip.people} · ${trip.transport}")
                        if (place != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            OutlinedButton({ nav.navigate("place/${place.id}") }, Modifier.weight(1f)) { Text("Guide") }
                            OutlinedButton({ Util.directions(ctx, place.lat, place.lng) }, Modifier.weight(1f)) { Text("Directions") }
                        }
                    }
                }
            }
            if (place != null) item {
                Section("Cost estimate")
                EstimateBlock(Planner.estimate(place, PlanInput(trip.departAt, trip.returnAt, trip.budget, trip.people, trip.transport,
                    trip.vibes.toSet(), Prefs.homeLat.toDouble(), Prefs.homeLng.toDouble())))
            }
            if (place != null) item {
                Section("Where to stay")
                GoogleNearby(place.lat, place.lng, "${place.name}, ${place.region}", stays = true,
                    checkIn = trip.departAt, checkOut = trip.returnAt, people = trip.people)
            }
            if (updates.isNotEmpty()) item {
                Section("Latest traveller updates")
                updates.take(3).forEach { u ->
                    Text("• ${u.type} — ${u.text}", style = MaterialTheme.typography.bodyMedium)
                    Text("   ${u.author}, ${fmtDate(u.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }

            item {
                Section("Budget tracker")
                Text("${Prefs.money(spent)} spent of ${Prefs.money(trip.budget)}",
                    color = if (spent > trip.budget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                LinearProgressIndicator({ if (trip.budget > 0) (spent.toFloat() / trip.budget).coerceAtMost(1f) else 0f },
                    Modifier.fillMaxWidth().padding(vertical = 6.dp))
                var label by remember { mutableStateOf("") }
                var amount by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(label, { label = it }, Modifier.weight(1f), label = { Text("Expense") }, singleLine = true)
                    Spacer(Modifier.width(8.dp))
                    NumberField("Amount", amount, Modifier.weight(.6f)) { amount = it }
                    IconButton({
                        val a = amount.toIntOrNull()
                        if (label.isNotBlank() && a != null && a > 0) scope.launch {
                            App.dao.insert(Expense(tripId = id, label = label.trim(), amount = a)); label = ""; amount = ""
                        }
                    }) { Icon(Icons.Default.AddCircle, "Add expense") }
                }
            }
            items(expenses, key = { "e${it.id}" }) { e ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(e.label, Modifier.weight(1f)); Text(Prefs.money(e.amount))
                    IconButton({ scope.launch { App.dao.delete(e) } }) { Icon(Icons.Default.Close, "Remove") }
                }
            }

            item {
                Section("Packing checklist (${items.count { it.done }}/${items.size})")
                var newItem by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newItem, { newItem = it }, Modifier.weight(1f), label = { Text("Add item") }, singleLine = true)
                    IconButton({
                        if (newItem.isNotBlank()) scope.launch { App.dao.insertItems(listOf(CheckItem(tripId = id, text = newItem.trim()))); newItem = "" }
                    }) { Icon(Icons.Default.AddCircle, "Add item") }
                }
            }
            items(items, key = { "c${it.id}" }) { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(c.done, { scope.launch { App.dao.update(c.copy(done = it)) } })
                    Text(c.text, Modifier.weight(1f), textDecoration = if (c.done) TextDecoration.LineThrough else null)
                    IconButton({ scope.launch { App.dao.delete(c) } }) { Icon(Icons.Default.Close, "Remove") }
                }
            }

            item {
                Section("Notes")
                OutlinedTextField(notes, { notes = it; scope.launch { App.dao.update(trip.copy(notes = it)) } },
                    Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text("Bookings, contacts, ideas…") })
                place?.let { WeatherCard(it.lat, it.lng) }
            }
        }
    }

    if (confirmDelete) AlertDialog({ confirmDelete = false }, title = { Text("Delete this trip?") },
        confirmButton = {
            TextButton({ scope.launch(Dispatchers.Main) { Notify.cancelTrip(ctx, id); App.dao.deleteTrip(id); nav.popBackStack() } }) { Text("Delete") }
        },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } })
}
