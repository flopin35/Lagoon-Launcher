package com.lagoon.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class AppEntry(
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
)

/** Loads the installed apps from the system and reloads whenever one is installed, updated or removed. */
class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()
    private val iconPx = (context.resources.displayMetrics.density * 72).toInt().coerceIn(96, 192)
    private var loadJob: Job? = null

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh(packageName)
        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) =
            refresh(*packageNames)
        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) =
            refresh(*packageNames)
    }

    fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        load()
    }

    fun stop() {
        launcherApps.unregisterCallback(callback)
        scope.cancel()
    }

    private fun refresh(vararg packages: String) {
        iconCache.keys.removeAll { key -> packages.any { key.startsWith("$it/") } }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = scope.launch {
            apps = withContext(Dispatchers.IO) { query() }
            loaded = true
        }
    }

    private fun query(): List<AppEntry> {
        val self = context.packageName
        return launcherApps.profiles
            .flatMap { user -> launcherApps.getActivityList(null, user) }
            .filter { it.componentName.packageName != self }
            .map { info ->
                val key = info.componentName.flattenToShortString() + "#" + info.user.hashCode()
                AppEntry(
                    key = key,
                    label = info.label.toString(),
                    component = info.componentName,
                    user = info.user,
                    icon = iconCache.getOrPut(key) { renderIcon(info) },
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /**
     * Draws the icon full-bleed into a square so the UI can cut it to any shape.
     * Adaptive icon layers are 108dp with a 72dp visible area, so each layer
     * overflows the square by a quarter on every side.
     */
    private fun renderIcon(info: LauncherActivityInfo): ImageBitmap {
        val size = iconPx
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val drawable = info.getBadgedIcon(0)
        if (drawable is AdaptiveIconDrawable) {
            val bleed = size / 4
            listOfNotNull(drawable.background, drawable.foreground).forEach { layer ->
                layer.setBounds(-bleed, -bleed, size + bleed, size + bleed)
                layer.draw(canvas)
            }
        } else {
            canvas.drawColor(0xFFF2FBF9.toInt())
            val pad = size / 6
            drawable.setBounds(pad, pad, size - pad, size - pad)
            drawable.draw(canvas)
        }
        return bitmap.asImageBitmap()
    }

    fun launch(app: AppEntry, sourceBounds: Rect?, options: Bundle?) {
        try {
            launcherApps.startMainActivity(app.component, app.user, sourceBounds, options)
        } catch (e: Exception) {
            Toast.makeText(context, "${app.label} couldn't be opened", Toast.LENGTH_SHORT).show()
        }
    }

    fun openInfo(app: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstall(app: AppEntry) {
        runCatching {
            val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.component.packageName, null))
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

private val preferredDock = listOf(
    "com.huawei.browser", "com.android.chrome", "com.huawei.camera", "com.android.camera2",
    "com.huawei.photos", "com.google.android.apps.photos", "com.huawei.himovie",
    "com.google.android.youtube", "com.huawei.appmarket", "com.android.settings",
)

/** Picks a sensible first-run dock: the browser, camera, gallery and so on, then fills any gaps. */
fun seedDock(apps: List<AppEntry>): List<String> {
    val picked = preferredDock
        .mapNotNull { pkg -> apps.firstOrNull { it.component.packageName == pkg } }
        .take(5)
    val fill = apps.filter { it !in picked }.take(5 - picked.size)
    return (picked + fill).map { it.key }
}
