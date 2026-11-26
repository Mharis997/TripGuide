package com.tripguide.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deepLink = if (savedInstanceState == null) intent.getStringExtra("route") else null
        setContent { TripTheme { AppNav(deepLink) } }
    }
}

@Composable
fun TripTheme(content: @Composable () -> Unit) {
    val dark = when (Prefs.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val scheme = when {
        Prefs.dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF5ED6C3), secondary = Color(0xFFFFB59A), tertiary = Color(0xFFB4C8FF))
        else -> lightColorScheme(primary = Color(0xFF00695C), secondary = Color(0xFFD84315), tertiary = Color(0xFF3557A6))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab("explore", "Explore", Icons.Default.Explore),
    Tab("plan", "Plan", Icons.Default.AutoAwesome),
    Tab("trips", "My trips", Icons.Default.Luggage),
    Tab("settings", "Settings", Icons.Default.Settings),
)

@Composable
fun AppNav(deepLink: String?) {
    val nav = rememberNavController()
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Prefs.askedNotifications) {
            Prefs.askedNotifications = true
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        deepLink?.let { nav.navigate(it) }
    }
    val current = nav.currentBackStackEntryAsState().value?.destination?.route?.substringBefore("?")
    Scaffold(bottomBar = {
        if (tabs.any { it.route == current }) NavigationBar {
            tabs.forEach { t ->
                NavigationBarItem(selected = current == t.route, label = { Text(t.label) }, icon = { Icon(t.icon, null) }, onClick = {
                    nav.navigate(t.route) { popUpTo("explore") { saveState = true }; launchSingleTop = true; restoreState = true }
                })
            }
        }
    }) { pad ->
        NavHost(nav, "explore", Modifier.padding(pad).consumeWindowInsets(pad)) {
            composable("explore") { ExploreScreen(nav) }
            composable("plan?place={place}", listOf(navArgument("place") { type = NavType.LongType; defaultValue = -1L })) {
                PlanScreen(nav, it.arguments!!.getLong("place"))
            }
            composable("trips") { TripsScreen(nav) }
            composable("settings") { SettingsScreen() }
            composable("place/{id}", listOf(navArgument("id") { type = NavType.LongType })) { PlaceScreen(nav, it.arguments!!.getLong("id")) }
            composable("edit?id={id}", listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) {
                EditPlaceScreen(nav, it.arguments!!.getLong("id"))
            }
            composable("trip/{id}", listOf(navArgument("id") { type = NavType.LongType })) { TripScreen(nav, it.arguments!!.getLong("id")) }
        }
    }
}
