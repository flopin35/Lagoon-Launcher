package com.lagoon.launcher

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

class ClockStyleOption(val id: String, val name: String)

val clockStyles = listOf(
    ClockStyleOption("analog", "Analog"),
    ClockStyleOption("ring", "Ring"),
    ClockStyleOption("digital", "Digital"),
)

/**
 * The home screen clock. It ticks once a second, and only while [active], so the
 * second hand costs nothing when Lagoon is hidden behind another app.
 */
@Composable
fun HomeClock(
    prefs: LauncherPrefs,
    color: Color,
    accent: Color,
    centered: Boolean,
    active: Boolean,
    shadow: Boolean,
    scaleOverride: Float? = null,
    stackedOverride: Boolean? = null,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(active) {
        now = LocalDateTime.now()
        while (active) {
            delay(1000L - System.currentTimeMillis() % 1000L)
            now = LocalDateTime.now()
        }
    }

    val textShadow = if (shadow) Shadow(Color(0x55000000), Offset(0f, 3f), 18f) else null
    val scale = scaleOverride ?: when (prefs.clockSize) {
        "small" -> 0.62f
        "medium" -> 0.8f
        else -> 1f
    }
    val align = if (centered) Alignment.CenterHorizontally else Alignment.Start
    val motion = prefs.motion

    val greeting: @Composable () -> Unit = { Greeting(now, color, accent, textShadow) }
    val date: @Composable () -> Unit = {
        Text(
            now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
            color = color.copy(alpha = 0.9f),
            fontFamily = Outfit,
            fontSize = (20 * scale.coerceAtLeast(0.8f)).sp,
            style = TextStyle(shadow = textShadow),
        )
    }

    if (prefs.clockStyle == "digital") {
        Column(horizontalAlignment = align) {
            greeting()
            DigitalTime(now, prefs.use24h, color, (104 * scale).sp, textShadow, motion)
            date()
        }
        return
    }

    val faceSize = (210 * scale).dp
    val face: @Composable () -> Unit = {
        if (prefs.clockStyle == "ring") RingFace(now, prefs.use24h, color, accent, faceSize, motion, textShadow)
        else AnalogFace(now, color, accent, faceSize, motion)
    }
    val caption: @Composable () -> Unit = {
        Column(horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            greeting()
            if (prefs.clockStyle == "analog") {
                DigitalTime(now, prefs.use24h, color, (44 * scale.coerceAtLeast(0.8f)).sp, textShadow, motion)
            }
            date()
        }
    }

    // Side by side on a tablet, stacked on a narrow screen or when centred.
    if (stackedOverride ?: (centered || LocalConfiguration.current.screenWidthDp < 600)) {
        Column(horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            face()
            caption()
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            face()
            caption()
        }
    }
}

@Composable
private fun Greeting(now: LocalDateTime, color: Color, accent: Color, shadow: Shadow?) {
    val greeting = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(accent, CircleShape))
        Text(
            greeting.uppercase(),
            color = color.copy(alpha = 0.82f),
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            letterSpacing = 2.sp,
            style = TextStyle(shadow = shadow),
        )
    }
}

/** Digits roll up into place when they change; the colon breathes with the seconds. */
@Composable
private fun DigitalTime(
    now: LocalDateTime,
    use24h: Boolean,
    color: Color,
    fontSize: TextUnit,
    shadow: Shadow?,
    motion: String,
) {
    val time = now.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm"))
    val colonAlpha by animateFloatAsState(
        targetValue = if (motion == "off" || now.second % 2 == 0) 1f else 0.3f,
        animationSpec = tween(450),
        label = "colon",
    )
    val style = TextStyle(
        color = color,
        fontFamily = Outfit,
        fontWeight = FontWeight.ExtraLight,
        fontSize = fontSize,
        letterSpacing = (-fontSize.value * 0.03f).sp,
        shadow = shadow,
    )
    Row(verticalAlignment = Alignment.Bottom) {
        time.forEachIndexed { index, char ->
            if (char == ':') {
                Text(":", style = style.copy(color = color.copy(alpha = colonAlpha)))
            } else {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        if (motion == "off") {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            (slideInVertically(spring(dampingRatio = 0.75f, stiffness = 260f)) { it } + fadeIn(tween(300))) togetherWith
                                (slideOutVertically(spring(dampingRatio = 1f, stiffness = 260f)) { -it } + fadeOut(tween(200))) using
                                SizeTransform(clip = false)
                        }
                    },
                    label = "digit$index",
                ) { digit ->
                    Text(digit.toString(), style = style)
                }
            }
        }
        if (!use24h) {
            Text(
                now.format(DateTimeFormatter.ofPattern("a")),
                style = style.copy(fontSize = fontSize * 0.22f, fontWeight = FontWeight.Light, color = color.copy(alpha = 0.8f)),
                modifier = Modifier.padding(start = 8.dp, bottom = (fontSize.value * 0.2f).dp),
            )
        }
    }
}

/** The second hand's angle, springing to each new second with a small mechanical overshoot. */
@Composable
private fun secondAngle(now: LocalDateTime, motion: String): State<Float> = animateFloatAsState(
    targetValue = now.toLocalTime().toSecondOfDay() * 6f,
    animationSpec = if (motion == "off") snap() else spring(dampingRatio = 0.42f, stiffness = 520f),
    label = "second",
)

@Composable
private fun appearProgress(motion: String): Animatable<Float, *> {
    val appear = remember { Animatable(if (motion == "off") 1f else 0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    return appear
}

private fun DrawScope.pointOn(angleDegrees: Float, radius: Float): Offset {
    val rad = Math.toRadians(angleDegrees - 90.0)
    return center + Offset((cos(rad) * radius).toFloat(), (sin(rad) * radius).toFloat())
}

@Composable
private fun AnalogFace(now: LocalDateTime, color: Color, accent: Color, size: Dp, motion: String) {
    val second = secondAngle(now, motion)
    val appear = appearProgress(motion)
    val minuteAngle = (now.minute + now.second / 60f) * 6f
    val hourAngle = ((now.hour % 12) + now.minute / 60f) * 30f

    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val a = appear.value
        val sweep = second.value % 360f

        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent), center, r * 1.2f), r * 1.2f)
        drawCircle(color.copy(alpha = 0.07f), r)
        drawCircle(color.copy(alpha = 0.22f), r, style = Stroke(1.dp.toPx()))

        // A ring that fills as the minute passes.
        val ringWidth = 3.dp.toPx()
        val inset = ringWidth * 2
        val ringSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
        drawArc(accent.copy(alpha = 0.16f), 0f, 360f, false, Offset(inset, inset), ringSize, style = Stroke(ringWidth))
        drawArc(accent, -90f, sweep * a, false, Offset(inset, inset), ringSize, style = Stroke(ringWidth, cap = StrokeCap.Round))

        val outer = r - inset - 7.dp.toPx()
        for (i in 0 until 60) {
            val quarter = i % 15 == 0
            val hour = i % 5 == 0
            val length = when {
                quarter -> 11.dp.toPx()
                hour -> 7.dp.toPx()
                else -> 3.dp.toPx()
            }
            val tickAlpha = (a * 60f - i).coerceIn(0f, 1f) // ticks draw in around the dial
            drawLine(
                color = if (quarter) accent else color.copy(alpha = if (hour) 0.7f else 0.28f),
                start = pointOn(i * 6f, outer - length),
                end = pointOn(i * 6f, outer),
                strokeWidth = if (hour) 2.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round,
                alpha = tickAlpha,
            )
        }

        rotate(hourAngle * a) {
            drawLine(color, center + Offset(0f, r * 0.08f), center - Offset(0f, r * 0.46f), 5.dp.toPx(), StrokeCap.Round)
        }
        rotate(minuteAngle * a) {
            drawLine(color, center + Offset(0f, r * 0.1f), center - Offset(0f, r * 0.66f), 3.5.dp.toPx(), StrokeCap.Round)
        }
        rotate(sweep) {
            drawLine(accent, center + Offset(0f, r * 0.18f), center - Offset(0f, r * 0.74f), 1.6.dp.toPx(), StrokeCap.Round)
            drawCircle(accent, 3.5.dp.toPx(), center - Offset(0f, r * 0.74f))
        }
        drawCircle(accent, 5.5.dp.toPx())
        drawCircle(Lagoon.Deep, 2.dp.toPx())
    }
}

@Composable
private fun RingFace(
    now: LocalDateTime,
    use24h: Boolean,
    color: Color,
    accent: Color,
    size: Dp,
    motion: String,
    shadow: Shadow?,
) {
    val second = secondAngle(now, motion)
    val appear = appearProgress(motion)

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val a = appear.value
            val sweep = second.value % 360f
            val strokeWidth = 6.dp.toPx()
            val inset = strokeWidth
            val ringSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)

            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.18f), Color.Transparent), center, r), r)
            drawArc(color.copy(alpha = 0.12f), 0f, 360f, false, Offset(inset, inset), ringSize, style = Stroke(strokeWidth))
            drawArc(accent, -90f, sweep * a, false, Offset(inset, inset), ringSize, style = Stroke(strokeWidth, cap = StrokeCap.Round))

            // A bright head on the end of the sweep.
            val head = pointOn(sweep * a, r - inset)
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.6f), Color.Transparent), head, strokeWidth * 2.4f), strokeWidth * 2.4f, head)
            drawCircle(Color.White, strokeWidth * 0.32f, head)

            // Minute dots inside the ring light up as the seconds pass them.
            val dotRadius = r - inset - 12.dp.toPx()
            for (i in 0 until 60) {
                val lit = i * 6f <= sweep
                drawCircle(
                    color = if (lit) accent else color.copy(alpha = 0.2f),
                    radius = if (i % 5 == 0) 2.2.dp.toPx() else 1.2.dp.toPx(),
                    center = pointOn(i * 6f, dotRadius),
                    alpha = a,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DigitalTime(now, use24h, color, (size.value * 0.25f).sp, shadow, motion)
            Text(
                "%02d".format(now.second),
                color = accent,
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                fontSize = (size.value * 0.075f).sp,
                letterSpacing = 1.sp,
            )
        }
    }
}
