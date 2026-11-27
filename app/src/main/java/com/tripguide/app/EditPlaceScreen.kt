package com.tripguide.app

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun EditPlaceScreen(nav: NavController, id: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var p by remember { mutableStateOf(Place(userAdded = true)) }
    var cost by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("1") }
    var lat by remember { mutableStateOf("") }
    var lng by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(id < 0) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        if (id >= 0) App.dao.getPlace(id)?.let {
            p = it; cost = it.dailyCost.toString(); days = it.minDays.toString(); lat = it.lat.toString(); lng = it.lng.toString()
        }
        loaded = true
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        scope.launch { p = p.copy(photos = p.photos + uris.mapNotNull { Util.importPhoto(ctx, it) }) }
    }
    fun fillLocation() = Util.lastLocation(ctx)?.let { lat = "%.5f".format(it.latitude); lng = "%.5f".format(it.longitude) }
        ?: Toast.makeText(ctx, "Location not available yet – open Maps once, or type coordinates.", Toast.LENGTH_LONG).show()
    val locPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) fillLocation() }

    Scaffold(topBar = {
        BackBar(if (id < 0) "Add a place" else "Edit place", { nav.popBackStack() }) {
            TextButton({
                val la = lat.toDoubleOrNull(); val ln = lng.toDoubleOrNull()
                error = when {
                    p.name.isBlank() -> "Name is required"
                    p.region.isBlank() -> "Region / city is required"
                    la == null || la !in -90.0..90.0 || ln == null || ln !in -180.0..180.0 -> "Valid latitude and longitude are required"
                    p.description.isBlank() -> "Add a short description"
                    else -> null
                }
                if (error == null) scope.launch(Dispatchers.Main) {
                    val local = p.copy(name = p.name.trim(), region = p.region.trim(), lat = la!!, lng = ln!!,
                        dailyCost = cost.toIntOrNull() ?: 0, minDays = (days.toIntOrNull() ?: 1).coerceAtLeast(1), updatedAt = System.currentTimeMillis())
                    // My own places are shared with everyone; built-in ones stay local edits.
                    App.dao.upsert(if (!local.userAdded) local else runCatching { Cloud.publish(ctx, local) }.getOrElse {
                        Toast.makeText(ctx, "Saved on this phone. Sharing failed: ${it.message}", Toast.LENGTH_LONG).show(); local
                    })
                    nav.popBackStack()
                }
            }) { Text("Save") }
        }
    }) { pad ->
        if (!loaded) return@Scaffold
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            if (p.userAdded && Cloud.enabled) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Shared with all TripGuide users. By saving you agree to the community rules.", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                TextButton({ Util.open(ctx, RULES_URL) }) { Text("Rules") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
            OutlinedTextField(p.name, { p = p.copy(name = it) }, Modifier.fillMaxWidth(), label = { Text("Name *") }, singleLine = true)
            OutlinedTextField(p.region, { p = p.copy(region = it) }, Modifier.fillMaxWidth(), label = { Text("Region / city *") }, singleLine = true)

            Section("Photos")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(p.photos) { ph ->
                    Box {
                        Photo(ph, Modifier.size(96.dp))
                        IconButton({ p = p.copy(photos = p.photos - ph) }, Modifier.align(Alignment.TopEnd).size(28.dp)) {
                            Icon(Icons.Default.Cancel, "Remove photo", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                item {
                    OutlinedButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.size(96.dp)) {
                        Icon(Icons.Default.AddAPhoto, "Add photos")
                    }
                }
            }

            Section("Location *")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Latitude", lat, Modifier.weight(1f), decimal = true) { lat = it }
                NumberField("Longitude", lng, Modifier.weight(1f), decimal = true) { lng = it }
            }
            TextButton({
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) fillLocation()
                else locPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }) { Icon(Icons.Default.MyLocation, null); Text(" Use my current location") }

            Section("Category")
            ChipPicker(Catalog.categories, listOf(p.category), single = true) { p = p.copy(category = it.first()) }
            Section("Vibes")
            ChipPicker(Catalog.vibes, p.vibes) { p = p.copy(vibes = it) }
            Section("Amenities")
            CheckboxGrid(Catalog.amenities, p.amenities) { p = p.copy(amenities = it) }
            Section("Reachable by")
            ChipPicker(Catalog.transport, p.transport) { p = p.copy(transport = it) }
            Section("Best months to visit")
            ChipPicker(Catalog.months, p.bestMonths.map { Catalog.months[it - 1] }) { sel ->
                p = p.copy(bestMonths = sel.map { Catalog.months.indexOf(it) + 1 }.sorted())
            }
            Section("Costs & duration")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Stay+food /person/day (${Prefs.currency})", cost, Modifier.weight(1f)) { cost = it }
                NumberField("Recommended days", days, Modifier.weight(1f)) { days = it }
            }

            Section("Information")
            listOf<Triple<String, String, (String) -> Unit>>(
                Triple("Description *", p.description) { p = p.copy(description = it) },
                Triple("How to get there", p.howToReach) { p = p.copy(howToReach = it) },
                Triple("Guide / itinerary / where to stay & eat", p.guide) { p = p.copy(guide = it) },
                Triple("Tips", p.tips) { p = p.copy(tips = it) },
                Triple("Safety & emergency contacts", p.safety) { p = p.copy(safety = it) },
            ).forEach { (label, value, set) ->
                OutlinedTextField(value, set, Modifier.fillMaxWidth().padding(bottom = 8.dp), label = { Text(label) }, minLines = 3)
            }
        }
    }
}
