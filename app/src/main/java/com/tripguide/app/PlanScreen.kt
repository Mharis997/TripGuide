package com.tripguide.app

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

private fun nextSaturday(hour: Int, plusDays: Long = 0) = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).plusDays(plusDays)
    .atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Trip wizard. With [placeId] >= 0 it creates a trip to that place; otherwise it suggests places. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(nav: NavController, placeId: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val fixedPlace = if (placeId >= 0) App.dao.place(placeId).collectAsState(null).value else null
    var depart by rememberSaveable { mutableLongStateOf(nextSaturday(7)) }
    var back by rememberSaveable { mutableLongStateOf(nextSaturday(20, 1)) }
    var budget by rememberSaveable { mutableStateOf("100000") }
    var people by rememberSaveable { mutableIntStateOf(2) }
    var transport by rememberSaveable { mutableStateOf("Car") }
    var vibes by rememberSaveable { mutableStateOf(listOf<String>()) }
    var showResults by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val input = PlanInput(depart, back, budget.toIntOrNull() ?: 0, people, transport, vibes.toSet(), Prefs.homeLat.toDouble(), Prefs.homeLng.toDouble())

    fun createTrip(p: Place) = scope.launch(Dispatchers.Main) {
        val trip = Trip(placeId = p.id, title = "${p.name} trip", departAt = depart, returnAt = back, budget = input.budget,
            people = people, transport = transport, vibes = vibes)
        val id = App.dao.insert(trip)
        App.dao.insertItems(Catalog.packingList(p, transport).map { CheckItem(tripId = id, text = it) })
        Notify.scheduleTrip(ctx, trip.copy(id = id))
        nav.navigate("trip/$id")
    }

    fun validate() = when {
        back <= depart -> "Return must be after departure"
        depart < System.currentTimeMillis() - 3_600_000 -> "Departure is in the past"
        (budget.toIntOrNull() ?: 0) <= 0 -> "Enter a budget"
        else -> null
    }.also { error = it } == null

    BackHandler(showResults) { showResults = false }

    Scaffold(topBar = {
        if (placeId >= 0 || showResults) BackBar(if (showResults) "Suggestions for you" else "Plan trip to ${fixedPlace?.name ?: ""}",
            { if (showResults) showResults = false else nav.popBackStack() })
        else TopAppBar(title = { Text("Plan a trip") })
    }) { pad ->
        if (showResults) {
            val places by App.dao.places().collectAsState(emptyList())
            val ranked = remember(places, input) { Planner.suggest(places, input) }
            LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("${Planner.days(input)} days · $people ${if (people == 1) "person" else "people"} · $transport · budget ${Prefs.money(input.budget)}",
                        style = MaterialTheme.typography.labelLarge)
                }
                if (ranked.isEmpty()) item { Text("No places yet – add reels from Instagram first, then TripGuide can suggest where to go.") }
                items(ranked, key = { it.place.id }) { s -> SuggestionCard(s, onView = { nav.navigate("place/${s.place.id}") }, onChoose = { createTrip(s.place) }) }
            }
            return@Scaffold
        }
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            if (placeId < 0) Text("Not sure where to go? Tell us about your trip and we'll suggest places that fit.",
                style = MaterialTheme.typography.bodyLarge)
            Section("When (from home: ${Prefs.homeName})")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateTimeButton("Depart", depart, Modifier.weight(1f)) { depart = it; if (back <= it) back = it + 2 * 86_400_000L }
                DateTimeButton("Return home", back, Modifier.weight(1f)) { back = it }
            }
            Section("Budget & group")
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberField("Total budget (${Prefs.currency})", budget, Modifier.weight(1f)) { budget = it }
                Spacer(Modifier.width(12.dp))
                IconButton({ if (people > 1) people-- }) { Icon(Icons.Default.RemoveCircleOutline, "Fewer people") }
                Text("$people", style = MaterialTheme.typography.titleLarge)
                IconButton({ if (people < 50) people++ }) { Icon(Icons.Default.AddCircleOutline, "More people") }
                Icon(Icons.Default.Group, null)
            }
            Section("Mode of transport")
            ChipPicker(Catalog.transport, listOf(transport), single = true) { transport = it.first() }
            if (placeId < 0) {
                Section("What vibe are you after?")
                ChipPicker(Catalog.vibes, vibes) { vibes = it }
            }
            error?.let { Text(it, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error) }
            if (fixedPlace != null) {
                val s = Planner.estimate(fixedPlace, input)
                Section("Estimate")
                EstimateBlock(s)
            }
            Button({
                if (validate()) { if (fixedPlace != null) createTrip(fixedPlace) else showResults = true }
            }, Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp)) {
                Icon(if (placeId >= 0) Icons.Default.Luggage else Icons.Default.AutoAwesome, null)
                Text(if (placeId >= 0) "  Create trip" else "  Suggest places")
            }
        }
    }
}

@Composable
fun EstimateBlock(s: Suggestion) {
    InfoRow("Distance", "${Prefs.distance(s.km)} straight line")
    InfoRow("Travel time (one way)", "≈ %.1f hrs".format(s.travelHours))
    InfoRow("Transport (round trip)", Prefs.money(s.transportCost))
    InfoRow("Stay & food", Prefs.money(s.stayCost))
    InfoRow("Estimated total", Prefs.money(s.total))
    s.warnings.forEach { Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    Text("Rough estimates – actual fares and prices vary.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun SuggestionCard(s: Suggestion, onView: () -> Unit, onChoose: () -> Unit) = ElevatedCard(onView, Modifier.fillMaxWidth()) {
    Row(Modifier.padding(12.dp)) {
        PlaceThumb(s.place, Modifier.size(84.dp))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.place.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${s.score}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text("${s.place.category} · ≈%.1f hrs away · ${Prefs.money(s.total)}".format(s.travelHours), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator({ s.score / 100f }, Modifier.fillMaxWidth().padding(vertical = 6.dp))
            s.reasons.forEach { Text("✓ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            s.warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), Arrangement.End) {
                TextButton(onView) { Text("Details") }
                Button(onChoose) { Text("Choose") }
            }
        }
    }
}
