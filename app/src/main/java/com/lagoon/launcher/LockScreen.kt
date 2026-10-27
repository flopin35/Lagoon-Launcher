package com.lagoon.launcher

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps Lagoon awake in the background so it can put the Lagoon lock screen up each
 * time the screen turns off. Android requires a visible notification while it runs.
 */
class LockService : Service() {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) showLock()
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        ContextCompat.registerReceiver(this, receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showLock() {
        runCatching {
            startActivity(
                Intent(this, LockActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            )
        }
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Lagoon lock screen", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shown while the Lagoon lock screen is turned on"
                setShowBadge(false)
            }
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lagoon)
            .setContentTitle("Lagoon lock screen is on")
            .setContentText("Turn it off in Lagoon settings.")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL = "lagoon_lock"
        private const val NOTIFICATION_ID = 7

        fun sync(context: Context, enabled: Boolean) {
            val intent = Intent(context, LockService::class.java)
            if (enabled) runCatching { ContextCompat.startForegroundService(context, intent) }
            else context.stopService(intent)
        }
    }
}

class LockActivity : ComponentActivity() {
    private val visible = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(AndroidColor.TRANSPARENT), SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        // The lock screen only goes away by swiping up.
        onBackPressedDispatcher.addCallback(this) { }
        Torch.init(this)
        val prefs = LauncherPrefs(this)
        setContent { LagoonLockScreen(prefs, visible.value, onUnlock = ::unlock) }
    }

    override fun onResume() {
        super.onResume()
        visible.value = true
    }

    override fun onPause() {
        visible.value = false
        super.onPause()
    }

    private fun unlock() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard != null && keyguard.isKeyguardLocked) {
            // Hands over to the system PIN, pattern or fingerprint screen.
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = close()
            })
        } else {
            close()
        }
    }

    private fun close() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }
}

@Composable
fun LagoonLockScreen(prefs: LauncherPrefs, active: Boolean, onUnlock: () -> Unit) {
    val accent = accentColor(prefs.accent)
    MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = Lagoon.Deep, surface = Lagoon.Surface)) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var lift by remember { mutableFloatStateOf(0f) }

        BoxWithConstraints(Modifier.fillMaxSize().background(Lagoon.Deep)) {
            val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val progress = (lift / (heightPx * 0.5f)).coerceIn(0f, 1f)

            val wallpaper = if (prefs.wallpaper == "system") "lagoon" else prefs.wallpaper
            WallpaperLayer(
                wallpaper,
                prefs.wallpaperVersion,
                Modifier.graphicsLayer {
                    val s = 1f + 0.06f * progress
                    scaleX = s
                    scaleY = s
                },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0x40000000), Color(0x10000000), Color(0x99000000))))
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = -lift
                        alpha = 1f - progress
                    }
                    .pointerInput(heightPx) {
                        val tracker = VelocityTracker()
                        detectVerticalDragGestures(
                            onDragStart = { tracker.resetTracking() },
                            onVerticalDrag = { change, dy ->
                                tracker.addPosition(change.uptimeMillis, change.position)
                                lift = (lift - dy).coerceAtLeast(0f)
                                change.consume()
                            },
                            onDragEnd = {
                                val velocity = tracker.calculateVelocity().y
                                scope.launch {
                                    if (lift > heightPx * 0.22f || velocity < -1400f) {
                                        animate(lift, heightPx * 0.6f, animationSpec = tween(220)) { v, _ -> lift = v }
                                        onUnlock()
                                        // If the PIN screen is cancelled, come back down.
                                        delay(700)
                                        animate(lift, 0f, animationSpec = spring(dampingRatio = 0.8f)) { v, _ -> lift = v }
                                    } else {
                                        animate(lift, 0f, animationSpec = spring(dampingRatio = 0.7f)) { v, _ -> lift = v }
                                    }
                                }
                            },
                        )
                    }
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Lock, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                    Text("Lagoon", color = Color.White.copy(alpha = 0.8f), fontFamily = Outfit, fontSize = 14.sp, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(28.dp))
                HomeClock(prefs, Color.White, accent, centered = true, active = active, shadow = true, stackedOverride = true)

                WeatherHub.report?.takeIf { prefs.weatherLat.isNotBlank() }?.let { report ->
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WeatherGlyph(report.code, report.isDay, 30.dp, accent, animate = active)
                        Text(
                            "${formatTemp(report.temp)}  ${skyLabel(report.code, report.isDay)}",
                            color = Color.White,
                            fontFamily = Outfit,
                            fontSize = 17.sp,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Column(
                    Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (MediaHub.title != null) {
                        Box(Modifier.clip(RoundedCornerShape(24.dp)).background(TileGlass).padding(16.dp)) {
                            MediaCard(accent, compact = true)
                        }
                    }
                    // App and title only: message text stays private until the tablet is unlocked.
                    NotificationHub.items.take(3).forEach { NotificationCard(it, prefs, showText = false) }
                    val more = NotificationHub.items.size - 3
                    if (more > 0) {
                        Text("+$more more", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }

                Spacer(Modifier.height(20.dp))
                UnlockHint(accent, active)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    LockShortcut(Icons.Rounded.FlashlightOn, "Torch", Torch.on, accent) { Torch.toggle() }
                    LockShortcut(Icons.Rounded.PhotoCamera, "Camera", false, accent) { Controls.openCamera(context) }
                }
            }
        }
    }
}

@Composable
private fun UnlockHint(accent: Color, active: Boolean) {
    val bob = if (active) {
        val transition = rememberInfiniteTransition(label = "hint")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "bob").value
    } else 0f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Rounded.KeyboardArrowUp,
            null,
            tint = accent,
            modifier = Modifier.size(30.dp).graphicsLayer { translationY = -bob * 10.dp.toPx() },
        )
        Text(
            "Swipe up to unlock",
            color = Color.White.copy(alpha = 0.6f + 0.4f * bob),
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun LockShortcut(icon: ImageVector, description: String, on: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (on) accent else Color(0x33FFFFFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (on) Lagoon.Deep else Color.White, modifier = Modifier.size(24.dp))
    }
}
