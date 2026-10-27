package com.lagoon.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

class WallpaperPreset(val id: String, val name: String, val light: Boolean, val paint: DrawScope.() -> Unit)

private fun DrawScope.glow(color: Color, cx: Float, cy: Float, r: Float) {
    val center = Offset(size.width * cx, size.height * cy)
    val radius = size.maxDimension * r
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, radius), radius, center)
}

/** Faint wave lines across the lower part of the screen, the lagoon's one recurring motif. */
private fun DrawScope.waves(color: Color, count: Int, baseline: Float, amplitude: Float) {
    val w = size.width
    val h = size.height
    val steps = 60
    repeat(count) { i ->
        val path = Path()
        val y0 = h * (baseline + i * 0.055f)
        val amp = h * amplitude * (1f - i * 0.15f)
        for (s in 0..steps) {
            val t = s.toFloat() / steps
            val y = y0 + sin(t * 2f * PI.toFloat() * 1.4f + i * 0.9f) * amp
            if (s == 0) path.moveTo(0f, y) else path.lineTo(w * t, y)
        }
        drawPath(path, color.copy(alpha = color.alpha * (1f - i * 0.18f)), style = Stroke(width = 1.4.dp.toPx()))
    }
}

val wallpaperPresets = listOf(
    WallpaperPreset("lagoon", "Lagoon", light = false) {
        drawRect(
            Brush.linearGradient(
                listOf(Color(0xFF03222A), Color(0xFF0A4F59), Color(0xFF17958F)),
                start = Offset(0f, size.height), end = Offset(size.width, 0f),
            )
        )
        glow(Color(0x7060EEDC), 0.88f, 0.08f, 0.55f)
        glow(Color(0x80021A20), 0.1f, 1f, 0.6f)
        waves(Color(0x1CFFFFFF), 4, 0.7f, 0.03f)
    },
    WallpaperPreset("reef", "Reef", light = false) {
        drawRect(
            Brush.linearGradient(
                listOf(Color(0xFF0B3440), Color(0xFF1C7F80), Color(0xFF56C2B2)),
                start = Offset(0f, 0f), end = Offset(size.width, size.height),
            )
        )
        glow(Color(0x66FF9E80), 0.92f, 0.98f, 0.5f)
        glow(Color(0x4D9FF3E6), 0.12f, 0.08f, 0.45f)
        waves(Color(0x16FFFFFF), 3, 0.76f, 0.025f)
    },
    WallpaperPreset("seafoam", "Seafoam", light = true) {
        drawRect(
            Brush.linearGradient(
                listOf(Color(0xFFF2FCFA), Color(0xFFCDF3EC), Color(0xFF9FE3D8)),
                start = Offset(0f, 0f), end = Offset(size.width, size.height),
            )
        )
        glow(Color(0x663DD6C6), 0.85f, 0.2f, 0.5f)
        glow(Color(0x55FFFFFF), 0.1f, 0.9f, 0.5f)
        waves(Color(0x220A4F59), 4, 0.72f, 0.028f)
    },
    WallpaperPreset("midnight", "Midnight", light = false) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF020F13), Color(0xFF06242B), Color(0xFF0B3A42))))
        glow(Color(0x402EC4B6), 0.5f, -0.05f, 0.7f)
        waves(Color(0x127FF0E0), 5, 0.72f, 0.022f)
    },
    WallpaperPreset("dusk", "Dusk", light = false) {
        drawRect(
            Brush.verticalGradient(
                listOf(Color(0xFF10203A), Color(0xFF1D4F63), Color(0xFF3E8E8C), Color(0xFFF0A882))
            )
        )
        glow(Color(0x55FFC9A8), 0.5f, 1.05f, 0.5f)
        waves(Color(0x18FFFFFF), 3, 0.8f, 0.02f)
    },
)

fun presetById(id: String): WallpaperPreset? = wallpaperPresets.firstOrNull { it.id == id }

/** The user's own photo, scaled down once on import and kept in app storage. */
object CustomWallpaper {
    private fun file(context: Context) = File(context.filesDir, "wallpaper.jpg")

    fun import(context: Context, uri: Uri): Boolean = runCatching {
        val dm = context.resources.displayMetrics
        val target = max(dm.widthPixels, dm.heightPixels)
        val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > target) {
                    val scale = target.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts)!! }
        }
        file(context).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        true
    }.getOrDefault(false)

    fun load(context: Context): ImageBitmap? =
        runCatching { BitmapFactory.decodeFile(file(context).path)?.asImageBitmap() }.getOrNull()
}

@Composable
fun WallpaperLayer(id: String, version: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    when (id) {
        // The device wallpaper shows through the window; add a light scrim for legible text.
        "system" -> Box(modifier.fillMaxSize().background(Color(0x26000000)))
        "custom" -> {
            val image by produceState<ImageBitmap?>(null, version) {
                value = withContext(Dispatchers.IO) { CustomWallpaper.load(context) }
            }
            Box(modifier.fillMaxSize().background(Color(0xFF03222A))) {
                image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0x47000000), Color.Transparent, Color(0x5C000000)))
                    )
                )
            }
        }
        else -> {
            val preset = presetById(id) ?: wallpaperPresets.first()
            Canvas(modifier.fillMaxSize()) { preset.paint(this) }
        }
    }
}
