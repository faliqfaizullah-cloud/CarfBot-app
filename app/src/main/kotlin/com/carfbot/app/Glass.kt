package com.carfbot.app

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Frosted-glass surface: translucent gradient fill plus a light rim, like glass catching light. */
fun Modifier.glass(shape: Shape, dark: Boolean = true): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            if (dark) listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f))
            else listOf(Color.White.copy(alpha = 0.72f), Color.White.copy(alpha = 0.40f))
        )
    )
    .border(
        1.dp,
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (dark) 0.38f else 0.90f),
                Color.White.copy(alpha = if (dark) 0.06f else 0.25f)
            )
        ),
        shape
    )

/** Scalloped "cookie" shape, one of the signature Material 3 Expressive shapes. */
class CookieShape(private val bumps: Int = 9, private val depth: Float = 0.07f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = min(size.width, size.height) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val path = Path()
        val n = 240
        for (i in 0..n) {
            val t = (2.0 * PI * i / n).toFloat()
            val rr = r * (1f - depth + depth * cos(bumps * t))
            val x = cx + rr * cos(t)
            val y = cy + rr * sin(t)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** Glass button whose corners morph and which springs when pressed (Material 3 Expressive behaviour). */
@Composable
fun ExpressiveSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    rest: Dp = 24.dp,
    pressedRadius: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val ctx = LocalContext.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val radius by animateDpAsState(
        if (pressed) pressedRadius else rest, spring(dampingRatio = 0.5f, stiffness = 500f), label = "radius"
    )
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "scale"
    )
    Box(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .glass(RoundedCornerShape(max(radius.value, 0f).dp), dark)
            .clickable(interactionSource = source, indication = null) { Haptics.tick(ctx); onClick() },
        content = content
    )
}

/** Wavy progress line (Material 3 Expressive "wavy" indicator). The filled part waves; the track stays straight. */
@Composable
fun WavyLinearProgress(
    progress: Float, color: Color, track: Color, modifier: Modifier = Modifier,
    amplitude: Dp = 3.dp, wavelength: Dp = 24.dp, stroke: Dp = 6.dp
) {
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "phase"
    )
    Canvas(modifier.fillMaxWidth().height(amplitude * 2 + stroke + 2.dp)) {
        val w = size.width
        val cy = size.height / 2f
        val sw = stroke.toPx()
        val amp = amplitude.toPx()
        val wl = wavelength.toPx()
        val endX = w * progress.coerceIn(0f, 1f)
        val trackStart = min(w, endX + sw)
        if (trackStart < w) {
            drawLine(track, Offset(trackStart, cy), Offset(w, cy), strokeWidth = sw, cap = StrokeCap.Round)
        }
        if (endX > sw) {
            val path = Path()
            var x = sw / 2f
            path.moveTo(x, cy + amp * sin(phase + x / wl * 2f * PI.toFloat()))
            while (x < endX) {
                x = min(endX, x + 3f)
                path.lineTo(x, cy + amp * sin(phase + x / wl * 2f * PI.toFloat()))
            }
            drawPath(path, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            drawLine(color, Offset(sw / 2f, cy), Offset(sw / 2f, cy), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}

/** Wavy circular progress ring with content in the middle. */
@Composable
fun WavyRing(
    progress: Float, color: Color, track: Color, modifier: Modifier = Modifier,
    fastWave: Boolean = false, stroke: Dp = 9.dp, amplitude: Dp = 3.dp,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val t = rememberInfiniteTransition(label = "ring")
    val phase by t.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(if (fastWave) 1200 else 3200, easing = LinearEasing)), label = "phase"
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val amp = amplitude.toPx()
            val rad = (min(size.width, size.height) - sw) / 2f - amp
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(track, radius = rad, center = c, style = Stroke(sw))
            val p = progress.coerceIn(0f, 1f)
            if (p > 0.005f) {
                val path = Path()
                val steps = max((360 * p).roundToInt(), 2)
                for (i in 0..steps) {
                    val a = (-90f + 360f * p * i / steps) * (PI.toFloat() / 180f)
                    val rr = rad + amp * sin(12f * a + phase)
                    val x = c.x + rr * cos(a)
                    val y = c.y + rr * sin(a)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        content()
    }
}

/** Material You colours from the wallpaper on Android 12+, with a pink/violet fallback. */
@Composable
fun CarfTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(ctx)
    else darkColorScheme(primary = Color(0xFFF2A3BB), tertiary = Color(0xFF9B7BE0))
    MaterialTheme(colorScheme = scheme, content = content)
}
