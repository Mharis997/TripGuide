package com.tripguide.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Navigation
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) =
    TopAppBar(title = { Text(title, maxLines = 1) }, actions = actions, navigationIcon = {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    })

@Composable
fun Photo(model: Any?, modifier: Modifier = Modifier) {
    if (model == null) Box(modifier.background(MaterialTheme.colorScheme.primaryContainer), Alignment.Center) {
        Icon(Icons.Default.Landscape, null, Modifier.size(48.dp), MaterialTheme.colorScheme.onPrimaryContainer)
    } else AsyncImage(model, null, modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoPager(photos: List<String>, modifier: Modifier = Modifier) {
    if (photos.isEmpty()) return Photo(null, modifier)
    val state = rememberPagerState { photos.size }
    Box(modifier) {
        HorizontalPager(state, Modifier.fillMaxSize()) { Photo(photos[it], Modifier.fillMaxSize()) }
        if (photos.size > 1) Text("${state.currentPage + 1}/${photos.size}", Modifier.align(Alignment.BottomEnd).padding(8.dp)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = .5f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipPicker(options: List<String>, selected: Collection<String>, single: Boolean = false, onChange: (List<String>) -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(o in selected, label = { Text(o) }, onClick = {
                onChange(if (single) listOf(o) else if (o in selected) selected - o else selected + o)
            })
        }
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckboxGrid(options: List<String>, selected: Collection<String>, onChange: (List<String>) -> Unit) =
    FlowRow(maxItemsInEachRow = 2) {
        options.forEach { o ->
            CheckRow(o, o in selected, Modifier.weight(1f)) { onChange(if (it) selected + o else selected - o) }
        }
    }

/** Checkbox whose whole row, label included, is the touch target. */
@Composable
fun CheckRow(label: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) =
    Row(modifier.toggleable(checked, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, null, Modifier.padding(end = 8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }

@Composable
fun Section(title: String, modifier: Modifier = Modifier) =
    Text(title, modifier.padding(top = 16.dp, bottom = 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

@Composable
fun StatusBadge(status: String) {
    if (status == "Open") return
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp)) {
        Text(status, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun NumberField(label: String, value: String, modifier: Modifier = Modifier, decimal: Boolean = false, onChange: (String) -> Unit) =
    OutlinedTextField(value, { v -> if (v.all { it.isDigit() || (decimal && (it == '.' || it == '-')) }) onChange(v) }, modifier,
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number))

private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a")
fun fmt(millis: Long): String = dateFmt.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
fun fmtDate(millis: Long): String = DateTimeFormatter.ofPattern("d MMM yyyy").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** Platform date picker followed by time picker. */
@Composable
fun DateTimeButton(label: String, millis: Long, modifier: Modifier = Modifier, onPick: (Long) -> Unit) {
    val ctx = LocalContext.current
    OutlinedButton(modifier = modifier, onClick = {
        val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()
        DatePickerDialog(ctx, { _, y, m, d ->
            TimePickerDialog(ctx, { _, h, min ->
                onPick(LocalDateTime.of(y, m + 1, d, h, min).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            }, t.hour, t.minute, false).show()
        }, t.year, t.monthValue - 1, t.dayOfMonth).apply { datePicker.minDate = System.currentTimeMillis() - 60_000 }.show()
    }) {
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(fmt(millis), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun WeatherCard(lat: Double, lng: Double) {
    val days by produceState<List<Util.Day>?>(null, lat, lng) { value = Util.forecast(lat, lng) }
    Section("7-day weather")
    when {
        days == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
        days!!.isEmpty() -> Text("Weather unavailable offline.", style = MaterialTheme.typography.bodySmall)
        else -> Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            days!!.forEach { d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(java.time.LocalDate.parse(d.date).dayOfWeek.name.take(3), style = MaterialTheme.typography.labelSmall)
                    Text(Util.weatherEmoji(d.code))
                    Text("${d.max.toInt()}°", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("${d.min.toInt()}°", style = MaterialTheme.typography.labelSmall)
                    Text("${d.rain}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) = Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
    Text(label, Modifier.weight(.45f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, Modifier.weight(.55f), style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun PlaceThumb(p: Place, modifier: Modifier = Modifier) =
    Photo(p.photos.firstOrNull(), modifier.clip(RoundedCornerShape(12.dp)))

/** Bookmark that files a place under one of Catalog.lists (Favorites, Plan to go, Visited). */
@Composable
fun SaveButton(p: Place, tint: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }) {
            Icon(if (p.saved.isEmpty()) Icons.Default.BookmarkBorder else Icons.Default.Bookmark,
                if (p.saved.isEmpty()) "Save" else "Saved: ${p.saved}", tint = if (tint == androidx.compose.ui.graphics.Color.Unspecified) LocalContentColor.current else tint)
        }
        DropdownMenu(open, { open = false }) {
            (Catalog.lists + "").forEach { l ->
                DropdownMenuItem({ Text(if (l.isEmpty()) "Remove from lists" else l) }, enabled = !(l.isEmpty() && p.saved.isEmpty()),
                    leadingIcon = { RadioButton(p.saved == l && l.isNotEmpty(), null) },
                    onClick = { open = false; scope.launch { App.dao.upsert(p.copy(saved = l)) } })
            }
        }
    }
}

/** Starts turn-by-turn navigation (Android Auto ready). */
@Composable
fun GuideButton(p: Place, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Button({ Util.navigate(ctx, p.lat, p.lng) }, modifier) { Icon(Icons.Default.Navigation, null); Text(" Guide") }
}

/** Plays a reel through Instagram's official embed page. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReelEmbed(url: String, modifier: Modifier = Modifier) = AndroidView(
    factory = { ctx ->
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            webViewClient = WebViewClient() // keep the embed inside the app
            setBackgroundColor(android.graphics.Color.BLACK)
            loadUrl(embedUrl(url))
        }
    },
    update = { if (it.url?.startsWith(embedUrl(url)) != true) it.loadUrl(embedUrl(url)) },
    onRelease = { it.destroy() },
    modifier = modifier,
)
