package com.lagoon.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.roundToInt

val TileGlass = Color(0x6B03181D)
val TileLine = Color(0x1FFFFFFF)
val TileShape = RoundedCornerShape(26.dp)

/** A frosted glass card, the building block of the dashboard. */
@Composable
fun Tile(
    modifier: Modifier = Modifier,
    title: String? = null,
    accent: Color = accentOptions.first().color,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(TileShape)
            .background(TileGlass)
            .border(1.dp, TileLine, TileShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null || trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (title != null) {
                    Box(Modifier.size(6.dp).background(accent, CircleShape))
                    Text(
                        title.uppercase(),
                        color = Lagoon.Muted,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.6.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                trailing?.invoke()
            }
        }
        content()
    }
}

@Composable
fun TileButton(text: String, accent: Color, onClick: () -> Unit) {
    Text(
        text,
        color = Lagoon.Deep,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(CircleShape)
            .background(accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun RoundControl(icon: ImageVector, description: String, size: Dp, background: Color, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Album art, track and transport controls for whatever is playing. */
@Composable
fun MediaCard(accent: Color, compact: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val hasMedia = MediaHub.title != null
    val artSize = if (compact) 48.dp else 60.dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = hasMedia) { MediaHub.openApp(context) },
        ) {
            Box(
                Modifier
                    .size(artSize)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(accent, Color(0xFF0A4F59)))),
                contentAlignment = Alignment.Center,
            ) {
                val art = MediaHub.art
                if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Rounded.MusicNote, null, tint = Lagoon.Deep, modifier = Modifier.size(artSize * 0.45f))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    MediaHub.title ?: "Nothing playing",
                    color = Lagoon.Text,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    fontSize = if (compact) 15.sp else 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    MediaHub.artist ?: if (hasMedia) "" else "Play music in any app",
                    color = Lagoon.Muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            val dim = if (hasMedia) 1f else 0.4f
            RoundControl(Icons.Rounded.SkipPrevious, "Previous", 40.dp, Color(0x1FFFFFFF), Lagoon.Text.copy(alpha = dim)) { MediaHub.previous() }
            RoundControl(
                if (MediaHub.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (MediaHub.playing) "Pause" else "Play",
                if (compact) 46.dp else 52.dp,
                accent.copy(alpha = if (hasMedia) 1f else 0.4f),
                Lagoon.Deep,
            ) { MediaHub.playPause() }
            RoundControl(Icons.Rounded.SkipNext, "Next", 40.dp, Color(0x1FFFFFFF), Lagoon.Text.copy(alpha = dim)) { MediaHub.next() }
        }
    }
}

fun timeAgo(time: Long): String {
    val minutes = (System.currentTimeMillis() - time) / 60_000L
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> "${minutes / (24 * 60)}d"
    }
}

@Composable
fun NotificationCard(
    item: LagoonNotification,
    prefs: LauncherPrefs,
    showText: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x1AFFFFFF))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(34.dp).clip(iconShape(prefs.iconShape)).background(Color(0x26FFFFFF))) {
            item.icon?.let { Image(it, null, Modifier.fillMaxSize()) }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.appName, color = Lagoon.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text("· ${timeAgo(item.time)}", color = Lagoon.Muted.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            if (item.title.isNotBlank()) {
                Text(item.title, color = Lagoon.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (showText && item.text.isNotBlank()) {
                Text(item.text, color = Lagoon.Text.copy(alpha = 0.75f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A ring that fills to the battery level, with the percentage in the middle. */
@Composable
fun BatteryRing(battery: BatteryInfo, accent: Color, size: Dp) {
    val level by animateFloatAsState(battery.percent / 100f, tween(900), label = "battery")
    val color = when {
        battery.charging -> accent
        battery.percent <= 15 -> Color(0xFFFF8A73)
        else -> accent
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.09f
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(Color(0x24FFFFFF), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * level, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (battery.charging) Icon(Icons.Rounded.Bolt, "Charging", tint = accent, modifier = Modifier.size(size * 0.16f))
            Text(
                "${battery.percent}%",
                color = Lagoon.Text,
                fontFamily = Outfit,
                fontWeight = FontWeight.Light,
                fontSize = (size.value * 0.22f).sp,
            )
        }
    }
}

/** This month at a glance with today picked out in the accent colour. */
@Composable
fun MonthCalendar(accent: Color, modifier: Modifier = Modifier) {
    val today = remember { LocalDate.now() }
    val first = today.withDayOfMonth(1)
    val offset = first.dayOfWeek.value % 7 // Sunday first
    val days = today.lengthOfMonth()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            today.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault()) + " " + today.year,
            color = Lagoon.Text,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
        )
        Row(Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, color = Lagoon.Muted, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        val cells = (0 until offset).map { 0 } + (1..days).toList()
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).height(17.dp), contentAlignment = Alignment.Center) {
                        if (day == today.dayOfMonth) Box(Modifier.size(17.dp).background(accent, CircleShape))
                        if (day > 0) {
                            Text(
                                "$day",
                                color = if (day == today.dayOfMonth) Lagoon.Deep else Lagoon.Text.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                fontWeight = if (day == today.dayOfMonth) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

fun formatTemp(value: Double): String = "${value.roundToInt()}°"

/** Current conditions with a short forecast row. */
@Composable
fun WeatherCard(prefs: LauncherPrefs, accent: Color, animate: Boolean, onSetLocation: () -> Unit, showForecast: Boolean = true) {
    val report = WeatherHub.report
    if (prefs.weatherLat.isBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Set your town to see the forecast.", color = Lagoon.Muted, fontSize = 14.sp)
            TileButton("Set location", accent, onSetLocation)
        }
        return
    }
    if (report == null) {
        Text(
            if (WeatherHub.error != null) "Weather is unavailable right now." else "Loading weather…",
            color = Lagoon.Muted,
            fontSize = 14.sp,
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        WeatherGlyph(report.code, report.isDay, 58.dp, accent, animate)
        Column(Modifier.weight(1f)) {
            Text(formatTemp(report.temp), color = Lagoon.Text, fontFamily = Outfit, fontWeight = FontWeight.Light, fontSize = 40.sp)
            Text(
                skyLabel(report.code, report.isDay) + (report.days.firstOrNull()?.let { "  ·  ${formatTemp(it.high)} / ${formatTemp(it.low)}" } ?: ""),
                color = Lagoon.Muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (showForecast && report.days.size > 1) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            report.days.drop(1).take(4).forEach { day ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(day.date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.getDefault()), color = Lagoon.Muted, fontSize = 11.sp)
                    WeatherGlyph(day.code, true, 26.dp, accent, animate = false)
                    Text(formatTemp(day.high), color = Lagoon.Text, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun StorageBar(accent: Color) {
    val (used, total) = remember { Controls.storage() }
    if (total <= 0L) return
    val gb = 1024.0 * 1024.0 * 1024.0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0x24FFFFFF))) {
            Box(Modifier.fillMaxWidth((used.toFloat() / total).coerceIn(0f, 1f)).height(6.dp).background(accent, CircleShape))
        }
        Text("%.0f of %.0f GB".format(used / gb, total / gb), color = Lagoon.Muted, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
fun BatteryLabel(battery: BatteryInfo) {
    Text(if (battery.charging) "Charging" else "On battery", color = Lagoon.Muted, fontSize = 12.sp)
}

@Composable
fun Gap(width: Dp) = Spacer(Modifier.width(width))
