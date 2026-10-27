package com.lagoon.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.sin

class WeatherDay(val date: LocalDate, val code: Int, val high: Double, val low: Double)

class WeatherReport(
    val temp: Double,
    val code: Int,
    val isDay: Boolean,
    val humidity: Int,
    val wind: Double,
    val days: List<WeatherDay>,
    val fetchedAt: Long,
)

class Place(val lat: String, val lon: String, val name: String)

/** Current weather and a short forecast from Open-Meteo, cached so the home screen never waits. */
object WeatherHub {
    var report by mutableStateOf<WeatherReport?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private fun loadCache(prefs: LauncherPrefs) {
        if (report != null || prefs.weatherCache.isBlank()) return
        report = runCatching { parse(prefs.weatherCache, prefs.weatherTime.toLongOrNull() ?: 0L) }.getOrNull()
    }

    fun forget(prefs: LauncherPrefs) {
        report = null
        prefs.weatherCache = ""
        prefs.weatherTime = ""
    }

    suspend fun refresh(prefs: LauncherPrefs, force: Boolean = false) {
        loadCache(prefs)
        val lat = prefs.weatherLat
        val lon = prefs.weatherLon
        if (lat.isBlank() || lon.isBlank()) return
        val age = System.currentTimeMillis() - (report?.fetchedAt ?: 0L)
        if (!force && age < 30 * 60_000L) return
        loading = true
        val units = if (prefs.tempUnit == "f") "&temperature_unit=fahrenheit&wind_speed_unit=mph" else ""
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,relative_humidity_2m,weather_code,is_day,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=5$units"
        val result = withContext(Dispatchers.IO) { runCatching { httpGet(url) } }
        loading = false
        result
            .onSuccess { json ->
                val now = System.currentTimeMillis()
                runCatching { parse(json, now) }.onSuccess {
                    report = it
                    prefs.weatherCache = json
                    prefs.weatherTime = now.toString()
                    error = null
                }
            }
            .onFailure { error = "Couldn't reach the weather service" }
    }

    suspend fun searchCity(name: String): Place? = withContext(Dispatchers.IO) {
        runCatching {
            val query = URLEncoder.encode(name.trim(), "UTF-8")
            val json = JSONObject(httpGet("https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=$query"))
            val hit = json.optJSONArray("results")?.optJSONObject(0) ?: return@runCatching null
            val label = listOf(hit.optString("name"), hit.optString("country")).filter { it.isNotBlank() }.joinToString(", ")
            Place(hit.getDouble("latitude").toString(), hit.getDouble("longitude").toString(), label)
        }.getOrNull()
    }

    private fun parse(json: String, fetchedAt: Long): WeatherReport {
        val root = JSONObject(json)
        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val times = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val highs = daily.getJSONArray("temperature_2m_max")
        val lows = daily.getJSONArray("temperature_2m_min")
        val days = (0 until times.length()).map {
            WeatherDay(LocalDate.parse(times.getString(it)), codes.getInt(it), highs.getDouble(it), lows.getDouble(it))
        }
        return WeatherReport(
            temp = current.getDouble("temperature_2m"),
            code = current.getInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
            humidity = current.optInt("relative_humidity_2m"),
            wind = current.optDouble("wind_speed_10m"),
            days = days,
            fetchedAt = fetchedAt,
        )
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/** A rough device location for weather. Needs the coarse location permission already granted. */
@SuppressLint("MissingPermission")
suspend fun currentPlace(context: Context): Place? {
    val lm = context.getSystemService(LocationManager::class.java) ?: return null
    val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    val fresh = last?.takeIf { System.currentTimeMillis() - it.time < 60 * 60_000L }
    val location: Location = fresh ?: providers.firstOrNull()?.let { provider ->
        withTimeoutOrNull(15_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }
                }
                runCatching { lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper()) }
                    .onFailure { if (cont.isActive) cont.resume(null) }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
            }
        }
    } ?: last ?: return null

    @Suppress("DEPRECATION")
    val name = withContext(Dispatchers.IO) {
        runCatching {
            Geocoder(context).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()?.let {
                it.locality ?: it.subAdminArea ?: it.adminArea
            }
        }.getOrNull()
    } ?: "Current location"
    return Place("%.3f".format(java.util.Locale.US, location.latitude), "%.3f".format(java.util.Locale.US, location.longitude), name)
}

enum class Sky { Clear, PartCloud, Cloud, Fog, Drizzle, Rain, Snow, Storm }

fun skyFor(code: Int): Sky = when (code) {
    0 -> Sky.Clear
    1, 2 -> Sky.PartCloud
    3 -> Sky.Cloud
    45, 48 -> Sky.Fog
    in 51..57 -> Sky.Drizzle
    in 61..67, in 80..82 -> Sky.Rain
    in 71..77, 85, 86 -> Sky.Snow
    in 95..99 -> Sky.Storm
    else -> Sky.Cloud
}

fun skyLabel(code: Int, isDay: Boolean = true): String = when (skyFor(code)) {
    Sky.Clear -> if (isDay) "Sunny" else "Clear"
    Sky.PartCloud -> "Partly cloudy"
    Sky.Cloud -> "Cloudy"
    Sky.Fog -> "Foggy"
    Sky.Drizzle -> "Drizzle"
    Sky.Rain -> "Rain"
    Sky.Snow -> "Snow"
    Sky.Storm -> "Thunderstorm"
}

private val Sun = Color(0xFFFFD27A)
private val Moon = Color(0xFFE6F2F0)
private val CloudWhite = Color(0xFFF2FBF9)
private val CloudGrey = Color(0xFFB9CFCC)

/**
 * A drawn weather icon. When [animate] is true the sun's rays turn slowly and rain,
 * snow and fog drift; it should be false whenever the icon is off screen.
 */
@Composable
fun WeatherGlyph(code: Int, isDay: Boolean, size: Dp, accent: Color, animate: Boolean, modifier: Modifier = Modifier) {
    val t = if (animate) {
        val transition = rememberInfiniteTransition(label = "weather")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "t").value
    } else 0f
    val sky = skyFor(code)
    Canvas(modifier.size(size)) {
        val w = this.size.width
        when (sky) {
            Sky.Clear -> if (isDay) sun(Offset(w / 2, w / 2), w * 0.24f, t) else moon(Offset(w / 2, w / 2), w * 0.3f)
            Sky.PartCloud -> {
                if (isDay) sun(Offset(w * 0.62f, w * 0.36f), w * 0.17f, t) else moon(Offset(w * 0.64f, w * 0.34f), w * 0.2f)
                cloud(Offset(w * 0.44f, w * 0.6f), w * 0.72f, CloudWhite)
            }
            Sky.Cloud -> {
                cloud(Offset(w * 0.6f, w * 0.4f), w * 0.55f, CloudGrey)
                cloud(Offset(w * 0.45f, w * 0.58f), w * 0.78f, CloudWhite)
            }
            Sky.Fog -> {
                cloud(Offset(w * 0.5f, w * 0.4f), w * 0.7f, CloudGrey)
                repeat(3) { i ->
                    val y = w * (0.68f + i * 0.1f)
                    val shift = sin((t + i * 0.3f) * 2 * Math.PI).toFloat() * w * 0.05f
                    drawLine(CloudWhite.copy(alpha = 0.8f), Offset(w * 0.18f + shift, y), Offset(w * 0.82f + shift, y), w * 0.045f, StrokeCap.Round)
                }
            }
            Sky.Drizzle, Sky.Rain -> {
                cloud(Offset(w * 0.5f, w * 0.38f), w * 0.78f, CloudWhite)
                val drops = if (sky == Sky.Rain) 4 else 3
                repeat(drops) { i ->
                    val phase = (t + i / drops.toFloat()) % 1f
                    val x = w * (0.28f + i * 0.44f / (drops - 1).coerceAtLeast(1))
                    val y = w * (0.6f + phase * 0.3f)
                    drawLine(accent.copy(alpha = 1f - phase), Offset(x, y), Offset(x - w * 0.04f, y + w * 0.1f), w * 0.04f, StrokeCap.Round)
                }
            }
            Sky.Snow -> {
                cloud(Offset(w * 0.5f, w * 0.38f), w * 0.78f, CloudWhite)
                repeat(4) { i ->
                    val phase = (t + i / 4f) % 1f
                    val x = w * (0.3f + i * 0.14f) + sin((phase + i) * 6f) * w * 0.02f
                    drawCircle(Color.White.copy(alpha = 1f - phase * 0.7f), w * 0.035f, Offset(x, w * (0.62f + phase * 0.3f)))
                }
            }
            Sky.Storm -> {
                cloud(Offset(w * 0.5f, w * 0.36f), w * 0.8f, CloudGrey)
                val flash = if (t in 0.1f..0.2f || t in 0.26f..0.32f) 1f else 0.75f
                val bolt = Path().apply {
                    moveTo(w * 0.54f, w * 0.52f)
                    lineTo(w * 0.42f, w * 0.74f)
                    lineTo(w * 0.52f, w * 0.74f)
                    lineTo(w * 0.44f, w * 0.94f)
                    lineTo(w * 0.64f, w * 0.66f)
                    lineTo(w * 0.53f, w * 0.66f)
                    lineTo(w * 0.6f, w * 0.52f)
                    close()
                }
                drawPath(bolt, Sun.copy(alpha = flash))
            }
        }
    }
}

private fun DrawScope.sun(center: Offset, radius: Float, t: Float) {
    drawCircle(Sun.copy(alpha = 0.25f), radius * 1.7f, center)
    rotate(t * 45f, center) {
        repeat(8) { i ->
            val angle = i * Math.PI / 4
            val start = Offset(center.x + cos(angle).toFloat() * radius * 1.35f, center.y + sin(angle).toFloat() * radius * 1.35f)
            val end = Offset(center.x + cos(angle).toFloat() * radius * 1.75f, center.y + sin(angle).toFloat() * radius * 1.75f)
            drawLine(Sun, start, end, radius * 0.22f, StrokeCap.Round)
        }
    }
    drawCircle(Sun, radius, center)
}

private fun DrawScope.moon(center: Offset, radius: Float) {
    val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, radius)) }
    val bite = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(Offset(center.x + radius * 0.45f, center.y - radius * 0.3f), radius * 0.85f))
    }
    drawCircle(Moon.copy(alpha = 0.18f), radius * 1.5f, center)
    drawPath(Path.combine(PathOperation.Difference, disc, bite), Moon)
}

private fun DrawScope.cloud(center: Offset, width: Float, color: Color) {
    val h = width * 0.36f
    drawCircle(color, h * 0.62f, Offset(center.x - width * 0.18f, center.y))
    drawCircle(color, h * 0.82f, Offset(center.x + width * 0.06f, center.y - h * 0.22f))
    drawCircle(color, h * 0.55f, Offset(center.x + width * 0.3f, center.y + h * 0.05f))
    drawRoundRect(
        color,
        topLeft = Offset(center.x - width * 0.4f, center.y),
        size = Size(width * 0.8f, h * 0.62f),
        cornerRadius = CornerRadius(h * 0.31f),
    )
}
