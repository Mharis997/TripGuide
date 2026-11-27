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
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(nav: NavController) {
    val places by App.dao.places().collectAsState(null)
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var vibes by rememberSaveable { mutableStateOf(listOf<String>()) }
    var amenities by rememberSaveable { mutableStateOf(listOf<String>()) }
    var savedOnly by rememberSaveable { mutableStateOf<String?>(null) }
    var reelsMode by rememberSaveable { mutableStateOf(true) }
    var openOnly by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf("Name") }
    var showFilters by remember { mutableStateOf(false) }
    val reels by Cloud.reels.collectAsState()
    val reelsByPlace = remember(reels) { reels.groupBy { it.placeKey } }

    fun dist(p: Place) = Planner.km(Prefs.homeLat.toDouble(), Prefs.homeLng.toDouble(), p.lat, p.lng).toInt()
    val shown = places.orEmpty().filter { p ->
        (query.isBlank() || listOf(p.name, p.region, p.description, p.category).any { it.contains(query.trim(), true) }) &&
            (category == null || p.category == category) &&
            (vibes.isEmpty() || vibes.any { it in p.vibes }) &&
            p.amenities.containsAll(amenities) &&
            (savedOnly == null || p.saved == savedOnly) && (!openOnly || p.status == "Open")
    }.let { l ->
        when (sort) { "Nearest" -> l.sortedBy(::dist); "Cheapest" -> l.sortedBy { it.dailyCost }; else -> l }
    }.let { l -> if (reelsMode) l.sortedByDescending { reelsByPlace[it.cloudKey]?.size ?: 0 } else l } // places with reels lead the feed
    val activeFilters = vibes.size + amenities.size + (if (openOnly) 1 else 0)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("TripGuide", fontWeight = FontWeight.Bold) }, actions = {
                IconButton({ nav.navigate("addreel") }) { Icon(Icons.Default.AddLink, "Add an Instagram reel") }
                var listMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton({ listMenu = true }) {
                        Icon(if (savedOnly == null) Icons.Default.BookmarkBorder else Icons.Default.Bookmark, "Show saved list")
                    }
                    DropdownMenu(listMenu, { listMenu = false }) {
                        (listOf<String?>(null) + Catalog.lists).forEach { l ->
                            DropdownMenuItem({ Text(l ?: "All places") }, leadingIcon = { RadioButton(savedOnly == l, null) },
                                onClick = { savedOnly = l; listMenu = false })
                        }
                    }
                }
                IconButton({ reelsMode = !reelsMode }) {
                    Icon(if (reelsMode) Icons.Default.ViewAgenda else Icons.Default.Slideshow, if (reelsMode) "Show as list" else "Show as reels")
                }
            })
        },
        floatingActionButton = {
            if (!reelsMode) ExtendedFloatingActionButton(onClick = { nav.navigate("edit") }, icon = { Icon(Icons.Default.AddLocationAlt, null) }, text = { Text("Add place") })
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Search places, regions…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
                    trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Default.Close, "Clear") } })
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(category == null, { category = null }, { Text("All") }) }
                    items(Catalog.categories) { c -> FilterChip(category == c, { category = if (category == c) null else c }, { Text(c) }) }
                }
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
            if (places != null && shown.isEmpty()) {
                Text("No places match. Clear some filters or add a new place.", Modifier.padding(32.dp), style = MaterialTheme.typography.bodyLarge)
            } else if (reelsMode) {
                val pager = rememberPagerState { shown.size }
                VerticalPager(pager, Modifier.fillMaxSize(), key = { shown[it].id }) { i ->
                    val p = shown[i]
                    ReelPage(p, reelsByPlace[p.cloudKey].orEmpty(), dist(p)) { nav.navigate("place/${p.id}") }
                }
            } else LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
            items(shown, key = { it.id }) { p ->
                ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { nav.navigate("place/${p.id}") }) {
                    Box {
                        PhotoPager(p.photos.take(1), Modifier.fillMaxWidth().height(170.dp))
                        Box(Modifier.align(Alignment.TopEnd)) { SaveButton(p, androidx.compose.ui.graphics.Color.White) }
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

/** One full-height "short": the place's newest reel (or its photos) with actions and a swipeable info panel. */
@Composable
private fun ReelPage(p: Place, reels: List<Reel>, km: Int, onOpen: () -> Unit) = Column(Modifier.fillMaxSize().background(Color.Black)) {
    Box(Modifier.weight(1f).fillMaxWidth()) {
        if (reels.isNotEmpty()) ReelEmbed(reels.first().url, Modifier.fillMaxSize()) else PhotoPager(p.photos, Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = .35f), MaterialTheme.shapes.large)) {
            SaveButton(p, Color.White)
            IconButton(onOpen) { Icon(Icons.Default.Info, "Details", tint = Color.White) }
        }
    }
    Surface(Modifier.fillMaxWidth().clickable(onClick = onOpen), tonalElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusBadge(p.status)
            }
            Text("${p.region} · ${p.category} · ${Prefs.distance(km)} away", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text((reels.firstOrNull()?.summary?.ifBlank { null } ?: p.description), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GuideButton(p)
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onOpen) { Text(if (reels.isEmpty()) "Details" else "${reels.size} reel${if (reels.size == 1) "" else "s"} · Details") }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.KeyboardDoubleArrowUp, "Swipe up for the next place", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
