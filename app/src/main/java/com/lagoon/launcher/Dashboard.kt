package com.lagoon.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The tile home screen. On a wide screen (a tablet held sideways) every tile fits at
 * once; on a narrow one the tiles stack and scroll.
 */
@Composable
fun Dashboard(
    prefs: LauncherPrefs,
    accent: Color,
    active: Boolean,
    resumeTicks: Int,
    onOpenShade: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    nestedScroll: NestedScrollConnection,
    modifier: Modifier = Modifier,
) {
    val tiles = DashboardTiles(prefs, accent, active, resumeTicks, onOpenShade, onOpenSearch, onOpenSettings)
    BoxWithConstraints(modifier) {
        if (maxWidth >= 700.dp && maxHeight >= 300.dp) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1.3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    tiles.Clock(Modifier.weight(1f).fillMaxWidth(), scale = 0.5f, stacked = false)
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        tiles.Calendar(Modifier.weight(1.2f).fillMaxHeight())
                        tiles.Battery(Modifier.weight(1f).fillMaxHeight())
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    tiles.Weather(Modifier.weight(1f).fillMaxWidth())
                    tiles.Media(Modifier.weight(1f).fillMaxWidth())
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    tiles.Controls(Modifier.weight(1f).fillMaxWidth(), labels = false)
                    tiles.Notifications(Modifier.weight(1f).fillMaxWidth())
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().nestedScroll(nestedScroll).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                tiles.Clock(Modifier.fillMaxWidth(), scale = 0.72f, stacked = true)
                Row(Modifier.fillMaxWidth().height(232.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    tiles.Weather(Modifier.weight(1.35f).fillMaxHeight(), forecast = false)
                    tiles.Battery(Modifier.weight(1f).fillMaxHeight())
                }
                tiles.Media(Modifier.fillMaxWidth())
                tiles.Controls(Modifier.fillMaxWidth(), labels = true)
                Row(Modifier.fillMaxWidth().height(220.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    tiles.Calendar(Modifier.weight(1f).fillMaxHeight())
                    tiles.Notifications(Modifier.weight(1f).fillMaxHeight())
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

private class DashboardTiles(
    val prefs: LauncherPrefs,
    val accent: Color,
    val active: Boolean,
    val resumeTicks: Int,
    val onOpenShade: () -> Unit,
    val onOpenSearch: () -> Unit,
    val onOpenSettings: () -> Unit,
) {
    @Composable
    fun Clock(modifier: Modifier, scale: Float, stacked: Boolean) {
        Tile(
            modifier,
            accent = accent,
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallRound(Icons.Rounded.Search, "Search apps", onOpenSearch)
                    SmallRound(Icons.Rounded.Tune, "Lagoon settings", onOpenSettings)
                }
            },
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                HomeClock(prefs, Color.White, accent, centered = false, active = active, shadow = false, scaleOverride = scale, stackedOverride = stacked)
            }
        }
    }

    @Composable
    fun Weather(modifier: Modifier, forecast: Boolean = true) {
        Tile(modifier, title = prefs.weatherPlace.ifBlank { "Weather" }, accent = accent) {
            WeatherCard(prefs, accent, animate = active, onSetLocation = onOpenSettings, showForecast = forecast)
        }
    }

    @Composable
    fun Media(modifier: Modifier) {
        Tile(modifier, title = "Now playing", accent = accent) {
            if (needsAccess()) AccessPrompt("Allow notification access to control music from here.")
            else MediaCard(accent, compact = true)
        }
    }

    @Composable
    fun Controls(modifier: Modifier, labels: Boolean) {
        val all = quickToggles(resumeTicks)
        // Wi-Fi, Bluetooth, Torch, Do not disturb, Auto-rotate, Location
        val picked = listOf(0, 1, 3, 4, 5, 6).map { all[it] }
        Tile(modifier, title = "Controls", accent = accent) {
            ToggleGrid(picked, columns = 3, accent = accent, size = if (labels) 52.dp else 46.dp, showLabels = labels)
        }
    }

    @Composable
    fun Battery(modifier: Modifier) {
        val battery = rememberBattery()
        Tile(modifier, title = "Battery", accent = accent) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BatteryRing(battery, accent, 76.dp)
                BatteryLabel(battery)
            }
            StorageBar(accent)
        }
    }

    @Composable
    fun Calendar(modifier: Modifier) {
        Tile(modifier, accent = accent) {
            MonthCalendar(accent, Modifier.fillMaxWidth())
        }
    }

    @Composable
    fun Notifications(modifier: Modifier) {
        val items = NotificationHub.items
        Tile(
            modifier,
            title = "Notifications",
            accent = accent,
            onClick = onOpenShade,
            trailing = {
                if (items.isNotEmpty()) {
                    Text(
                        "${items.size}",
                        color = Lagoon.Deep,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).background(accent).padding(horizontal = 9.dp, vertical = 2.dp),
                    )
                }
            },
        ) {
            when {
                needsAccess() -> AccessPrompt("Allow notification access to see them here and in the control centre.")
                items.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.NotificationsNone, null, tint = Lagoon.Muted, modifier = Modifier.size(22.dp))
                    Text("You're all caught up", color = Lagoon.Muted, fontSize = 14.sp)
                }
                else -> items.take(2).forEach { NotificationCard(it, prefs, showText = false) }
            }
        }
    }

    @Composable
    private fun needsAccess(): Boolean {
        val context = LocalContext.current
        val granted = remember(resumeTicks) { NotificationHub.hasAccess(context) }
        return !granted && !NotificationHub.connected
    }

    @Composable
    private fun AccessPrompt(text: String) {
        val context = LocalContext.current
        Text(text, color = Lagoon.Muted, fontSize = 13.sp)
        TileButton("Allow access", accent) { NotificationHub.openAccessSettings(context) }
    }

    @Composable
    private fun SmallRound(icon: ImageVector, description: String, onClick: () -> Unit) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Color(0x1FFFFFFF)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, description, tint = Lagoon.Text, modifier = Modifier.size(18.dp))
        }
    }
}
