package com.lagoon.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun LagoonApp(repo: AppRepository, prefs: LauncherPrefs, homeEvents: Int, resumeTicks: Int, appVisible: Boolean) {
    val accent = accentColor(prefs.accent)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = Lagoon.Deep,
            secondary = accent,
            background = Lagoon.Deep,
            onBackground = Lagoon.Text,
            surface = Lagoon.Surface,
            onSurface = Lagoon.Text,
        )
    ) {
        LauncherRoot(repo, prefs, accent, homeEvents, resumeTicks, appVisible)
    }
}

@Composable
private fun LauncherRoot(
    repo: AppRepository,
    prefs: LauncherPrefs,
    accent: Color,
    homeEvents: Int,
    resumeTicks: Int,
    appVisible: Boolean,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val activity = context as? Activity

    // 0 = home screen, 1 = app drawer fully open
    var progress by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var query by remember { mutableStateOf("") }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    val drawerOpen by remember { derivedStateOf { progress > 0.5f } }

    val hidden = remember(prefs.hidden) { prefs.hidden.toSet() }
    val renames = remember(prefs.renames) { prefs.renameMap() }
    val allApps = remember(repo.apps, renames) {
        if (renames.isEmpty()) repo.apps
        else repo.apps
            .map { app -> renames[app.key]?.let { AppEntry(app.key, it, app.component, app.user, app.icon) } ?: app }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
    val visibleApps = remember(allApps, hidden) { allApps.filter { it.key !in hidden } }
    val byKey = remember(allApps) { allApps.associateBy { it.key } }
    val dockApps = prefs.dock.mapNotNull { byKey[it] }.filter { it.key !in hidden }
    val homeApps = prefs.home.mapNotNull { byKey[it] }.filter { it.key !in hidden }

    LaunchedEffect(repo.loaded) {
        if (repo.loaded && !prefs.seeded) {
            prefs.dock = seedDock(repo.apps)
            prefs.seeded = true
        }
    }

    LaunchedEffect(prefs.wallpaper) {
        val window = activity?.window ?: return@LaunchedEffect
        if (prefs.wallpaper == "system") {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(0xFF03222A.toInt()))
        }
    }

    val lightWallpaper = presetById(prefs.wallpaper)?.light == true
    val lightBars = lightWallpaper && !drawerOpen && !settingsOpen && menuApp == null
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        fun animateDrawer(target: Float, velocity: Float = 0f) {
            settleJob?.cancel()
            settleJob = scope.launch {
                when (prefs.motion) {
                    "off" -> progress = target
                    "lively" -> animate(progress, target, velocity, spring(dampingRatio = 0.82f, stiffness = 420f)) { v, _ -> progress = v }
                    else -> animate(progress, target, velocity, spring(dampingRatio = 1f, stiffness = 260f)) { v, _ -> progress = v }
                }
                if (target == 0f) {
                    query = ""
                    focusManager.clearFocus()
                    keyboard?.hide()
                }
            }
        }

        // velocityY is in px/s, positive when the finger moves down
        fun settle(velocityY: Float) {
            val target = when {
                velocityY < -800f -> 1f
                velocityY > 800f -> 0f
                progress > 0.4f -> 1f
                else -> 0f
            }
            animateDrawer(target, -velocityY / heightPx)
        }

        fun openApp(app: AppEntry, bounds: Rect?) {
            val rect = bounds?.let {
                android.graphics.Rect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt())
            }
            val options = rect?.let {
                ActivityOptions.makeScaleUpAnimation(view, it.left, it.top, it.width(), it.height()).toBundle()
            }
            repo.launch(app, rect, options)
            if (progress > 0f) scope.launch {
                delay(450)
                settleJob?.cancel()
                progress = 0f
                query = ""
                focusManager.clearFocus()
            }
        }

        LaunchedEffect(homeEvents) {
            if (homeEvents > 0) {
                menuApp = null
                if (settingsOpen) settingsOpen = false else animateDrawer(0f)
            }
        }

        BackHandler {
            when {
                menuApp != null -> menuApp = null
                settingsOpen -> settingsOpen = false
                progress > 0f -> animateDrawer(0f)
            }
        }

        // Drags that no child consumed: swipe up for apps, down for notifications, and
        // dragging the drawer's header to close it. Positions are read here at the root,
        // which never moves, so the drawer tracks the finger exactly.
        val dragModifier = Modifier.pointerInput(heightPx) {
            val tracker = VelocityTracker()
            var total = 0f
            detectVerticalDragGestures(
                onDragStart = {
                    tracker.resetTracking()
                    total = 0f
                    settleJob?.cancel()
                },
                onDragEnd = {
                    if (settingsOpen || menuApp != null) return@detectVerticalDragGestures
                    val velocity = tracker.calculateVelocity().y
                    if (progress == 0f && total > 48.dp.toPx()) expandNotifications(context) else settle(velocity)
                },
                onDragCancel = { if (!settingsOpen && menuApp == null) settle(0f) },
                onVerticalDrag = { change, dy ->
                    if (settingsOpen || menuApp != null) return@detectVerticalDragGestures
                    tracker.addPosition(change.uptimeMillis, change.position)
                    total += dy
                    if (progress > 0f || dy < 0f) progress = (progress - dy / heightPx).coerceIn(0f, 1f)
                    change.consume()
                },
            )
        }

        // Pulling down on the app list once it is scrolled to the top closes the drawer.
        val closeThreshold = with(density) { 56.dp.toPx() }
        val drawerScroll = remember(heightPx) {
            object : NestedScrollConnection {
                var pull = 0f

                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y < 0f) pull = 0f
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.UserInput && available.y > 0f) pull += available.y
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    val close = pull > closeThreshold || (pull > 0f && available.y > 1200f)
                    pull = 0f
                    if (close) {
                        animateDrawer(0f, -available.y / heightPx)
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        Box(Modifier.fillMaxSize().then(dragModifier)) {
            WallpaperLayer(
                prefs.wallpaper,
                prefs.wallpaperVersion,
                Modifier.graphicsLayer {
                    val s = 1f + 0.05f * progress.coerceIn(0f, 1f)
                    scaleX = s
                    scaleY = s
                },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.coerceIn(0f, 1f) }
                    .background(Color(0x66000000))
            )

            HomeScreen(
                prefs = prefs,
                accent = accent,
                homeApps = homeApps,
                dockApps = dockApps,
                light = lightWallpaper,
                clockActive = appVisible && !drawerOpen && !settingsOpen,
                onLaunch = ::openApp,
                onMenu = { menuApp = it },
                onOpenSettings = { settingsOpen = true },
                modifier = Modifier.graphicsLayer {
                    val p = progress.coerceIn(0f, 1f)
                    alpha = (1f - p * 1.6f).coerceIn(0f, 1f)
                    translationY = -p * 48.dp.toPx()
                    val s = 1f - 0.04f * p
                    scaleX = s
                    scaleY = s
                },
            )

            AppDrawer(
                apps = visibleApps,
                query = query,
                onQueryChange = { query = it },
                prefs = prefs,
                accent = accent,
                progress = { progress },
                nestedScroll = drawerScroll,
                onLaunch = ::openApp,
                onMenu = { menuApp = it },
                onOpenSettings = { settingsOpen = true },
                modifier = Modifier.graphicsLayer {
                    translationY = (1f - progress).coerceAtLeast(0f) * heightPx
                },
            )

            AppMenu(
                app = menuApp,
                prefs = prefs,
                accent = accent,
                onDismiss = { menuApp = null },
                onInfo = repo::openInfo,
                onUninstall = repo::uninstall,
            )

            AnimatedVisibility(
                visible = settingsOpen,
                enter = slideInHorizontally(motionSpec(prefs.motion)) { it / 3 } + fadeIn(motionSpec(prefs.motion)),
                exit = slideOutHorizontally(motionSpec(prefs.motion)) { it / 3 } + fadeOut(motionSpec(prefs.motion)),
            ) {
                SettingsScreen(prefs, allApps, accent, resumeTicks, onClose = { settingsOpen = false })
            }
        }
    }
}

/** The chosen column count, capped so icons never touch on narrow screens. */
@Composable
private fun fittedColumns(prefs: LauncherPrefs, sidePadding: Int): Int {
    val width = LocalConfiguration.current.screenWidthDp - sidePadding * 2
    val fit = width / (prefs.iconSize + 30)
    return prefs.columns.coerceAtMost(fit).coerceAtLeast(3)
}

@Composable
private fun HomeScreen(
    prefs: LauncherPrefs,
    accent: Color,
    homeApps: List<AppEntry>,
    dockApps: List<AppEntry>,
    light: Boolean,
    clockActive: Boolean,
    onLaunch: (AppEntry, Rect?) -> Unit,
    onMenu: (AppEntry) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = if (light) Lagoon.Ink else Color.White
    val openSettings by rememberUpdatedState(onOpenSettings)
    Box(modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onLongPress = { openSettings() }) }) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 36.dp, vertical = 20.dp)
        ) {
            if (prefs.showClock) {
                val centered = prefs.clockAlign == "center"
                Box(Modifier.fillMaxWidth(), contentAlignment = if (centered) Alignment.TopCenter else Alignment.TopStart) {
                    HomeClock(prefs, content, accent, centered, clockActive, shadow = !light)
                }
            }
            Spacer(Modifier.weight(1f))
            if (homeApps.isNotEmpty()) {
                val columns = fittedColumns(prefs, sidePadding = 36)
                val cellWidth = ((LocalConfiguration.current.screenWidthDp - 72) / columns).coerceAtMost(prefs.iconSize + 40).dp
                Column {
                    homeApps.chunked(columns).forEach { row ->
                        Row {
                            row.forEach { app ->
                                AppIcon(
                                    app = app,
                                    prefs = prefs,
                                    accent = accent,
                                    showLabel = prefs.showLabels,
                                    labelColor = content,
                                    elevated = true,
                                    onClick = { onLaunch(app, it) },
                                    onLongClick = { onMenu(app) },
                                    modifier = Modifier.width(cellWidth),
                                )
                            }
                        }
                    }
                }
            } else if (dockApps.isNotEmpty()) {
                Text(
                    "Swipe up for all apps. Long-press one to pin it here.",
                    color = content.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    style = TextStyle(shadow = if (light) null else Shadow(Color(0x40000000), Offset(0f, 2f), 10f)),
                )
            }
            Spacer(Modifier.height(20.dp))
            if (prefs.showDock && dockApps.isNotEmpty()) {
                Dock(dockApps, prefs, accent, light, onLaunch, onMenu, Modifier.align(Alignment.CenterHorizontally))
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 44.dp, height = 4.dp)
                    .background(content.copy(alpha = 0.45f), CircleShape)
            )
        }
    }
}

@Composable
private fun Dock(
    apps: List<AppEntry>,
    prefs: LauncherPrefs,
    accent: Color,
    light: Boolean,
    onLaunch: (AppEntry, Rect?) -> Unit,
    onMenu: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(30.dp)
    // Shrink dock icons when they would not fit across the screen (home padding 72dp, dock padding 32dp, 18dp gaps).
    val available = LocalConfiguration.current.screenWidthDp - 72 - 32 - 18 * (apps.size - 1)
    val iconSize = minOf(prefs.iconSize, available / apps.size.coerceAtLeast(1)).dp
    Row(
        modifier
            .background(if (light) Color(0x66FFFFFF) else Color(0x2EFFFFFF), shape)
            .border(1.dp, if (light) Color(0x40FFFFFF) else Color(0x24FFFFFF), shape)
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        apps.forEach { app ->
            AppIcon(
                app = app,
                prefs = prefs,
                accent = accent,
                showLabel = false,
                labelColor = Color.White,
                elevated = true,
                onClick = { onLaunch(app, it) },
                onLongClick = { onMenu(app) },
                iconSize = iconSize,
            )
        }
    }
}

private class CoordsHolder {
    var coords: LayoutCoordinates? = null
}

private val grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppEntry,
    prefs: LauncherPrefs,
    accent: Color,
    showLabel: Boolean,
    labelColor: Color,
    elevated: Boolean,
    onClick: (Rect?) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = prefs.iconSize.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = if (prefs.motion == "off") snap() else spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "press",
    )
    val haptics = LocalHapticFeedback.current
    val holder = remember { CoordsHolder() }
    val shape = iconShape(prefs.iconShape)
    val mono = prefs.iconStyle == "mono"
    val tint = remember(accent) { lerp(accent, Color.White, 0.2f) }

    Column(
        modifier
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onClick(holder.coords?.takeIf { it.isAttached }?.boundsInWindow()) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(iconSize)
                .onGloballyPositioned { holder.coords = it }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.shape = shape
                    clip = true
                    shadowElevation = if (elevated) 6.dp.toPx() else 0f
                }
        ) {
            Image(
                app.icon,
                contentDescription = app.label,
                modifier = Modifier.fillMaxSize(),
                colorFilter = if (mono) grayscale else null,
                filterQuality = FilterQuality.Medium,
            )
            if (mono) Box(Modifier.fillMaxSize().drawBehind { drawRect(tint, blendMode = BlendMode.Multiply) })
        }
        if (showLabel) {
            Text(
                app.label,
                color = labelColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    shadow = if (elevated && labelColor == Color.White) Shadow(Color(0x66000000), Offset(0f, 1f), 6f) else null
                ),
                modifier = Modifier.padding(top = 6.dp).widthIn(max = iconSize + 30.dp),
            )
        }
    }
}

@Composable
private fun AppDrawer(
    apps: List<AppEntry>,
    query: String,
    onQueryChange: (String) -> Unit,
    prefs: LauncherPrefs,
    accent: Color,
    progress: () -> Float,
    nestedScroll: NestedScrollConnection,
    onLaunch: (AppEntry, Rect?) -> Unit,
    onMenu: (AppEntry) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trimmed = query.trim()
    val filtered = remember(apps, trimmed) {
        if (trimmed.isEmpty()) apps
        else apps.filter { it.label.contains(trimmed, ignoreCase = true) }
            .sortedBy { !it.label.startsWith(trimmed, ignoreCase = true) }
    }
    val columns = fittedColumns(prefs, sidePadding = 16)
    val motionOff = prefs.motion == "off"
    val gridState = rememberLazyGridState()
    LaunchedEffect(trimmed) { gridState.scrollToItem(0) }

    Column(
        modifier
            .fillMaxSize()
            .background(Lagoon.Panel, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            // Swallow taps on empty panel space so they never reach the home screen underneath.
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding()
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 40.dp, height = 4.dp).background(Lagoon.Muted.copy(alpha = 0.5f), CircleShape))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SearchField(
                query = query,
                onChange = onQueryChange,
                accent = accent,
                onGo = { filtered.firstOrNull()?.let { onLaunch(it, null) } },
                modifier = Modifier.weight(1f),
            )
            RoundIconButton(Icons.Rounded.Settings, "Launcher settings", onOpenSettings)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(nestedScroll),
        ) {
            itemsIndexed(filtered, key = { _, app -> app.key }) { index, app ->
                val row = (index / columns).coerceAtMost(8)
                AppIcon(
                    app = app,
                    prefs = prefs,
                    accent = accent,
                    showLabel = prefs.showLabels,
                    labelColor = Lagoon.Text,
                    elevated = false,
                    onClick = { onLaunch(app, it) },
                    onLongClick = { onMenu(app) },
                    // Rows rise into place one after another as the drawer opens.
                    modifier = Modifier.graphicsLayer {
                        val p = progress()
                        val t = if (motionOff) (if (p > 0f) 1f else 0f) else ((p - 0.25f - row * 0.045f) / 0.4f).coerceIn(0f, 1f)
                        alpha = t
                        translationY = (1f - t) * 28.dp.toPx()
                    },
                )
            }
            if (filtered.isEmpty() && trimmed.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "No apps match “$trimmed”",
                        color = Lagoon.Muted,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 48.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onChange: (String) -> Unit,
    accent: Color,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = Lagoon.Text, fontSize = 16.sp),
        cursorBrush = SolidColor(accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = modifier.height(50.dp).background(Lagoon.Surface, RoundedCornerShape(25.dp)),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.Search, null, tint = Lagoon.Muted, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search apps", color = Lagoon.Muted, fontSize = 16.sp)
                    inner()
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        tint = Lagoon.Muted,
                        modifier = Modifier.size(28.dp).clip(CircleShape).clickable { onChange("") }.padding(4.dp),
                    )
                }
            }
        },
    )
}

@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(50.dp).clip(CircleShape).background(Lagoon.Surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Lagoon.Text, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun BoxScope.AppMenu(
    app: AppEntry?,
    prefs: LauncherPrefs,
    accent: Color,
    onDismiss: () -> Unit,
    onInfo: (AppEntry) -> Unit,
    onUninstall: (AppEntry) -> Unit,
) {
    // Keep showing the last app while the card animates away.
    var lastApp by remember { mutableStateOf<AppEntry?>(null) }
    SideEffect { if (app != null) lastApp = app }
    val current = app ?: lastApp

    AnimatedVisibility(visible = app != null, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x8C000000))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        )
    }
    AnimatedVisibility(
        visible = app != null,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(motionSpec(prefs.motion)) { it / 2 } + fadeIn(),
        exit = slideOutVertically(motionSpec(prefs.motion)) { it / 2 } + fadeOut(),
    ) {
        current?.let { MenuCard(it, prefs, accent, onDismiss, onInfo, onUninstall) }
    }
}

@Composable
private fun MenuCard(
    app: AppEntry,
    prefs: LauncherPrefs,
    accent: Color,
    onDismiss: () -> Unit,
    onInfo: (AppEntry) -> Unit,
    onUninstall: (AppEntry) -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val inHome = app.key in prefs.home
    val inDock = app.key in prefs.dock
    val homeLimit = prefs.columns * 3
    var renaming by remember(app.key) { mutableStateOf(false) }
    var draft by remember(app.key) { mutableStateOf(app.label) }
    Column(
        Modifier
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding()
            .padding(16.dp)
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .background(Color(0xFF072830), shape)
            .border(1.dp, Lagoon.Line, shape)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Image(app.icon, null, Modifier.size(56.dp).clip(iconShape(prefs.iconShape)))
            Column(Modifier.weight(1f)) {
                Text(
                    app.label,
                    color = Lagoon.Text,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(app.component.packageName, color = Lagoon.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (renaming) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it.take(40) },
                singleLine = true,
                textStyle = TextStyle(color = Lagoon.Text, fontSize = 17.sp),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    prefs.rename(app.key, draft)
                    onDismiss()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(Lagoon.Surface, RoundedCornerShape(16.dp))
                    .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp),
                decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { inner() } },
            )
            Spacer(Modifier.height(8.dp))
            MenuRow(Icons.Rounded.Check, "Save name", accent) {
                prefs.rename(app.key, draft)
                onDismiss()
            }
            MenuRow(Icons.Rounded.Refresh, "Use the app's own name", accent) {
                prefs.rename(app.key, null)
                onDismiss()
            }
            return@Column
        }
        MenuRow(Icons.Rounded.Create, "Rename", accent) { renaming = true }
        MenuRow(
            Icons.Rounded.Home,
            when {
                inHome -> "Remove from home screen"
                prefs.home.size >= homeLimit -> "Home screen is full"
                else -> "Add to home screen"
            },
            accent,
            enabled = inHome || prefs.home.size < homeLimit,
        ) {
            prefs.home = prefs.home.toggled(app.key, homeLimit)
            onDismiss()
        }
        MenuRow(
            Icons.Rounded.Star,
            when {
                inDock -> "Remove from dock"
                prefs.dock.size >= 6 -> "Dock is full (6 apps)"
                else -> "Add to dock"
            },
            accent,
            enabled = inDock || prefs.dock.size < 6,
        ) {
            prefs.dock = prefs.dock.toggled(app.key, 6)
            onDismiss()
        }
        MenuRow(Icons.Rounded.Lock, "Hide from app list", accent) {
            prefs.hidden = prefs.hidden + app.key
            onDismiss()
        }
        MenuRow(Icons.Rounded.Info, "App info", accent) {
            onInfo(app)
            onDismiss()
        }
        MenuRow(Icons.Rounded.Delete, "Uninstall", Color(0xFFFF8A73)) {
            onUninstall(app)
            onDismiss()
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, text: String, tint: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = if (enabled) tint else Lagoon.Muted, modifier = Modifier.size(22.dp))
        Text(text, color = if (enabled) Lagoon.Text else Lagoon.Muted, fontSize = 16.sp)
    }
}

/** Opens the notification shade. StatusBarManager has no public method for this, so it is called by name. */
fun expandNotifications(context: Context) {
    runCatching {
        val service = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
    }
}
