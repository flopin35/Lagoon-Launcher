@file:OptIn(ExperimentalTextApi::class)

package com.lagoon.launcher

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

object Lagoon {
    val Deep = Color(0xFF03222A)
    val Panel = Color(0xF5062A31)
    val Surface = Color(0xFF0C3840)
    val SurfaceHigh = Color(0xFF14464F)
    val Text = Color(0xFFEAF8F5)
    val Muted = Color(0xFF8FB7B1)
    val Line = Color(0x1FFFFFFF)
    val Ink = Color(0xFF07323A)
}

class AccentOption(val id: String, val name: String, val color: Color)

val accentOptions = listOf(
    AccentOption("turquoise", "Turquoise", Color(0xFF2EC4B6)),
    AccentOption("aqua", "Aqua", Color(0xFF4FD6E8)),
    AccentOption("seafoam", "Seafoam", Color(0xFF8BE3C9)),
    AccentOption("coral", "Coral", Color(0xFFFF8A73)),
    AccentOption("sand", "Sand", Color(0xFFE9C46A)),
    AccentOption("lilac", "Lilac", Color(0xFFB7A6F0)),
)

fun accentColor(id: String): Color = (accentOptions.firstOrNull { it.id == id } ?: accentOptions.first()).color

val Outfit = FontFamily(
    Font(R.font.outfit, FontWeight.ExtraLight, variationSettings = FontVariation.Settings(FontVariation.weight(200))),
    Font(R.font.outfit, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.outfit, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.outfit, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.outfit, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

/** A superellipse: softer than a rounded square, the default icon shape. */
val SquircleShape = GenericShape { size, _ ->
    val n = 5.0
    val a = size.width / 2.0
    val b = size.height / 2.0
    val steps = 72
    for (i in 0..steps) {
        val t = 2 * PI * i / steps
        val c = cos(t)
        val s = sin(t)
        val x = (a + a * sign(c) * abs(c).pow(2 / n)).toFloat()
        val y = (b + b * sign(s) * abs(s).pow(2 / n)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A closed curve traced from a polar radius function r(t) in 0..1 of the half-size. */
private fun polarShape(steps: Int = 120, radius: (Double) -> Double) = GenericShape { size, _ ->
    val cx = size.width / 2.0
    val cy = size.height / 2.0
    for (i in 0..steps) {
        val t = 2 * PI * i / steps
        val r = radius(t)
        val x = (cx + cx * r * cos(t)).toFloat()
        val y = (cy + cy * r * sin(t)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

val HexagonShape = GenericShape { size, _ ->
    // Flat-topped hexagon with softened proportions so icons still fill it.
    val w = size.width
    val h = size.height
    moveTo(w * 0.25f, 0f)
    lineTo(w * 0.75f, 0f)
    lineTo(w, h * 0.5f)
    lineTo(w * 0.75f, h)
    lineTo(w * 0.25f, h)
    lineTo(0f, h * 0.5f)
    close()
}

// Eight soft petals, like a scalloped seal.
val FlowerShape = polarShape { t -> 0.92 + 0.08 * cos(8 * t) }

// Four rounded lobes.
val CloverShape = polarShape { t -> 0.86 + 0.14 * abs(cos(2 * t)).pow(0.6) }

class IconShapeOption(val id: String, val name: String, val shape: Shape)

val iconShapes = listOf(
    IconShapeOption("squircle", "Squircle", SquircleShape),
    IconShapeOption("circle", "Circle", CircleShape),
    IconShapeOption("rounded", "Rounded", RoundedCornerShape(24)),
    IconShapeOption("square", "Square", RoundedCornerShape(10)),
    IconShapeOption("pebble", "Pebble", RoundedCornerShape(50, 50, 50, 16)),
    IconShapeOption("teardrop", "Teardrop", RoundedCornerShape(50, 50, 50, 4)),
    IconShapeOption("hexagon", "Hexagon", HexagonShape),
    IconShapeOption("flower", "Flower", FlowerShape),
    IconShapeOption("clover", "Clover", CloverShape),
)

fun iconShape(id: String): Shape = (iconShapes.firstOrNull { it.id == id } ?: iconShapes.first()).shape

fun <T> motionSpec(motion: String): FiniteAnimationSpec<T> = when (motion) {
    "off" -> snap()
    "lively" -> spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
    else -> spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow)
}
