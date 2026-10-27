@file:OptIn(ExperimentalMaterial3Api::class)

package com.lagoon.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Lagoon's own notification shade: quick switches, brightness and volume, music,
 * and the live notification list. It slides down from the top of the home screen.
 */
@Composable
fun ControlCenter(
    prefs: LauncherPrefs,
    accent: Color,
    resumeTicks: Int,
    open: Boolean,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    listScroll: NestedScrollConnection,
    modifier: Modifier = Modifier,
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 700
    val shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
    Column(
        modifier
            .fillMaxSize()
            .background(Color(0xF7041A20), shape)
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = if (wide) 32.dp else 18.dp)
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ShadeHeader(prefs, accent, open, onOpenSettings)
        if (wide) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Panel { ToggleGrid(quickToggles(resumeTicks), columns = 5, accent = accent, size = 54.dp) }
                    Panel {
                        BrightnessSlider(accent, resumeTicks)
                        VolumeSlider(accent, resumeTicks)
                    }
                    Panel { MediaCard(accent, compact = true) }
                }
                NotificationList(prefs, accent, resumeTicks, onClose, listScroll, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            // On a phone-sized screen everything scrolls together so nothing gets cut off.
            val context = LocalContext.current
            val granted = remember(resumeTicks) { NotificationHub.hasAccess(context) } || NotificationHub.connected
            val items = NotificationHub.items
            val toggles = quickToggles(resumeTicks)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().nestedScroll(listScroll),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { Panel { ToggleGrid(toggles, columns = 5, accent = accent, size = 44.dp) } }
                item {
                    Panel {
                        BrightnessSlider(accent, resumeTicks)
                        VolumeSlider(accent, resumeTicks)
                    }
                }
                if (MediaHub.title != null) item { Panel { MediaCard(accent, compact = true) } }
                item { NotificationHeader(accent, items) }
                when {
                    !granted -> item { AccessCard(accent) }
                    items.isEmpty() -> item { CaughtUp() }
                    else -> items(items, key = { it.key }) { item ->
                        SwipeableNotification(item, prefs) {
                            NotificationHub.open(item)
                            onClose()
                        }
                    }
                }
            }
        }
        // The handle: drag it up (or tap it) to put the panel away.
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 44.dp, height = 4.dp).background(Lagoon.Muted.copy(alpha = 0.6f), CircleShape))
        }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0x14FFFFFF))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun ShadeHeader(prefs: LauncherPrefs, accent: Color, open: Boolean, onOpenSettings: () -> Unit) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(open) {
        while (open) {
            now = LocalDateTime.now()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    val battery = rememberBattery()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                now.format(DateTimeFormatter.ofPattern(if (prefs.use24h) "HH:mm" else "h:mm a")),
                color = Lagoon.Text,
                fontFamily = Outfit,
                fontWeight = FontWeight.ExtraLight,
                fontSize = 46.sp,
            )
            Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = Lagoon.Muted, fontSize = 14.sp)
        }
        BatteryRing(battery, accent, 52.dp)
        RoundIconButton(Icons.Rounded.Tune, "Lagoon settings", onOpenSettings)
    }
}

@Composable
private fun NotificationList(
    prefs: LauncherPrefs,
    accent: Color,
    resumeTicks: Int,
    onClose: () -> Unit,
    listScroll: NestedScrollConnection,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val granted = remember(resumeTicks) { NotificationHub.hasAccess(context) } || NotificationHub.connected
    val items = NotificationHub.items
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NotificationHeader(accent, items)
        when {
            !granted -> AccessCard(accent)
            items.isEmpty() -> CaughtUp()
            else -> LazyColumn(
                Modifier.fillMaxWidth().weight(1f).nestedScroll(listScroll),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(items, key = { it.key }) { item ->
                    SwipeableNotification(item, prefs) {
                        NotificationHub.open(item)
                        onClose()
                    }
                }
            }
        }
        Spacer(Modifier.height(0.dp))
    }
}

@Composable
private fun NotificationHeader(accent: Color, items: List<LagoonNotification>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "NOTIFICATIONS",
            color = accent,
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.weight(1f),
        )
        if (items.any { it.clearable }) {
            Row(
                Modifier.clip(CircleShape).clickable { NotificationHub.clearAll() }.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.ClearAll, null, tint = Lagoon.Text, modifier = Modifier.size(18.dp))
                Text("Clear all", color = Lagoon.Text, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun AccessCard(accent: Color) {
    val context = LocalContext.current
    Panel {
        Text("Lagoon can show your notifications here.", color = Lagoon.Text, fontFamily = Outfit, fontSize = 17.sp)
        Text(
            "Turn on notification access for Lagoon. It stays on this device; nothing is sent anywhere.",
            color = Lagoon.Muted,
            fontSize = 13.sp,
        )
        TileButton("Allow notification access", accent) { NotificationHub.openAccessSettings(context) }
    }
}

@Composable
private fun CaughtUp() {
    Column(
        Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.NotificationsNone, null, tint = Lagoon.Muted, modifier = Modifier.size(34.dp))
        Text("You're all caught up", color = Lagoon.Muted, fontSize = 15.sp)
    }
}

@Composable
private fun SwipeableNotification(item: LagoonNotification, prefs: LauncherPrefs, onOpen: () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled && item.clearable) {
                NotificationHub.dismiss(item)
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {},
        enableDismissFromStartToEnd = item.clearable,
        enableDismissFromEndToStart = item.clearable,
    ) {
        NotificationCard(item, prefs, showText = true, onClick = onOpen)
    }
}
