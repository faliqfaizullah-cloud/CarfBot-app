package com.carfbot.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

enum class Tool { Blur, Tint, Wand, Mosaic }

/**
 * AI Studio: touch any area of a photo to blur, colour or pixelate it, or let AI describe what is there,
 * tag the image and write (and re-write) a text-to-image style prompt for it.
 */
@Composable
fun StudioScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var edited by remember { mutableStateOf<Bitmap?>(null) }
    var showOriginal by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf(Tool.Wand) }
    var colorIdx by remember { mutableIntStateOf(0) }
    var prompt by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("Pick a photo to start") }
    var reprompt by remember { mutableStateOf("") }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    fun load(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            val b = withContext(Dispatchers.IO) { StudioOps.loadBitmap(ctx, uri) }
            if (b != null) {
                original = b; edited = b; prompt = ""; tags = emptyList(); reprompt = ""
                tool = Tool.Wand; showOriginal = false
                note = "Touch on any area to edit the image"
            } else {
                note = "I couldn’t open that image."
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> load(uri) }

    fun latest(screenshot: Boolean) {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ctx.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            note = "Allow photo access for CarfBot first (Settings > Apps > CarfBot > Permissions)."
            return
        }
        val u = Smart.latestImage(ctx, screenshot)
        if (u == null) note = "I couldn’t find a recent ${if (screenshot) "screenshot" else "photo"}." else load(u)
    }

    fun analyze(instruction: String?) {
        val bmp = edited ?: return
        busy = true
        note = "Looking…"
        scope.launch {
            val b64 = withContext(Dispatchers.IO) { StudioOps.toBase64(bmp) }
            val reply = AiClient.vision(ctx, StudioOps.promptFor(instruction), b64)
            val parsed = StudioOps.parse(reply)
            if (parsed != null) {
                prompt = parsed.first; tags = parsed.second
                note = "Touch an area with the wand to ask about it"
                Haptics.click(ctx)
            } else {
                note = reply.take(160)
            }
            busy = false
        }
    }

    fun askArea(src: Bitmap, cx: Int, cy: Int, r: Int) {
        busy = true
        note = "Looking at that area…"
        scope.launch {
            val b64 = withContext(Dispatchers.IO) { StudioOps.toBase64(StudioOps.crop(src, cx, cy, r), 768) }
            val reply = AiClient.vision(ctx, StudioOps.AREA_PROMPT, b64)
            val parsed = StudioOps.parse(reply)
            if (parsed != null) {
                prompt = parsed.first; tags = parsed.second
                note = "Touch another area to ask again"
                Haptics.click(ctx)
            } else {
                note = reply.take(160)
            }
            busy = false
        }
    }

    fun applyAt(x: Float, y: Float, dragging: Boolean) {
        val src = edited ?: return
        if (showOriginal || busy) return
        val sc = min(boxSize.width.toFloat() / src.width, boxSize.height.toFloat() / src.height)
        if (sc <= 0f) return
        val ox = (boxSize.width - src.width * sc) / 2f
        val oy = (boxSize.height - src.height * sc) / 2f
        val bx = ((x - ox) / sc).toInt()
        val by = ((y - oy) / sc).toInt()
        if (bx !in 0 until src.width || by !in 0 until src.height) return
        val r = (min(src.width, src.height) * 0.14f).toInt().coerceAtLeast(24)
        when (tool) {
            Tool.Blur -> { Haptics.tick(ctx); edited = StudioOps.blur(src, bx, by, r) }
            Tool.Mosaic -> { Haptics.tick(ctx); edited = StudioOps.mosaic(src, bx, by, r) }
            Tool.Tint -> { Haptics.tick(ctx); edited = StudioOps.tint(src, bx, by, r, StudioOps.colors[colorIdx % StudioOps.colors.size]) }
            Tool.Wand -> if (!dragging) { Haptics.tick(ctx); askArea(src, bx, by, r * 2) }
        }
    }

    fun save() {
        val bmp = edited ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) { StudioOps.save(ctx, bmp) }
            note = if (ok) "Saved to Pictures/CarfBot" else "Couldn’t save the image (storage permission?)."
            if (ok) Haptics.confirm(ctx)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 18.dp)
        ) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, onBack)
                T("Edit Image", 17, Look.ink, FontWeight.Medium, Modifier.weight(1f), align = TextAlign.Center)
                GlassIconButton(Icons.Outlined.Image, { picker.launch("image/*") })
                Spacer(Modifier.width(8.dp))
                GlassIconButton(Icons.Filled.Check, { save() }, solid = edited != null)
            }
            Spacer(Modifier.height(6.dp))

            if (edited == null) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().glass(RoundedCornerShape(32.dp), dark = false).padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.AutoFixHigh, null, tint = Look.ink, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(14.dp))
                    T("AI Studio", 34, Look.ink, FontWeight.Light, align = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    T(
                        "Touch any part of a photo to blur, colour or pixelate it. Let AI describe it, tag it and write a prompt for it.",
                        15, Look.ink.copy(alpha = 0.7f), lineHeight = 21, align = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    GlassChip("Pick a photo", icon = Icons.Outlined.Image) { picker.launch("image/*") }
                    Spacer(Modifier.height(10.dp))
                    GlassChip("Latest screenshot") { latest(true) }
                    Spacer(Modifier.height(10.dp))
                    GlassChip("Latest photo") { latest(false) }
                }
            } else {
                Box(
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(30.dp))
                        .background(Color.White.copy(alpha = 0.35f))
                        .onSizeChanged { boxSize = it }
                        .pointerInput(Unit) { detectTapGestures { off -> applyAt(off.x, off.y, false) } }
                        .pointerInput(Unit) {
                            var last = Offset.Zero
                            detectDragGestures(
                                onDragStart = { last = it; applyAt(it.x, it.y, true) },
                                onDrag = { change, _ ->
                                    if ((change.position - last).getDistance() > 36f) {
                                        last = change.position
                                        applyAt(change.position.x, change.position.y, true)
                                    }
                                }
                            )
                        }
                ) {
                    val shown = if (showOriginal) original else edited
                    if (shown != null) {
                        Image(
                            bitmap = shown.asImageBitmap(), contentDescription = null,
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (busy) Box(Modifier.align(Alignment.TopCenter).padding(12.dp)) { GlassChip("Looking…") }
                    if (showOriginal) Box(Modifier.align(Alignment.TopStart).padding(12.dp)) { GlassChip("Original") }
                }
                Spacer(Modifier.height(10.dp))
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp), dark = false).padding(14.dp)) {
                    GlassChip("Prompt", selected = true)
                    Spacer(Modifier.height(6.dp))
                    T(
                        if (prompt.isBlank()) "Tap the wand to let AI describe this image, then re-prompt to change the style." else prompt,
                        15, Look.ink, maxLines = 4, lineHeight = 21
                    )
                    if (tags.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(tags) { GlassChip(it) } }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp).height(54.dp).glass(CircleShape, dark = false).padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassIconButton(Icons.Filled.Close, { prompt = ""; tags = emptyList(); reprompt = "" }, size = 40.dp)
                    BasicTextField(
                        value = reprompt, onValueChange = { reprompt = it }, singleLine = true,
                        textStyle = TextStyle(fontFamily = Sans, fontSize = 16.sp, color = Look.ink),
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        decorationBox = { inner ->
                            if (reprompt.isEmpty()) T("Re-prompt: make it more cyberpunk", 15, Look.ink.copy(alpha = 0.5f), maxLines = 1)
                            inner()
                        }
                    )
                    GlassIconButton(Icons.Outlined.AutoFixHigh, { analyze(reprompt) }, size = 40.dp, solid = true)
                }
            }

            Spacer(Modifier.height(10.dp))
            T(note, 14, Look.ink.copy(alpha = 0.7f), align = TextAlign.Center, modifier = Modifier.fillMaxWidth(), maxLines = 2)

            if (edited != null) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    StudioBtn(Icons.Outlined.Layers, showOriginal, false) {
                        showOriginal = !showOriginal
                        note = if (showOriginal) "Showing the original. Tap again to go back." else "Touch on any area to edit the image"
                    }
                    StudioBtn(Icons.Outlined.Crop, tool == Tool.Blur, false) { tool = Tool.Blur; note = "Blur: touch or drag over an area" }
                    StudioBtn(Icons.Outlined.FormatColorFill, tool == Tool.Tint, false) {
                        if (tool == Tool.Tint) colorIdx++ else tool = Tool.Tint
                        note = "Tint: touch an area to colour it. Tap this button again for a new colour"
                    }
                    StudioBtn(Icons.Outlined.AutoFixHigh, tool == Tool.Wand, true) {
                        tool = Tool.Wand
                        note = "AI: touch an area and I’ll tell you what is there"
                        if (prompt.isBlank() && !busy) analyze(null)
                    }
                    StudioBtn(Icons.Outlined.Texture, tool == Tool.Mosaic, false) { tool = Tool.Mosaic; note = "Mosaic: touch or drag over an area" }
                    StudioBtn(Icons.Outlined.Refresh, false, false) {
                        edited = original; showOriginal = false
                        note = "Edits cleared"
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, big: Boolean, onClick: () -> Unit) {
    GlassIconButton(icon, onClick, size = if (big) 58.dp else 50.dp, solid = active)
}
