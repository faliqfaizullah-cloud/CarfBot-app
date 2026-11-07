package com.carfbot.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The "Futuristic Blur" theme: pastel sky, soft blurred shapes, thin type and glass controls. */
object Look {
    var futuristic by mutableStateOf(true)
    var navLight by mutableStateOf(false)
    val ink = Color(0xFF22304A)

    fun skyColors(h: Int = Ctx.hour()): List<Color> = when (h) {
        in 5..11 -> listOf(Color(0xFFCFE0EC), Color(0xFFE6EEF4), Color(0xFFF3EEF3))
        in 12..17 -> listOf(Color(0xFFD9E6F4), Color(0xFFEDE7F3), Color(0xFFF6E9EF))
        else -> listOf(Color(0xFFEAD7E4), Color(0xFFF1E2EB), Color(0xFFE2DBEC))
    }
}

/** Blurs in from out of focus (p = 0) to sharp (p = 1). */
fun Modifier.focusIn(p: Float, maxBlur: Dp = 18.dp): Modifier {
    val q = p.coerceIn(0f, 1f)
    return this
        .blur(maxBlur * (1f - q), BlurredEdgeTreatment.Unbounded)
        .graphicsLayer {
            alpha = q
            val s = 0.95f + 0.05f * q
            scaleX = s; scaleY = s
        }
}

/** Slowly drifting pastel light blobs behind everything. */
@Composable
fun FutureGlow(modifier: Modifier = Modifier.fillMaxSize()) {
    val t = rememberInfiniteTransition(label = "fglow")
    val d by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "d"
    )
    Canvas(modifier) {
        fun blob(cx: Float, cy: Float, r: Float, c: Color) =
            drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
        blob(size.width * (0.15f + 0.10f * d), size.height * 0.25f, size.width * 0.80f, Color(0xFF9EC3E6).copy(alpha = 0.55f))
        blob(size.width * (0.90f - 0.10f * d), size.height * 0.35f, size.width * 0.70f, Color(0xFFE9A9C8).copy(alpha = 0.50f))
        blob(size.width * 0.50f, size.height * (0.85f - 0.05f * d), size.width * 0.80f, Color(0xFFC7B6EA).copy(alpha = 0.45f))
    }
}

/** A soft, out-of-focus figure with a glowing visor, drawn with gradients (no images needed). */
@Composable
fun GhostFigure(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "ghost")
    val d by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d"
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val shift = (d - 0.5f) * w * 0.05f
        fun soft(cx: Float, cy: Float, rx: Float, ry: Float, color: Color) {
            withTransform({ translate(cx, cy); scale(1f, ry / rx, Offset.Zero) }) {
                drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), Offset.Zero, rx), rx, Offset.Zero)
            }
        }
        soft(w * 0.62f + shift, h * 0.90f, w * 0.70f, h * 0.45f, Color(0xFF1B2A44).copy(alpha = 0.85f))
        soft(w * 0.54f + shift * 1.5f, h * 0.42f, w * 0.34f, h * 0.30f, Color(0xFF2B4A6F).copy(alpha = 0.80f))
        soft(w * 0.52f + shift * 1.5f, h * 0.40f, w * 0.22f, h * 0.20f, Color(0xFF8FB4D8).copy(alpha = 0.55f))
        val glow = Color(0xFFFF7A45)
        for (i in 0..1) {
            val y = h * (0.37f + 0.05f * i)
            val a = Offset(w * 0.30f + shift * 2f, y + h * 0.015f * i)
            val b = Offset(w * 0.64f + shift * 2f, y - h * 0.02f)
            drawLine(glow.copy(alpha = 0.25f), a, b, strokeWidth = 16.dp.toPx(), cap = StrokeCap.Round)
            drawLine(glow.copy(alpha = 0.95f), a, b, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier,
    size: Dp = 46.dp, tint: Color = Look.ink, solid: Boolean = false
) {
    val ctx = LocalContext.current
    Box(
        modifier.size(size)
            .then(if (solid) Modifier.clip(CircleShape).background(Color.White) else Modifier.glass(CircleShape, dark = false))
            .clickable { Haptics.tick(ctx); onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.48f)) }
}

@Composable
fun GlassChip(
    text: String, modifier: Modifier = Modifier, icon: ImageVector? = null,
    selected: Boolean = false, onClick: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    var m = modifier.then(
        if (selected) Modifier.clip(CircleShape).background(Color.White) else Modifier.glass(CircleShape, dark = false)
    )
    if (onClick != null) m = m.clickable { Haptics.tick(ctx); onClick() }
    Row(m.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = Look.ink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        T(text, 14, Look.ink, FontWeight.Medium)
    }
}

/** Bottom row of round glass buttons; the selected one turns solid white and grows. */
@Composable
fun FutureToolbar(
    items: List<Pair<ImageVector, String>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, (icon, label) ->
            val on = i == selected
            val scale by animateFloatAsState(if (on) 1.18f else 1f, spring(dampingRatio = 0.55f, stiffness = 450f), label = "tb")
            Box(
                Modifier.size(54.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                    .then(if (on) Modifier.clip(CircleShape).background(Color.White) else Modifier.glass(CircleShape, dark = false))
                    .clickable { Haptics.tick(ctx); onSelect(i) },
                contentAlignment = Alignment.Center
            ) { Icon(icon, label, tint = if (on) Look.ink else Color(0xFF55657F), modifier = Modifier.size(24.dp)) }
        }
    }
}
