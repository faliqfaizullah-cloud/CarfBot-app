package com.carfbot.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private val Gold = Color(0xFFE6CB8E)

/** Greeting state of the assistant overlay: glass card + three glass orbs with a golden centre. */
@Composable
fun ColumnScope.GreetingSection(
    listening: Boolean, thinking: Boolean, level: Float,
    onMic: () -> Unit, onFiles: () -> Unit, onCamera: () -> Unit
) {
    Spacer(Modifier.height(18.dp))
    GreetingCard(listening, thinking, Modifier.weight(1f).fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    OrbRow(listening, level, onMic, onFiles, onCamera)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun GreetingCard(listening: Boolean, thinking: Boolean, modifier: Modifier) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val hello = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    val status = when {
        listening -> "I’m Listening"
        thinking -> "Thinking…"
        else -> "Tap the mic to talk"
    }
    val shape = RoundedCornerShape(36.dp)
    Column(
        modifier.clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.05f))), shape)
            .padding(horizontal = 26.dp, vertical = 26.dp)
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White)) { append("Let’s ") }
                withStyle(SpanStyle(color = Gold)) { append("get started") }
            },
            style = TextStyle(fontFamily = Sans, fontSize = 19.sp, letterSpacing = (-0.3).sp)
        )
        Spacer(Modifier.height(20.dp))
        val text = buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White)) {
                append("$hello,\nI’m ")
                appendInlineContent("avatar", "[c]")
                append(" CarfBot, your personal assistant. So tell me, ")
            }
            withStyle(SpanStyle(color = Color(0xFF8A8A8E))) { append("What can I help you with today?") }
        }
        val inline = mapOf(
            "avatar" to InlineTextContent(
                Placeholder(58.sp, 34.sp, PlaceholderVerticalAlign.TextCenter)
            ) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(17.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFF2A3BB), Color(0xFF9B7BE0)))),
                    contentAlignment = Alignment.Center
                ) { Text("C", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = Sans) }
            }
        )
        Text(
            text, inlineContent = inline,
            style = TextStyle(fontFamily = Sans, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.8).sp)
        )
        Spacer(Modifier.weight(1f))
        Text(
            status,
            style = TextStyle(fontFamily = Sans, fontSize = 18.sp, color = Color.White.copy(alpha = 0.85f), letterSpacing = (-0.3).sp)
        )
    }
}

@Composable
private fun OrbRow(listening: Boolean, level: Float, onMic: () -> Unit, onFiles: () -> Unit, onCamera: () -> Unit) {
    val t = rememberInfiniteTransition(label = "gold")
    val drift by t.animateFloat(
        -0.15f, 0.15f, infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift"
    )
    val pulse by t.animateFloat(
        1f, 1.07f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )
    Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
        // golden light streaks behind the orbs
        Canvas(Modifier.fillMaxSize().blur(26.dp)) {
            val w = size.width
            val h = size.height
            fun streak(cx: Float, cy: Float, sw: Float, sh: Float, rot: Float, a: Float) {
                rotate(rot, Offset(cx, cy)) {
                    drawOval(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color(0xFFE9C46A).copy(alpha = a), Color.Transparent),
                            startX = cx - sw / 2f, endX = cx + sw / 2f
                        ),
                        topLeft = Offset(cx - sw / 2f, cy - sh / 2f), size = Size(sw, sh)
                    )
                }
            }
            streak(w * (0.30f + drift), h * 0.55f, w * 0.55f, h * 0.16f, -8f, 0.65f)
            streak(w * (0.72f - drift), h * 0.50f, w * 0.45f, h * 0.12f, 6f, 0.45f)
            streak(w * 0.5f, h * 0.62f, w * 0.35f, h * 0.20f, 0f, 0.55f)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            GlassOrb(62.dp, onFiles) {
                Icon(Icons.Outlined.NoteAdd, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(26.dp))
            }
            GoldOrb(listening, level, pulse, onMic)
            GlassOrb(62.dp, onCamera) {
                Icon(Icons.Outlined.Videocam, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun GlassOrb(diameter: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    Box(
        Modifier.size(diameter).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.08f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f))), CircleShape)
            .clickable { Haptics.tick(ctx); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawOval(
                Color.White.copy(alpha = 0.26f),
                topLeft = Offset(size.width * 0.2f, size.height * 0.05f),
                size = Size(size.width * 0.6f, size.height * 0.22f)
            )
        }
        content()
    }
}

@Composable
private fun GoldOrb(listening: Boolean, level: Float, pulse: Float, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val s = if (listening) pulse + level * 0.12f else 1f
    Box(
        Modifier.size(76.dp).scale(s)
            .shadow(22.dp, CircleShape, spotColor = Color(0xCCE9C46A), ambientColor = Color(0x55E9C46A))
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    0f to Color(0xFFFBEFC4), 0.55f to Color(0xFFDDB85F), 1f to Color(0xFF9A8040)
                )
            )
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.1f))), CircleShape)
            .clickable { Haptics.tick(ctx); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawOval(
                Color.White.copy(alpha = 0.35f),
                topLeft = Offset(size.width * 0.22f, size.height * 0.05f),
                size = Size(size.width * 0.56f, size.height * 0.2f)
            )
        }
        Icon(Icons.Filled.Mic, null, tint = Color(0xFF4A3A12), modifier = Modifier.size(28.dp))
    }
}
