package com.lagoon.launcher

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/** Thin wrappers over the system switches Lagoon is allowed to read or flip. */
object Controls {
    private fun start(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun wifiOn(context: Context): Boolean =
        runCatching { context.applicationContext.getSystemService(WifiManager::class.java)?.isWifiEnabled == true }.getOrDefault(false)

    // Android 10 no longer lets apps flip Wi-Fi directly, so open the system's quick panel instead.
    fun openWifi(context: Context) = start(
        context,
        if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS),
    )

    fun openInternet(context: Context) = start(
        context,
        if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_WIRELESS_SETTINGS),
    )

    @SuppressLint("MissingPermission")
    fun bluetoothOn(context: Context): Boolean =
        runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true }.getOrDefault(false)

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun toggleBluetooth(context: Context) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val done = Build.VERSION.SDK_INT < 33 && adapter != null &&
            runCatching { if (adapter.isEnabled) adapter.disable() else adapter.enable() }.getOrDefault(false)
        if (!done) start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    fun locationOn(context: Context): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
            else lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) || lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)
    }

    fun openLocation(context: Context) = start(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    fun openAirplane(context: Context) = start(context, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
    fun openSettings(context: Context) = start(context, Intent(Settings.ACTION_SETTINGS))

    fun openCamera(context: Context) {
        val secure = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val normal = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(secure) }.onFailure { runCatching { context.startActivity(normal) } }
    }

    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun requestWriteSettings(context: Context) = start(
        context,
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")),
    )

    fun autoRotate(context: Context): Boolean =
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    fun setAutoRotate(context: Context, on: Boolean): Boolean {
        if (!canWriteSettings(context)) {
            requestWriteSettings(context)
            return false
        }
        return runCatching { Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0) }
            .getOrDefault(false)
    }

    fun brightness(context: Context): Float =
        (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f).coerceIn(0f, 1f)

    fun setBrightness(context: Context, value: Float): Boolean {
        if (!canWriteSettings(context)) return false
        return runCatching {
            val resolver = context.contentResolver
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, (value * 255).roundToInt().coerceIn(2, 255))
        }.getOrDefault(false)
    }

    fun volume(context: Context): Float {
        val am = context.getSystemService(AudioManager::class.java) ?: return 0f
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
    }

    fun setVolume(context: Context, value: Float) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, (value * max).roundToInt(), 0) }
    }

    /** Used and total bytes on internal storage. */
    fun storage(): Pair<Long, Long> = runCatching {
        val stat = StatFs(Environment.getDataDirectory().path)
        (stat.totalBytes - stat.availableBytes) to stat.totalBytes
    }.getOrDefault(0L to 0L)
}

object Torch {
    var on by mutableStateOf(false)
        private set
    var available by mutableStateOf(false)
        private set
    private var cameraId: String? = null
    private var manager: CameraManager? = null

    fun init(context: Context) {
        if (manager != null) return
        val cm = context.applicationContext.getSystemService(CameraManager::class.java) ?: return
        manager = cm
        cameraId = runCatching {
            cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull()
        available = cameraId != null
        runCatching {
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(id: String, enabled: Boolean) {
                    if (id == cameraId) on = enabled
                }
            }, Handler(Looper.getMainLooper()))
        }
    }

    fun toggle() {
        val id = cameraId ?: return
        runCatching { manager?.setTorchMode(id, !on) }
    }
}

class BatteryInfo(val percent: Int, val charging: Boolean)

private fun readBattery(intent: Intent?): BatteryInfo {
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = (intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    return BatteryInfo(
        percent = if (level >= 0) level * 100 / scale else 0,
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
    )
}

/** A value that is re-read whenever one of the given system broadcasts arrives. */
@Composable
private fun <T> rememberBroadcast(actions: List<String>, read: (Context, Intent?) -> T): T {
    val context = LocalContext.current
    var value by remember { mutableStateOf(read(context, null)) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                value = read(context, intent)
            }
        }
        val filter = IntentFilter().apply { actions.forEach(::addAction) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return value
}

@Composable
fun rememberBattery(): BatteryInfo = rememberBroadcast(listOf(Intent.ACTION_BATTERY_CHANGED)) { context, intent ->
    readBattery(intent ?: ContextCompat.registerReceiver(context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED))
}

@Composable
fun rememberWifiOn(): Boolean = rememberBroadcast(listOf(WifiManager.WIFI_STATE_CHANGED_ACTION)) { c, _ -> Controls.wifiOn(c) }

@Composable
fun rememberBluetoothOn(): Boolean = rememberBroadcast(listOf(BluetoothAdapter.ACTION_STATE_CHANGED)) { c, _ -> Controls.bluetoothOn(c) }

@Composable
fun rememberLocationOn(): Boolean = rememberBroadcast(listOf(LocationManager.PROVIDERS_CHANGED_ACTION)) { c, _ -> Controls.locationOn(c) }

/** A round switch with a label, lit in the accent colour when on. */
@Composable
fun ControlToggle(
    icon: ImageVector,
    label: String,
    on: Boolean,
    accent: Color,
    onClick: () -> Unit,
    size: Dp = 56.dp,
    showLabel: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "toggle")
    val background by animateColorAsState(if (on) accent else Color(0x24FFFFFF), label = "toggleBg")
    val tint by animateColorAsState(if (on) Lagoon.Deep else Lagoon.Text, label = "toggleFg")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.width(size + 22.dp),
    ) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(background)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(size * 0.42f))
        }
        if (showLabel) {
            Text(label, color = Lagoon.Text.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

class ToggleSpec(val icon: ImageVector, val label: String, val on: Boolean, val onClick: () -> Unit)

/** Every quick switch Lagoon offers, in the order the control centre shows them. */
@Composable
fun quickToggles(resumeTicks: Int): List<ToggleSpec> {
    val context = LocalContext.current
    val wifi = rememberWifiOn()
    val bluetooth = rememberBluetoothOn()
    val location = rememberLocationOn()
    var rotate by remember(resumeTicks) { mutableStateOf(Controls.autoRotate(context)) }
    return listOf(
        ToggleSpec(Icons.Rounded.Wifi, "Wi-Fi", wifi) { Controls.openWifi(context) },
        ToggleSpec(Icons.Rounded.Bluetooth, "Bluetooth", bluetooth) { Controls.toggleBluetooth(context) },
        ToggleSpec(Icons.Rounded.SignalCellularAlt, "Mobile data", false) { Controls.openInternet(context) },
        ToggleSpec(Icons.Rounded.FlashlightOn, "Torch", Torch.on) { Torch.toggle() },
        ToggleSpec(Icons.Rounded.DoNotDisturbOn, "Do not disturb", NotificationHub.dnd) {
            if (NotificationHub.connected) NotificationHub.setDnd(!NotificationHub.dnd) else NotificationHub.openAccessSettings(context)
        },
        ToggleSpec(Icons.Rounded.ScreenRotation, "Auto-rotate", rotate) {
            if (Controls.setAutoRotate(context, !rotate)) rotate = !rotate
        },
        ToggleSpec(Icons.Rounded.LocationOn, "Location", location) { Controls.openLocation(context) },
        ToggleSpec(Icons.Rounded.AirplanemodeActive, "Airplane", false) { Controls.openAirplane(context) },
        ToggleSpec(Icons.Rounded.Settings, "Settings", false) { Controls.openSettings(context) },
    )
}

@Composable
fun ToggleGrid(toggles: List<ToggleSpec>, columns: Int, accent: Color, size: Dp = 56.dp, showLabels: Boolean = true) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Shrink the buttons rather than let the last column fall off a narrow screen.
        val fitted = minOf(size, maxWidth / columns - 22.dp)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            toggles.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { ControlToggle(it.icon, it.label, it.on, accent, it.onClick, fitted, showLabels) }
                    repeat(columns - row.size) { Spacer(Modifier.width(fitted + 22.dp)) }
                }
            }
        }
    }
}

@Composable
private fun LevelSlider(icon: ImageVector, description: String, value: Float, accent: Color, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, description, tint = Lagoon.Text, modifier = Modifier.size(22.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = accent,
                inactiveTrackColor = Color(0x2EFFFFFF),
            ),
        )
    }
}

@Composable
fun BrightnessSlider(accent: Color, resumeTicks: Int) {
    val context = LocalContext.current
    val canWrite = remember(resumeTicks) { Controls.canWriteSettings(context) }
    var level by remember(resumeTicks) { mutableFloatStateOf(Controls.brightness(context)) }
    if (canWrite) {
        LevelSlider(Icons.Rounded.LightMode, "Brightness", level, accent) {
            level = it
            Controls.setBrightness(context, it)
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Color(0x1AFFFFFF))
                .clickable { Controls.requestWriteSettings(context) }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Rounded.LightMode, null, tint = Lagoon.Text, modifier = Modifier.size(22.dp))
            Text("Allow Lagoon to change brightness and rotation", color = Lagoon.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("Allow", color = accent, fontSize = 14.sp)
        }
    }
}

@Composable
fun VolumeSlider(accent: Color, resumeTicks: Int) {
    val context = LocalContext.current
    var level by remember(resumeTicks) { mutableFloatStateOf(Controls.volume(context)) }
    LevelSlider(Icons.AutoMirrored.Rounded.VolumeUp, "Media volume", level, accent) {
        level = it
        Controls.setVolume(context, it)
    }
}
