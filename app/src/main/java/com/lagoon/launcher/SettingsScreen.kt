package com.lagoon.launcher

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(prefs: LauncherPrefs, apps: List<AppEntry>, accent: Color, resumeTicks: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { CustomWallpaper.import(context, uri) }
            if (ok) {
                prefs.wallpaper = "custom"
                prefs.wallpaperVersion = prefs.wallpaperVersion + 1
            } else {
                Toast.makeText(context, "That image couldn't be opened. Try another photo.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val isDefault = remember(resumeTicks) { isDefaultHome(context) }
    val notificationsGranted = remember(resumeTicks) { NotificationHub.hasAccess(context) } || NotificationHub.connected
    val writeSettingsGranted = remember(resumeTicks) { Controls.canWriteSettings(context) }
    var city by remember { mutableStateOf("") }

    fun applyPlace(place: Place?) {
        if (place == null) {
            Toast.makeText(context, "Couldn't find that place. Check the spelling and try again.", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.weatherLat = place.lat
        prefs.weatherLon = place.lon
        prefs.weatherPlace = place.name
        WeatherHub.forget(prefs)
        scope.launch { WeatherHub.refresh(prefs, force = true) }
    }

    fun useDeviceLocation() {
        scope.launch {
            val place = currentPlace(context)
            if (place == null) {
                Toast.makeText(context, "Location is off or unavailable. Type your town instead.", Toast.LENGTH_SHORT).show()
            } else {
                applyPlace(place)
            }
        }
    }

    fun searchCity() {
        if (city.isBlank()) return
        scope.launch { applyPlace(WeatherHub.searchCity(city)) }
    }

    fun setUnit(unit: String) {
        if (prefs.tempUnit == unit) return
        prefs.tempUnit = unit
        WeatherHub.forget(prefs)
        scope.launch { WeatherHub.refresh(prefs, force = true) }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) useDeviceLocation()
        else Toast.makeText(context, "Without location, type your town to get the weather.", Toast.LENGTH_SHORT).show()
    }
    val hiddenApps = apps.filter { it.key in prefs.hidden }
    val previewApps = apps.take(5)

    Column(
        Modifier
            .fillMaxSize()
            .background(Lagoon.Deep)
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back to home", onClose)
            Text("Settings", color = Lagoon.Text, fontFamily = Outfit, fontWeight = FontWeight.Light, fontSize = 34.sp)
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                if (!isDefault) DefaultHomeCard(accent) { openHomeSettings(context) }

                Section("Home screen", accent) {
                    Label("Layout")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Chip("Dashboard", prefs.homeLayout == "dashboard", accent, { prefs.homeLayout = "dashboard" })
                        Chip("Classic", prefs.homeLayout == "classic", accent, { prefs.homeLayout = "classic" })
                    }
                    Text(
                        if (prefs.homeLayout == "dashboard") "Glass tiles for the clock, weather, music, controls, battery, calendar and notifications."
                        else "A clean wallpaper with the clock, your pinned apps and the dock.",
                        color = Lagoon.Muted,
                        fontSize = 13.sp,
                    )
                    Label("Swipe down opens")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Chip("Lagoon control centre", prefs.swipeDown == "panel", accent, { prefs.swipeDown = "panel" })
                        Chip("System shade", prefs.swipeDown == "system", accent, { prefs.swipeDown = "system" })
                    }
                }

                Section("Lagoon lock screen", accent) {
                    SwitchRow(
                        "Show the Lagoon lock screen",
                        "Clock, weather, music and notifications each time you wake your device. Swipe up, then your PIN or pattern as usual.",
                        prefs.lagoonLock,
                        accent,
                    ) {
                        prefs.lagoonLock = it
                        LockService.sync(context, it)
                    }
                    Chip("Preview lock screen", false, accent, {
                        runCatching { context.startActivity(Intent(context, LockActivity::class.java)) }
                    })
                    if (prefs.lagoonLock) {
                        Text(
                            "Android shows a small \"Lagoon lock screen is on\" notification while this runs. On Huawei, also allow Lagoon under Settings › Battery › App launch so it isn't closed.",
                            color = Lagoon.Muted,
                            fontSize = 13.sp,
                        )
                    }
                }

                Section("Weather", accent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(prefs.weatherPlace.ifBlank { "No location set" }, color = Lagoon.Text, fontSize = 16.sp)
                            Text("Forecasts from Open-Meteo", color = Lagoon.Muted, fontSize = 13.sp)
                        }
                        Chip("Use my location", false, accent, {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                useDeviceLocation()
                            } else {
                                locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BasicTextField(
                            value = city,
                            onValueChange = { city = it.take(60) },
                            singleLine = true,
                            textStyle = TextStyle(color = Lagoon.Text, fontSize = 16.sp),
                            cursorBrush = SolidColor(accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { searchCity() }),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .background(Lagoon.SurfaceHigh, CircleShape)
                                .padding(horizontal = 18.dp),
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                    if (city.isEmpty()) Text("Type your town, e.g. Accra", color = Lagoon.Muted, fontSize = 16.sp)
                                    inner()
                                }
                            },
                        )
                        Chip("Search", false, accent, { searchCity() })
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Chip("°C", prefs.tempUnit == "c", accent, { setUnit("c") })
                        Chip("°F", prefs.tempUnit == "f", accent, { setUnit("f") })
                    }
                }

                Section("Permissions", accent) {
                    PermissionRow(
                        "Notification access",
                        "Notifications in the control centre and lock screen, and music controls",
                        notificationsGranted,
                        accent,
                    ) { NotificationHub.openAccessSettings(context) }
                    PermissionRow(
                        "Change system settings",
                        "Brightness and auto-rotate from the control centre",
                        writeSettingsGranted,
                        accent,
                    ) { Controls.requestWriteSettings(context) }
                }

                Section("Wallpaper", accent) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        wallpaperPresets.forEach { preset ->
                            WallpaperTile(preset.name, prefs.wallpaper == preset.id, accent, { prefs.wallpaper = preset.id }) {
                                Canvas(Modifier.fillMaxSize()) { preset.paint(this) }
                            }
                        }
                        WallpaperTile("Device", prefs.wallpaper == "system", accent, { prefs.wallpaper = "system" }) {
                            Box(Modifier.fillMaxSize().background(Lagoon.SurfaceHigh), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Home, null, tint = Lagoon.Muted)
                            }
                        }
                        WallpaperTile("Your photo", prefs.wallpaper == "custom", accent, { picker.launch("image/*") }) {
                            if (prefs.wallpaper == "custom") {
                                WallpaperLayer("custom", prefs.wallpaperVersion)
                            } else {
                                Box(Modifier.fillMaxSize().background(Lagoon.SurfaceHigh), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Add, null, tint = Lagoon.Muted)
                                }
                            }
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (prefs.wallpaper != "system") {
                            Chip("Use on lock screen too", false, accent, {
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) { applyToLockScreen(context, prefs.wallpaper) }
                                    Toast.makeText(
                                        context,
                                        if (ok) "Lock screen wallpaper updated" else "Your device didn't allow changing the lock screen",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            })
                        }
                        if (prefs.wallpaper == "custom") {
                            Chip("Change photo", false, accent, { picker.launch("image/*") })
                        }
                    }
                }

                Section("Clock", accent) {
                    Label("Style")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        clockStyles.forEach { style ->
                            Chip(style.name, prefs.clockStyle == style.id, accent, { prefs.clockStyle = style.id })
                        }
                    }
                    Label("Size")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Chip("Small", prefs.clockSize == "small", accent, { prefs.clockSize = "small" })
                        Chip("Medium", prefs.clockSize == "medium", accent, { prefs.clockSize = "medium" })
                        Chip("Large", prefs.clockSize == "large", accent, { prefs.clockSize = "large" })
                    }
                    Label("Position")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Chip("Left", prefs.clockAlign == "start", accent, { prefs.clockAlign = "start" })
                        Chip("Centre", prefs.clockAlign == "center", accent, { prefs.clockAlign = "center" })
                    }
                    SwitchRow("Show clock", "Time, date and greeting on the home screen", prefs.showClock, accent) { prefs.showClock = it }
                    SwitchRow("24-hour time", null, prefs.use24h, accent) { prefs.use24h = it }
                }

                Section("Accent colour", accent) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        accentOptions.forEach { option ->
                            val selected = prefs.accent == option.id
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(option.color)
                                        .clickable { prefs.accent = option.id },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (selected) Icon(Icons.Rounded.Check, "Selected", tint = Lagoon.Deep, modifier = Modifier.size(24.dp))
                                }
                                Text(option.name, color = if (selected) Lagoon.Text else Lagoon.Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }

                Section("Icons", accent) {
                    if (previewApps.isNotEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Lagoon.Deep, RoundedCornerShape(18.dp))
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            previewApps.forEach { app ->
                                AppIcon(app, prefs, accent, prefs.showLabels, Lagoon.Text, elevated = false, onClick = {}, onLongClick = {})
                            }
                        }
                    }
                    Label("Shape")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        iconShapes.forEach { option ->
                            val selected = prefs.iconShape == option.id
                            Chip(option.name, selected, accent, { prefs.iconShape = option.id }) {
                                ShapeSwatch(option.shape, if (selected) accent else Lagoon.Muted)
                            }
                        }
                    }
                    Label("Style")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Chip("Colour", prefs.iconStyle == "colour", accent, { prefs.iconStyle = "colour" })
                        Chip("Mono tint", prefs.iconStyle == "mono", accent, { prefs.iconStyle = "mono" })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label("Size", Modifier.weight(1f))
                        Text("${prefs.iconSize} dp", color = Lagoon.Muted, fontSize = 13.sp)
                    }
                    Slider(
                        value = prefs.iconSize.toFloat(),
                        onValueChange = { prefs.iconSize = it.roundToInt() },
                        valueRange = 44f..76f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = Lagoon.SurfaceHigh,
                            activeTickColor = Lagoon.Deep.copy(alpha = 0.4f),
                            inactiveTickColor = Lagoon.Muted.copy(alpha = 0.4f),
                        ),
                    )
                    SwitchRow("Show app names", null, prefs.showLabels, accent) { prefs.showLabels = it }
                }

                Section("Home & app list", accent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Columns", color = Lagoon.Text, fontSize = 16.sp)
                            Text("Apps per row in the app list and on the home screen", color = Lagoon.Muted, fontSize = 13.sp)
                        }
                        StepButton("−", prefs.columns > 4) { prefs.columns = prefs.columns - 1 }
                        Text(
                            "${prefs.columns}",
                            color = Lagoon.Text,
                            fontFamily = Outfit,
                            fontSize = 20.sp,
                            modifier = Modifier.width(44.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        StepButton("+", prefs.columns < 9) { prefs.columns = prefs.columns + 1 }
                    }
                    SwitchRow("Show dock", "The floating row of favourite apps", prefs.showDock, accent) { prefs.showDock = it }
                }

                Section("Motion", accent) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Chip("Off", prefs.motion == "off", accent, { prefs.motion = "off" })
                        Chip("Gentle", prefs.motion == "gentle", accent, { prefs.motion = "gentle" })
                        Chip("Lively", prefs.motion == "lively", accent, { prefs.motion = "lively" })
                    }
                    Text(
                        when (prefs.motion) {
                            "off" -> "Screens switch instantly. Uses the least battery."
                            "lively" -> "Springier movement with a little bounce."
                            else -> "Smooth, unhurried slides and fades."
                        },
                        color = Lagoon.Muted,
                        fontSize = 13.sp,
                    )
                }

                Section("Hidden apps", accent) {
                    if (hiddenApps.isEmpty()) {
                        Text("No hidden apps. Long-press an app and choose Hide to tuck it away.", color = Lagoon.Muted, fontSize = 14.sp)
                    } else {
                        hiddenApps.forEach { app ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Image(app.icon, null, Modifier.size(36.dp).clip(iconShape(prefs.iconShape)))
                                Text(
                                    app.label,
                                    color = Lagoon.Text,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "Show",
                                    color = accent,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { prefs.hidden = prefs.hidden - app.key }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }

                Column(
                    Modifier.fillMaxWidth().padding(bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Lagoon 2.0", color = Lagoon.Muted, fontFamily = Outfit, fontSize = 14.sp)
                    Text(
                        if (isDefault) "Lagoon is your home screen · ${apps.size} apps" else "${apps.size} apps installed",
                        color = Lagoon.Muted.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultHomeCard(accent: Color, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.14f), shape)
            .border(1.dp, accent.copy(alpha = 0.5f), shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Make Lagoon your home screen", color = Lagoon.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        Text(
            "On Huawei: Settings › Apps › Default apps › Launcher, then choose Lagoon. The Home button will then always bring you here.",
            color = Lagoon.Muted,
            fontSize = 14.sp,
        )
        Text(
            "Open settings",
            color = Lagoon.Deep,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier
                .clip(CircleShape)
                .background(accent)
                .clickable(onClick = onOpen)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun PermissionRow(title: String, subtitle: String, granted: Boolean, accent: Color, onAllow: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Lagoon.Text, fontSize = 16.sp)
            Text(subtitle, color = Lagoon.Muted, fontSize = 13.sp)
        }
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Check, null, tint = accent, modifier = Modifier.size(18.dp))
                Text("Allowed", color = accent, fontSize = 14.sp)
            }
        } else {
            Chip("Allow", false, accent, onAllow)
        }
    }
}

@Composable
private fun Section(title: String, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title.uppercase(),
            color = accent,
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
        )
        Column(
            Modifier.fillMaxWidth().background(Lagoon.Surface, RoundedCornerShape(24.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Lagoon.Text, fontSize = 16.sp, modifier = modifier)
}

@Composable
private fun WallpaperTile(
    name: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val border by animateColorAsState(if (selected) accent else Color.Transparent, label = "tile")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(width = 136.dp, height = 88.dp)
                .border(2.dp, border, RoundedCornerShape(18.dp))
                .padding(4.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
            content = content,
        )
        Text(name, color = if (selected) Lagoon.Text else Lagoon.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    val background by animateColorAsState(if (selected) accent.copy(alpha = 0.18f) else Lagoon.SurfaceHigh, label = "chip")
    val outline by animateColorAsState(if (selected) accent else Color.Transparent, label = "chipBorder")
    val shape = CircleShape
    Row(
        Modifier
            .clip(shape)
            .background(background)
            .border(1.dp, outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading?.invoke()
        Text(text, color = if (selected) accent else Lagoon.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ShapeSwatch(shape: Shape, color: Color) {
    Box(Modifier.size(18.dp).clip(shape).background(color))
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Lagoon.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Lagoon.Text else Lagoon.Muted.copy(alpha = 0.5f), fontSize = 20.sp)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Lagoon.Text, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = Lagoon.Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Lagoon.Deep,
                checkedTrackColor = accent,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Lagoon.Muted,
                uncheckedTrackColor = Lagoon.SurfaceHigh,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

fun isDefaultHome(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    @Suppress("DEPRECATION")
    val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return info?.activityInfo?.packageName == context.packageName
}

fun openHomeSettings(context: Context) {
    val candidates = listOf(
        Intent(Settings.ACTION_HOME_SETTINGS),
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
}
