package com.lagoon.launcher

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Every setting is backed by SharedPreferences and mirrored in Compose state,
 * so changing one both saves it and redraws the screens that read it.
 */
class LauncherPrefs(context: Context) {
    private val sp = context.getSharedPreferences("lagoon_prefs", Context.MODE_PRIVATE)

    var wallpaper by string("wallpaper", "lagoon")
    var wallpaperVersion by int("wallpaper_version", 0)
    var accent by string("accent", "turquoise")
    var iconShape by string("icon_shape", "squircle")
    var iconStyle by string("icon_style", "colour")
    var iconSize by int("icon_size", 58)
    var showLabels by bool("show_labels", true)
    var columns by int("columns", 6)
    var showClock by bool("show_clock", true)
    var use24h by bool("use_24h", true)
    var motion by string("motion", "gentle")
    var seeded by bool("seeded", false)
    var dock by list("dock")
    var home by list("home")
    var hidden by list("hidden")

    private fun string(key: String, default: String) =
        pref({ sp.getString(key, default) ?: default }) { sp.edit().putString(key, it).apply() }

    private fun int(key: String, default: Int) =
        pref({ sp.getInt(key, default) }) { sp.edit().putInt(key, it).apply() }

    private fun bool(key: String, default: Boolean) =
        pref({ sp.getBoolean(key, default) }) { sp.edit().putBoolean(key, it).apply() }

    private fun list(key: String) =
        pref({ sp.getString(key, "").orEmpty().split('\n').filter { it.isNotBlank() } }) {
            sp.edit().putString(key, it.joinToString("\n")).apply()
        }

    private fun <T> pref(read: () -> T, write: (T) -> Unit) = object : ReadWriteProperty<Any?, T> {
        private val state = mutableStateOf(read())
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            write(value)
        }
    }
}

fun List<String>.toggled(key: String, limit: Int = Int.MAX_VALUE): List<String> = when {
    key in this -> this - key
    size < limit -> this + key
    else -> this
}
