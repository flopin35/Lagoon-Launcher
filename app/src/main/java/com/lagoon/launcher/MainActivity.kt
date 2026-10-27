package com.lagoon.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    private lateinit var repo: AppRepository
    private lateinit var prefs: LauncherPrefs

    /** Bumped when the Home button is pressed while Lagoon is already in front. */
    private val homeEvents = mutableIntStateOf(0)
    /** Bumped on every resume so the clock and the default-launcher check stay fresh after sleep. */
    private val resumeTicks = mutableIntStateOf(0)
    /** True while Lagoon is on screen; the clock's second hand only animates then. */
    private val visible = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(this)
        repo = AppRepository(applicationContext).also { it.start() }
        setContent { LagoonApp(repo, prefs, homeEvents.intValue, resumeTicks.intValue, visible.value) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) homeEvents.intValue++
    }

    override fun onResume() {
        super.onResume()
        resumeTicks.intValue++
        visible.value = true
    }

    override fun onPause() {
        visible.value = false
        super.onPause()
    }

    override fun onDestroy() {
        repo.stop()
        super.onDestroy()
    }
}
