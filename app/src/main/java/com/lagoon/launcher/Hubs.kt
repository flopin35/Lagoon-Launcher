package com.lagoon.launcher

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale

class LagoonNotification(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val time: Long,
    val clearable: Boolean,
    val autoCancel: Boolean,
    val intent: PendingIntent?,
    val icon: ImageBitmap?,
)

/** Live notifications, shared by the control centre, the dashboard and the lock screen. */
object NotificationHub {
    internal var service: LagoonNotificationService? = null

    var connected by mutableStateOf(false)
        internal set
    var items by mutableStateOf<List<LagoonNotification>>(emptyList())
        internal set
    var dnd by mutableStateOf(false)
        internal set

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun openAccessSettings(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun open(item: LagoonNotification) {
        runCatching { item.intent?.send() }
        if (item.autoCancel && item.clearable) dismiss(item)
    }

    fun dismiss(item: LagoonNotification) {
        runCatching { service?.cancelNotification(item.key) }
    }

    fun clearAll() {
        runCatching { service?.cancelAllNotifications() }
    }

    fun setDnd(on: Boolean) {
        runCatching {
            service?.requestInterruptionFilter(
                if (on) NotificationListenerService.INTERRUPTION_FILTER_PRIORITY else NotificationListenerService.INTERRUPTION_FILTER_ALL
            )
        }
    }
}

/** Granted through Settings › Notification access; it feeds [NotificationHub] and [MediaHub]. */
class LagoonNotificationService : NotificationListenerService() {
    private val labels = HashMap<String, String>()
    private val icons = HashMap<String, ImageBitmap?>()

    override fun onListenerConnected() {
        NotificationHub.service = this
        NotificationHub.connected = true
        NotificationHub.dnd = currentInterruptionFilter.let { it != INTERRUPTION_FILTER_ALL && it != INTERRUPTION_FILTER_UNKNOWN }
        refresh()
        MediaHub.attach(this)
    }

    override fun onListenerDisconnected() {
        NotificationHub.service = null
        NotificationHub.connected = false
        NotificationHub.items = emptyList()
        MediaHub.detach()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        NotificationHub.dnd = interruptionFilter != INTERRUPTION_FILTER_ALL
    }

    private fun refresh() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        NotificationHub.items = active
            .filter { it.packageName != packageName }
            .filter { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            // Music players get their own card, so skip their media notification.
            .filter { !it.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) }
            .sortedByDescending { it.postTime }
            .mapNotNull(::toItem)
    }

    private fun toItem(sbn: StatusBarNotification): LagoonNotification? {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        val pkg = sbn.packageName
        val label = labels.getOrPut(pkg) {
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        }
        val icon = icons.getOrPut(pkg) {
            runCatching { packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
        return LagoonNotification(
            key = sbn.key,
            packageName = pkg,
            appName = label,
            title = title,
            text = text,
            time = sbn.postTime,
            clearable = sbn.isClearable,
            autoCancel = (sbn.notification.flags and Notification.FLAG_AUTO_CANCEL) != 0,
            intent = sbn.notification.contentIntent,
            icon = icon,
        )
    }
}

/** Whatever is playing right now, read from the active media session. */
object MediaHub {
    var title by mutableStateOf<String?>(null)
        private set
    var artist by mutableStateOf<String?>(null)
        private set
    var art by mutableStateOf<ImageBitmap?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var appPackage by mutableStateOf<String?>(null)
        private set

    private var manager: MediaSessionManager? = null
    private var component: ComponentName? = null
    private var controller: MediaController? = null
    private var artSource: Bitmap? = null
    private val handler = Handler(Looper.getMainLooper())

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = update()
        override fun onPlaybackStateChanged(state: PlaybackState?) = update()
        override fun onSessionDestroyed() = choose(activeSessions())
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { choose(it) }

    private fun activeSessions(): List<MediaController>? {
        val m = manager ?: return null
        val c = component ?: return null
        return runCatching { m.getActiveSessions(c) }.getOrNull()
    }

    fun attach(context: Context) {
        val m = context.getSystemService(MediaSessionManager::class.java) ?: return
        val c = ComponentName(context, LagoonNotificationService::class.java)
        manager = m
        component = c
        runCatching { m.addOnActiveSessionsChangedListener(sessionsListener, c, handler) }
        choose(activeSessions())
    }

    fun detach() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(callback)
        controller = null
        manager = null
        update()
    }

    private fun choose(list: List<MediaController>?) {
        val next = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
        if (next?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = next
            next?.registerCallback(callback, handler)
        }
        update()
    }

    private fun update() {
        val c = controller
        val md = c?.metadata
        title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val bitmap = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        if (bitmap !== artSource) {
            artSource = bitmap
            art = bitmap?.let {
                runCatching { it.scale(256, (256 * it.height / it.width.coerceAtLeast(1)).coerceAtLeast(1)).asImageBitmap() }.getOrNull()
            }
        }
        playing = c?.playbackState?.state == PlaybackState.STATE_PLAYING
        appPackage = c?.packageName
    }

    fun playPause() {
        val controls = controller?.transportControls ?: return
        if (playing) controls.pause() else controls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    fun openApp(context: Context) {
        val pkg = appPackage ?: return
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
