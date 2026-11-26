package com.tripguide.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(nav: NavController) {
    val places by App.dao.places().collectAsState(null)
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var vibes by rememberSaveable { mutableStateOf(listOf<String>()) }
    var amenities by rememberSaveable { mutableStateOf(listOf<String>()) }
    var favOnly by rememberSaveable { mutableStateOf(false) }
    var openOnly by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf("Name") }
    var showFilters by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun dist(p: Place) = Planner.km(Prefs.homeLat.toDouble(), Prefs.homeLng.toDouble(), p.lat, p.lng).toInt()
    val shown = places.orEmpty().filter { p ->
        (query.isBlank() || listOf(p.name, p.region, p.description, p.category).any { it.contains(query.trim(), true) }) &&
            (category == null || p.category == category) &&
            (vibes.isEmpty() || vibes.any { it in p.vibes }) &&
            p.amenities.containsAll(amenities) &&
            (!favOnly || p.favorite) && (!openOnly || p.status == "Open")
    }.let { l ->
        when (sort) { "Nearest" -> l.sortedBy(::dist); "Cheapest" -> l.sortedBy { it.dailyCost }; else -> l }
    }
    val activeFilters = vibes.size + amenities.size + (if (openOnly) 1 else 0)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("TripGuide", fontWeight = FontWeight.Bold) }, actions = {
                IconButton({ nav.navigate("discover") }) { Icon(Icons.Default.TravelExplore, "Discover on Google Maps") }
                IconButton({ favOnly = !favOnly }) {
                    Icon(if (favOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favourites only")
                }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { nav.navigate("edit") }, icon = { Icon(Icons.Default.AddLocationAlt, null) }, text = { Text("Add place") })
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 88.dp)) {
            item {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Search places, regions…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
                    trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Default.Close, "Clear") } })
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(category == null, { category = null }, { Text("All") }) }
                    items(Catalog.categories) { c -> FilterChip(category == c, { category = if (category == c) null else c }, { Text(c) }) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${shown.size} places", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    var sortMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton({ sortMenu = true }) { Icon(Icons.Default.Sort, null); Text(" $sort") }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            listOf("Name", "Nearest", "Cheapest").forEach { DropdownMenuItem({ Text(it) }, { sort = it; sortMenu = false }) }
                        }
                    }
                    BadgedBox({ if (activeFilters > 0) Badge { Text("$activeFilters") } }) {
                        TextButton({ showFilters = true }) { Icon(Icons.Default.Tune, null); Text(" Filters") }
                    }
                }
            }
            if (places != null && shown.isEmpty()) item {
                Text("No places match. Clear some filters or add a new place.", Modifier.padding(32.dp), style = MaterialTheme.typography.bodyLarge)
            }
            items(shown, key = { it.id }) { p ->
                ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { nav.navigate("place/${p.id}") }) {
                    Box {
                        PhotoPager(p.photos.take(1), Modifier.fillMaxWidth().height(170.dp))
                        IconButton({ scope.launch { App.dao.upsert(p.copy(favorite = !p.favorite)) } }, Modifier.align(Alignment.TopEnd)) {
                            Icon(if (p.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favourite",
                                tint = if (p.favorite) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.White)
                        }
                    }
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            StatusBadge(p.status)
                        }
                        Text("${p.region} · ${p.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${Prefs.distance(dist(p))} from ${Prefs.homeName} · ~${Prefs.money(p.dailyCost)}/person/day",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (showFilters) ModalBottomSheet({ showFilters = false }) {
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton({ vibes = emptyList(); amenities = emptyList(); openOnly = false }) { Text("Clear all") }
            }
            CheckRow("Hide places reported closed or blocked", openOnly) { openOnly = it }
            Section("Vibe")
            ChipPicker(Catalog.vibes, vibes) { vibes = it }
            Section("Must have amenities")
            CheckboxGrid(Catalog.amenities, amenities) { amenities = it }
        }
    }
}
