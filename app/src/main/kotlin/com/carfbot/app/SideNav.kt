package com.carfbot.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

enum class NavGlyph { Sparkle, Calendar, Chat, Folder, Chart }

/** The five navigation glyphs, drawn by hand so they match the design exactly. [detail] is the colour of the cut-outs. */
@Composable
fun NavIcon(kind: NavGlyph, tint: Color, detail: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val u = size.width / 24f
        fun box(x: Float, y: Float, w: Float, h: Float, rad: Float, c: Color) =
            drawRoundRect(c, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(rad * u))
        when (kind) {
            NavGlyph.Sparkle -> {
                val p = Path()
                p.moveTo(12f * u, 1.5f * u)
                p.quadraticBezierTo(13f * u, 11f * u, 22.5f * u, 12f * u)
                p.quadraticBezierTo(13f * u, 13f * u, 12f * u, 22.5f * u)
                p.quadraticBezierTo(11f * u, 13f * u, 1.5f * u, 12f * u)
                p.quadraticBezierTo(11f * u, 11f * u, 12f * u, 1.5f * u)
                p.close()
                drawPath(p, tint)
            }
            NavGlyph.Calendar -> {
                box(3f, 5f, 18f, 16f, 3.5f, tint)
                box(3f, 5f, 18f, 5.5f, 2.5f, detail)
                box(7f, 2.5f, 2.4f, 4f, 1.2f, tint)
                box(14.6f, 2.5f, 2.4f, 4f, 1.2f, tint)
                for (row in 0..1) for (col in 0..2) {
                    drawCircle(detail, 0.95f * u, Offset((7.5f + col * 4.5f) * u, (14f + row * 3.8f) * u))
                }
            }
            NavGlyph.Chat -> {
                drawCircle(tint, 8.6f * u, Offset(12f * u, 11f * u))
                val tail = Path()
                tail.moveTo(3.6f * u, 21.4f * u)
                tail.lineTo(10f * u, 18f * u)
                tail.lineTo(5f * u, 14.2f * u)
                tail.close()
                drawPath(tail, tint)
                for (i in 0..2) drawCircle(detail, 1.25f * u, Offset((8.2f + i * 3.8f) * u, 11f * u))
            }
            NavGlyph.Folder -> {
                box(3f, 5.5f, 8f, 5f, 2f, tint)
                box(3f, 8f, 18f, 12.5f, 3f, tint)
                box(8f, 14f, 8f, 1.8f, 0.9f, detail)
            }
            NavGlyph.Chart -> {
                box(4f, 4f, 16f, 16f, 4f, tint)
                box(7.6f, 11.5f, 2f, 5.5f, 1f, detail)
                box(11f, 8f, 2f, 9f, 1f, detail)
                box(14.4f, 10f, 2f, 7f, 1f, detail)
            }
        }
    }
}

/**
 * Frosted vertical navigation capsule. Dark version: near-black glass with a white disc on the active item.
 * Light version: pale blue-violet glass with a ringed disc on the active item.
 */
@Composable
fun SideNav(
    items: List<NavGlyph>, selected: Int, onSelect: (Int) -> Unit, light: Boolean, modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val outer = RoundedCornerShape(38.dp)
    val inner = RoundedCornerShape(32.dp)
    val body = if (light) {
        Brush.verticalGradient(
            listOf(
                Color(0xFFC3C0F4).copy(alpha = 0.62f), Color(0xFF9694F2).copy(alpha = 0.55f),
                Color(0xFF6D6CE6).copy(alpha = 0.50f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xFF1C1D2D).copy(alpha = 0.80f), Color(0xFF14121C).copy(alpha = 0.84f),
                Color(0xFF3A1F2B).copy(alpha = 0.80f)
            )
        )
    }
    val sheen = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.20f), Color.Transparent))

    Box(
        modifier.clip(outer)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.5.dp, Color.White.copy(alpha = 0.45f), outer)
            .padding(6.dp)
    ) {
        Column(
            Modifier.clip(inner).background(body).background(sheen)
                .border(1.dp, Color.White.copy(alpha = if (light) 0.75f else 0.14f), inner)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEachIndexed { i, g ->
                NavItem(g, i == selected, i == 0, light) { Haptics.tick(ctx); onSelect(i) }
            }
        }
    }
}

@Composable
private fun NavItem(glyph: NavGlyph, active: Boolean, top: Boolean, light: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (active) 1f else 0.92f, spring(dampingRatio = 0.55f, stiffness = 450f), label = "navScale")
    val disc by animateColorAsState(
        when {
            light && active -> Color(0xFF2A2FA0).copy(alpha = 0.55f)
            light && top -> Color.White.copy(alpha = 0.35f)
            light -> Color.Transparent
            active -> Color.White
            top -> Color(0xFF3A3A44)
            else -> Color.Transparent
        },
        tween(250), label = "navDisc"
    )
    val tint = when {
        light -> Color.White
        active -> Color(0xFF1B1B25)
        top -> Color.White
        else -> Color(0xFFB9B9C6).copy(alpha = 0.78f)
    }
    val detail = when {
        light -> Color(0xFF3B3FB5)
        active -> Color.White
        else -> Color(0xFF1F1F2B)
    }
    Box(
        Modifier.size(52.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape).background(disc)
            .then(if (light && active) Modifier.border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { NavIcon(glyph, tint, detail, Modifier.size(24.dp)) }
}
