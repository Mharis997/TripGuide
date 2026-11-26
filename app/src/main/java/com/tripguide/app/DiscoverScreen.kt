package com.tripguide.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

/** One Google Maps result: photo, rating, price, opens the pin in Google Maps. */
@Composable
fun GPlaceCard(p: GPlace, fromLat: Double? = null, fromLng: Double? = null) {
    val ctx = LocalContext.current
    OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { Util.open(ctx, p.mapsUri) }) {
        Row(Modifier.padding(10.dp)) {
            Column {
                Photo(p.photo?.let(Google::photoUrl), Modifier.size(92.dp).clip(RoundedCornerShape(10.dp)))
                p.photoBy?.let { Text("© $it", Modifier.width(92.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                p.rating?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, Modifier.size(16.dp), MaterialTheme.colorScheme.secondary)
                        Text(" %.1f (%,d)".format(it, p.ratings), style = MaterialTheme.typography.bodySmall)
                    }
                }
                val meta = listOfNotNull(p.type.ifBlank { null }, p.priceRange ?: p.priceLevel,
                    fromLat?.let { Prefs.distance(Planner.km(it, fromLng!!, p.lat, p.lng).toInt()) + " away" })
                Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text(p.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, null, Modifier.size(14.dp), MaterialTheme.colorScheme.error)
                    Text(" Open pin in Google Maps", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** Required attribution when showing Places data without a Google map. */
@Composable
fun GoogleAttribution() = Text("Data and photos: Google Maps", Modifier.padding(vertical = 6.dp),
    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun Results(result: Result<List<GPlace>>?, empty: String, fromLat: Double?, fromLng: Double?) {
    when {
        !Google.enabled -> Text("Google Maps results need a Places API key (see README → Google Maps setup).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        result == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
        result.isFailure -> Text("Couldn't load from Google: ${result.exceptionOrNull()?.message}", color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)
        result.getOrThrow().isEmpty() -> Text(empty)
        else -> { result.getOrThrow().forEach { GPlaceCard(it, fromLat, fromLng) }; GoogleAttribution() }
    }
}

/** Attractions or places to stay around a point, live from Google Maps. Stays also get live-price links. */
@Composable
fun GoogleNearby(lat: Double, lng: Double, where: String, stays: Boolean, checkIn: Long? = null, checkOut: Long? = null, people: Int = 2) {
    val ctx = LocalContext.current
    val result by produceState<Result<List<GPlace>>?>(null, lat, lng, stays) {
        if (Google.enabled) value = Google.nearby(ctx, lat, lng, if (stays) Google.stayTypes else Google.attractionTypes)
    }
    Column {
        if (stays) {
            Text(if (checkIn != null) "Live prices for ${fmtDate(checkIn)} – ${fmtDate(checkOut!!)}, $people guests:" else "Live prices for your dates:",
                style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                Button({ Util.open(ctx, Google.bookingUrl(where, checkIn, checkOut, people)) }, Modifier.weight(1f)) { Text("Booking.com") }
                OutlinedButton({ Util.open(ctx, Google.googleHotelsUrl(where)) }, Modifier.weight(1f)) { Text("Google Hotels") }
            }
        }
        Results(result, if (stays) "No places to stay found within 30 km." else "No attractions found within 30 km.", lat, lng)
    }
}

/** Search any sightseeing spot on Google Maps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(nav: NavController) {
    val ctx = LocalContext.current
    val default = "tourist attractions in ${if (Prefs.homeName == "My location") "Kerala" else Prefs.homeName}"
    var text by rememberSaveable { mutableStateOf(default) }
    var query by rememberSaveable { mutableStateOf(default) }
    val result by produceState<Result<List<GPlace>>?>(null, query) {
        value = null
        if (Google.enabled && query.isNotBlank()) value = Google.search(ctx, query)
    }
    Scaffold(topBar = { BackBar("Discover on Google Maps", { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item {
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("e.g. waterfalls near Thrissur") }, leadingIcon = { Icon(Icons.Default.TravelExplore, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions { query = text.trim() },
                    trailingIcon = { IconButton({ query = text.trim() }) { Icon(Icons.Default.Search, "Search") } })
            }
            item { Results(result, "Nothing found. Try another search.", null, null) }
        }
    }
}
