package com.carfbot.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Pal {
    val PinkTop = Color(0xFFF2A3BB)
    val PinkMid = Color(0xFFF6C9D5)
    val Cream = Color(0xFFFBF4F2)
    val Lav = Color(0xFFDAD3EF)
    val Slate = Color(0xFF6C7C93)
    val Blue = Color(0xFF8FA9BE)
    val CardGrey = Color(0xFFF1EFEF)
    val Pill = Brush.horizontalGradient(listOf(Color(0xFFFBE3EC), Color(0xFFE6DAF3)))
}

// Drop a font file (e.g. Inter) in res/font and swap this to FontFamily(Font(R.font.inter)).
val Sans: FontFamily = FontFamily.SansSerif

@Composable
fun T(
    text: String, size: Int, color: Color = Pal.Slate, weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, lineHeight: Int = 0,
    align: TextAlign? = null
) {
    Text(
        text, modifier = modifier, color = color, maxLines = maxLines, textAlign = align,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            fontFamily = Sans, fontSize = size.sp, fontWeight = weight,
            lineHeight = if (lineHeight > 0) lineHeight.sp else TextStyle.Default.lineHeight
        )
    )
}

@Composable
fun GlassCircle(size: Dp = 52.dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    Box(
        Modifier.size(size).shadow(8.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x33B07A99))
            .clip(CircleShape).background(Color.White.copy(alpha = 0.88f)).clickable { Haptics.tick(ctx); onClick() },
        contentAlignment = Alignment.Center
    ) { content() }
}
