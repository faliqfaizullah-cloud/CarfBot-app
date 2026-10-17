package com.carfbot.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// ═══════════════════════════ Cards ═══════════════════════════

enum class CardKind { SHORTCUTS, HERO, DEVICE, MUSIC, FILES, CLIPBOARD, SETTINGS }

private class StackCard(val id: String, val title: String, val kind: CardKind, val height: Dp, val preview: String)

private fun heroTitle(): String = when (Ctx.hour()) {
    in 5..11 -> "Good Morning"
    in 12..17 -> "Good Afternoon"
    else -> "Good Evening"
}

private fun stackCards(): List<StackCard> = listOf(
    StackCard("shortcuts", "Smart Shortcuts", CardKind.SHORTCUTS, 150.dp, Ctx.pills().first().command),
    StackCard("hero", heroTitle(), CardKind.HERO, 230.dp, ""),
    StackCard("device", "My Device", CardKind.DEVICE, 150.dp, "battery"),
    StackCard("settings", "Settings", CardKind.SETTINGS, 58.dp, ""),
    StackCard("files", "Apps & Files", CardKind.FILES, 150.dp, "open camera, take a photo and share it on whatsapp"),
    StackCard("music", "Music & Media", CardKind.MUSIC, 150.dp, "next song"),
    StackCard("clipboard", "Clipboard & Screenshots", CardKind.CLIPBOARD, 150.dp, "summarize clipboard")
)

private fun itemsFor(kind: CardKind): List<Pair<String, String>> = when (kind) {
    CardKind.SHORTCUTS -> Ctx.pills().map { it.label to it.command }
    CardKind.FILES -> listOf(
        "Open an app" to "open ", "Find a file" to "find file ", "Find a photo" to "find photo ",
        "Find a document" to "find document ", "Directions" to "directions to ",
        "Call a contact" to "call ",
        "Chain commands" to "open camera, take a photo and share it on whatsapp"
    )
    CardKind.MUSIC -> listOf(
        "Play / resume" to "play", "Pause" to "pause", "Next song" to "next song",
        "Previous song" to "previous song", "What’s playing" to "what's playing", "Lyrics" to "lyrics",
        "Identify song" to "identify song", "Play a song" to "play "
    )
    CardKind.CLIPBOARD -> listOf(
        "Summarize clipboard" to "summarize clipboard", "Translate clipboard" to "translate clipboard to english",
        "Organize clipboard" to "organize clipboard", "What’s on my clipboard" to "what's on my clipboard",
        "Summarize screenshot" to "summarize my screenshot", "Extract screenshot text" to "extract text from my screenshot"
    )
    else -> emptyList()
}

// ═══════════════════════════ Chat state (survives open/close of the card) ═══════════════════════════

class ChatState(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val capture: suspend () -> Uri?
) {
    val entries = mutableStateListOf<Msg>()
    var partial by mutableStateOf("")
    var level by mutableFloatStateOf(0f)
    var listening by mutableStateOf(false)
    var thinking by mutableStateOf(false)
    var draft by mutableStateOf("")
    private val voice = VoiceController(ctx)

    init {
        voice.onLevel = { level = it }
        voice.onPartial = { partial = it }
        voice.onFinal = { send(it) }
        voice.onError = { m -> partial = ""; Haptics.reject(ctx); entries.add(Msg(m, false)) }
        voice.onState = { on -> listening = on }
    }

    fun hasMic() = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun startListening() { partial = ""; voice.start() }
    fun stopListening() { voice.finish() }
    fun cancelVoice() { voice.cancel() }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        partial = ""; draft = ""
        Haptics.tick(ctx)
        entries.add(Msg(t, true))
        thinking = true
        scope.launch {
            val reply = Router.execute(ctx, t, capture)
            if (reply != null) {
                entries.add(Msg(reply, false))
            } else {
                var idx = -1
                val answer = AiClient.ask(ctx, t, { x ->
                    if (idx < 0) { idx = entries.size; entries.add(Msg(x, false)) } else { entries[idx] = Msg(x, false) }
                }, capture)
                if (idx >= 0) entries[idx] = Msg(answer, false) else entries.add(Msg(answer, false))
            }
            thinking = false
            Haptics.click(ctx)
        }
    }

    fun clear() {
        voice.cancel()
        entries.clear(); partial = ""; thinking = false
    }
}

// ═══════════════════════════ Gestures ═══════════════════════════

/** Two-finger pinch. onPinch(zoom, centroidY) returns true once it has acted (the gesture then stops reporting). */
private fun Modifier.pinch(onPinch: (Float, Float) -> Boolean): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var initial = 0f
        var done = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pts = event.changes.filter { it.pressed }
            if (pts.size >= 2 && !done) {
                val d = (pts[0].position - pts[1].position).getDistance()
                if (initial == 0f) {
                    initial = d
                } else if (initial > 10f) {
                    val cy = (pts[0].position.y + pts[1].position.y) / 2f
                    if (onPinch(d / initial, cy)) done = true
                }
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

/** Lays the child out at an exact pixel size, anchored top-left, even if the parent is smaller. */
private fun Modifier.fixedSizeTopStart(w: Int, h: Int): Modifier = layout { measurable, _ ->
    val p = measurable.measure(Constraints.fixed(max(w, 1), max(h, 1)))
    layout(max(w, 1), max(h, 1)) { p.place(0, 0) }
}

// ═══════════════════════════ App root ═══════════════════════════

@Composable
fun CarfApp(wantListen: Boolean, onListenHandled: () -> Unit) {
    CarfTheme { CarfAppContent(wantListen, onListenHandled) }
}

@Composable
private fun CarfAppContent(wantListen: Boolean, onListenHandled: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val capture = rememberPhotoCapture()
    val chat = remember { ChatState(ctx, scope, capture) }
    val cards = remember { stackCards() }

    // Launch animation: the app opens as the full-screen "Good Morning" card, then shrinks into the stack.
    var launching by rememberSaveable { mutableStateOf(true) }
    val progress = remember { Animatable(if (launching) 1f else 0f) }
    var openId by remember { mutableStateOf<String?>(if (launching) "hero" else null) }
    val rects = remember { mutableStateMapOf<String, Rect>() }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var showSettings by remember { mutableStateOf(false) }

    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) chat.startListening()
        else chat.entries.add(Msg("Allow microphone access to talk to me. You can still type.", false))
    }
    LaunchedEffect(Unit) { perms.launch(Actions.permissions() + arrayOf(Manifest.permission.RECORD_AUDIO)) }

    fun expand(id: String) {
        if (openId != null) return
        openId = id
        Haptics.click(ctx)
        scope.launch { progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 320f)) }
    }

    fun collapse() {
        scope.launch {
            chat.cancelVoice()
            Haptics.tick(ctx)
            progress.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 360f))
            openId = null
        }
    }

    fun run(cmd: String) {
        if (cmd.endsWith(" ")) chat.draft = cmd else chat.send(cmd)
        openId = "hero"
    }

    fun toggleMic() {
        Haptics.tick(ctx)
        when {
            chat.listening -> chat.stopListening()
            chat.hasMic() -> chat.startListening()
            else -> micPerm.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        if (launching) {
            delay(950)
            progress.animateTo(0f, tween(1150, easing = FastOutSlowInEasing))
            openId = null
            launching = false
        }
    }

    LaunchedEffect(wantListen) {
        if (wantListen) {
            while (launching) delay(100)
            if (openId == null) {
                openId = "hero"
                progress.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
            }
            if (chat.hasMic()) chat.startListening()
            onListenHandled()
        }
    }

    BackHandler(enabled = openId != null && !launching) { collapse() }

    val lightBars by remember { derivedStateOf { progress.value > 0.5f } }
    SideEffect {
        val w = (view.context as? Activity)?.window
        if (w != null) WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = lightBars
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        Modifier.fillMaxSize().background(Color(0xFF0B090C))
            .onSizeChanged { rootSize = it }
            .pinch { zoom, cy ->
                if (launching || progress.isRunning) {
                    false
                } else if (openId == null && zoom > 1.22f) {
                    val target = cards.filter { it.kind != CardKind.SETTINGS }
                        .minByOrNull { abs((rects[it.id]?.center?.y ?: -1e6f) - cy) }
                    if (target != null) { expand(target.id); true } else false
                } else if (openId != null && progress.value >= 0.99f && zoom < 0.8f) {
                    collapse(); true
                } else false
            }
    ) {
        // ───── list of cards ─────
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val q = progress.value.coerceIn(0f, 1f)
                alpha = (1f - q * 1.5f).coerceIn(0f, 1f)
                val s = 1f - 0.08f * q
                scaleX = s; scaleY = s
            }
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = topInset + 12.dp, bottom = bottomInset + 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { HeaderPill { if (openId == null && !launching) showSettings = true } }
                itemsIndexed(cards) { i, c ->
                    StackCardItem(
                        card = c, index = i, hidden = openId == c.id, progress = progress,
                        onPositioned = { rects[c.id] = it },
                        onClick = {
                            if (!launching && openId == null) {
                                if (c.kind == CardKind.SETTINGS) showSettings = true else expand(c.id)
                            }
                        }
                    )
                }
            }
        }

        // ───── the card that grows into a full screen (and shrinks back) ─────
        val id = openId
        val card = cards.firstOrNull { it.id == id }
        val r = if (id != null) rects[id] else null
        if (card != null && r != null && rootSize.width > 0) {
            val e = progress.value.coerceIn(0f, 1.03f)
            val fw = rootSize.width.toFloat()
            val fh = rootSize.height.toFloat()
            val left = lerp(r.left, 0f, e)
            val top = lerp(r.top, 0f, e)
            val w = lerp(r.width, fw, e)
            val h = lerp(r.height, fh, e)
            Box(
                Modifier.zIndex(1f)
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .layout { m, _ ->
                        val p = m.measure(Constraints.fixed(max(w.roundToInt(), 1), max(h.roundToInt(), 1)))
                        layout(p.width, p.height) { p.place(0, 0) }
                    }
                    .clip(RoundedCornerShape(lerp(30f, 0f, e.coerceAtMost(1f)).dp))
            ) {
                // 1) the card as it looks in the list, scaled up and fading out
                Box(
                    Modifier.fixedSizeTopStart(r.width.roundToInt(), r.height.roundToInt()).graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = w / r.width; scaleY = h / r.height
                        alpha = (1f - e * 2.2f).coerceIn(0f, 1f)
                    }
                ) { CardPreview(card) }
                // 2) the full screen, scaled down from full size and fading in
                Box(
                    Modifier.fixedSizeTopStart(rootSize.width, rootSize.height).graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = w / fw; scaleY = h / fh
                        alpha = ((e - 0.3f) / 0.6f).coerceIn(0f, 1f)
                    }
                ) {
                    when (card.kind) {
                        CardKind.HERO -> ChatDetail(chat, !launching, { toggleMic() }, { run(it) }, { showSettings = true })
                        CardKind.DEVICE -> DeviceDetail { run(it) }
                        else -> ShortcutDetail(card.title, itemsFor(card.kind)) { run(it) }
                    }
                }
            }
        }

        // block touches while the launch animation plays
        if (launching) {
            Box(
                Modifier.fillMaxSize().zIndex(2f)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
            )
        }
    }

    if (showSettings) SettingsDialog(onDismiss = { showSettings = false })
}

// ═══════════════════════════ List UI ═══════════════════════════

@Composable
private fun HeaderPill(onSettings: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(56.dp).glass(RoundedCornerShape(26.dp), dark = true),
        contentAlignment = Alignment.Center
    ) {
        Text("CarfBot", style = TextStyle(fontFamily = Sans, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEDE6EA)))
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)) {
            val ctx = LocalContext.current
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF3A363C))
                    .clickable { Haptics.tick(ctx); onSettings() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Settings, null, tint = Color(0xFFEDE6EA), modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun StackCardItem(
    card: StackCard, index: Int, hidden: Boolean, progress: Animatable<Float, *>,
    onPositioned: (Rect) -> Unit, onClick: () -> Unit
) {
    Box(
        Modifier.fillMaxWidth().height(card.height)
            .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
            .graphicsLayer {
                // staggered slide-in while the launch animation shrinks the hero card into the stack
                val q = progress.value.coerceIn(0f, 1f)
                val t = ((1f - q) * 1.7f - index * 0.14f).coerceIn(0f, 1f)
                alpha = if (hidden) 0f else t
                translationY = (1f - t) * 90.dp.toPx()
            }
            .clip(RoundedCornerShape(30.dp))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(30.dp))
            .clickable(onClick = onClick)
    ) { CardPreview(card) }
}

private fun paletteFor(kind: CardKind): Triple<Color, Color, Color> = when (kind) {
    CardKind.SHORTCUTS -> Triple(Color(0xFF1F1824), Color(0xFF6A4A82), Color(0xFFF2B4C6))
    CardKind.HERO -> Triple(Color(0xFF1A1418), Color(0xFFD36B9A), Color(0xFFE8C6D2))
    CardKind.FILES -> Triple(Color(0xFF241713), Color(0xFF9A4A22), Color(0xFFF4B9A6))
    CardKind.MUSIC -> Triple(Color(0xFF181A24), Color(0xFF3E5A9A), Color(0xFFBFD0F5))
    CardKind.CLIPBOARD -> Triple(Color(0xFF152019), Color(0xFF2F7A5A), Color(0xFFBFEBD3))
    CardKind.DEVICE -> Triple(Color(0xFF111A22), Color(0xFF2A7FB5), Color(0xFFBFE3F5))
    CardKind.SETTINGS -> Triple(Color(0xFF38353A), Color(0xFF38353A), Color.White)
}

@Composable
private fun CardPreview(card: StackCard) {
    val (base, glow, titleColor) = paletteFor(card.kind)
    Box(Modifier.fillMaxSize().background(base)) {
        if (card.kind != CardKind.SETTINGS) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width * 0.5f, size.height * 0.55f)
                val rad = size.width * 0.75f
                drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.75f), Color.Transparent), c, rad), rad, c)
            }
        }
        if (card.kind == CardKind.SETTINGS) {
            Text(
                card.title, modifier = Modifier.align(Alignment.Center),
                style = TextStyle(fontFamily = Sans, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = titleColor)
            )
        } else if (card.kind == CardKind.HERO) {
            Text(
                card.title.replace(" ", "\n"), modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
                style = TextStyle(
                    fontFamily = Sans, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, lineHeight = 33.sp,
                    letterSpacing = (-1).sp, color = titleColor, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            )
            HeroMiniCards(Modifier.align(Alignment.BottomCenter))
        } else {
            Text(
                card.title, modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
                style = TextStyle(fontFamily = Sans, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = titleColor)
            )
            if (card.kind == CardKind.DEVICE) DeviceMini(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp))
            else NoteMini(card.preview, Modifier.align(Alignment.BottomCenter).offset(y = 26.dp))
        }
    }
}

@Composable
private fun NoteMini(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.width(170.dp).height(84.dp).graphicsLayer { rotationZ = -2f }
            .clip(RoundedCornerShape(16.dp)).background(Color(0xFFF7F3EE)).padding(12.dp)
    ) {
        Text(
            "“$text”", maxLines = 3,
            style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 12.sp, lineHeight = 15.sp, color = Color(0xFF2B2430))
        )
    }
}

@Composable
private fun HeroMiniCards(modifier: Modifier) {
    val day = remember { SimpleDateFormat("EEE", Locale.getDefault()).format(Date()) }
    val date = remember { SimpleDateFormat("MMM d", Locale.getDefault()).format(Date()) }
    Row(modifier.offset(y = 26.dp), horizontalArrangement = Arrangement.spacedBy((-16).dp), verticalAlignment = Alignment.Bottom) {
        Box(
            Modifier.size(width = 92.dp, height = 104.dp).graphicsLayer { rotationZ = -5f }
                .clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F3EE)).padding(10.dp)
        ) {
            Text(
                "Say “open camera”…", maxLines = 4,
                style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 11.sp, lineHeight = 14.sp, color = Color(0xFF2B2430))
            )
        }
        Box(
            Modifier.size(width = 92.dp, height = 96.dp).graphicsLayer { rotationZ = 1f }
                .clip(RoundedCornerShape(14.dp)).background(Color.White).padding(10.dp)
        ) {
            Column {
                Text(day, style = TextStyle(fontFamily = Sans, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2430)))
                Text(date, style = TextStyle(fontFamily = Sans, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6C6672)))
            }
        }
        Box(
            Modifier.size(width = 96.dp, height = 112.dp).graphicsLayer { rotationZ = 5f }
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFF2A3BB), Color(0xFF9B7BE0)))),
            contentAlignment = Alignment.Center
        ) { Text("C", style = TextStyle(fontFamily = Sans, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White)) }
    }
}

// ═══════════════════════════ Detail screens (light peach) ═══════════════════════════

fun peachBrush() = Brush.verticalGradient(listOf(Color(0xFFF7E3DA), Color(0xFFF2B9CF), Color(0xFFF6E6EE)))

@Composable
fun Glow() {
    Canvas(Modifier.fillMaxSize()) {
        fun blob(c: Offset, rad: Float, color: Color) =
            drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), c, rad), rad, c)
        blob(Offset(size.width * 0.12f, size.height * 0.30f), size.width * 0.75f, Color(0xFFFFA874).copy(alpha = 0.65f))
        blob(Offset(size.width * 0.98f, size.height * 0.18f), size.width * 0.70f, Color(0xFFE56BC9).copy(alpha = 0.50f))
        blob(Offset(size.width * 0.50f, size.height * 0.78f), size.width * 0.70f, Color(0xFFFFC39A).copy(alpha = 0.45f))
    }
}

@Composable
fun BigTitle(text: String, size: Int, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier,
        style = TextStyle(
            fontFamily = Sans, fontSize = size.sp, fontWeight = FontWeight.Bold, lineHeight = (size * 0.92f).sp,
            letterSpacing = (-2).sp, color = Color.White.copy(alpha = 0.96f),
            shadow = Shadow(Color(0x66C94A93), Offset(0f, 8f), 36f)
        )
    )
}

@Composable
fun NoteCard(label: String, cmd: String, modifier: Modifier, onClick: () -> Unit) {
    ExpressiveSurface(onClick, modifier.height(100.dp), dark = false, rest = 24.dp) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Text(
                label, maxLines = 2,
                style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2430))
            )
            Spacer(Modifier.weight(1f))
            T(cmd.trim().take(34), 12, Color(0xFF6C6672), maxLines = 1)
        }
    }
}

@Composable
private fun ShortcutDetail(title: String, shortcuts: List<Pair<String, String>>, onRun: (String) -> Unit) {
    Box(Modifier.fillMaxSize().background(peachBrush())) {
        Glow()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(36.dp))
            BigTitle(title.replace(" & ", " &\n"), 52)
            Spacer(Modifier.height(22.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(shortcuts.chunked(2)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (l, c) -> NoteCard(l, c, Modifier.weight(1f)) { onRun(c) } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TipNote(tip: Tip, onRun: (String) -> Unit, modifier: Modifier) {
    Column(
        modifier.shadow(18.dp, RoundedCornerShape(24.dp), spotColor = Color(0x33B05A8A))
            .clip(RoundedCornerShape(24.dp)).background(Color.White).padding(16.dp)
    ) {
        Text(
            tip.body, maxLines = 4,
            style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 16.sp, lineHeight = 21.sp, color = Color(0xFF2B2430))
        )
        Spacer(Modifier.weight(1f))
        T(tip.title, 12, Color(0xFF9A93A6), maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tip.actions.take(2).forEach { (label, cmd) -> ActionPill(label) { onRun(cmd) } }
        }
    }
}

@Composable
private fun ChatDetail(
    chat: ChatState, interactive: Boolean, onMic: () -> Unit, onRun: (String) -> Unit, onSettings: () -> Unit
) {
    val ctx = LocalContext.current
    var tipIdx by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val conversation = chat.entries.isNotEmpty() || chat.partial.isNotEmpty()
    val ink = Color(0xFF2B2430)

    LaunchedEffect(chat.entries.size, chat.partial.isNotEmpty(), chat.thinking) {
        val n = listState.layoutInfo.totalItemsCount
        if (n > 0) listState.animateScrollToItem(max(0, n - 1))
    }

    Box(Modifier.fillMaxSize().background(peachBrush())) {
        Glow()
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .padding(horizontal = 20.dp)
        ) {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                if (conversation) BigTitle(heroTitle(), 28)
                Spacer(Modifier.weight(1f))
                if (conversation) {
                    GlassCircle(44.dp, { chat.clear() }) { Icon(Icons.Filled.Edit, null, tint = Pal.Slate, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(8.dp))
                }
                GlassCircle(44.dp, onSettings) { Icon(Icons.Outlined.Settings, null, tint = Pal.Slate, modifier = Modifier.size(20.dp)) }
            }

            if (!conversation) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val w = maxWidth
                    val hh = maxHeight
                    BigTitle(heroTitle().replace(" ", "\n"), 76, Modifier.align(Alignment.TopStart))
                    // swipeable tip note with action buttons
                    Box(
                        Modifier.align(Alignment.TopStart).offset(y = hh * 0.30f)
                            .width(w * 0.64f).height(214.dp).graphicsLayer { rotationZ = -3f }
                    ) {
                        SwipeCard(key = tipIdx, onDismiss = { tipIdx++ }, modifier = Modifier) {
                            TipNote(tips[tipIdx % tips.size], onRun, Modifier.fillMaxSize())
                        }
                    }
                    // avatar card
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(y = hh * 0.36f).size(w * 0.32f, w * 0.38f)
                            .graphicsLayer { rotationZ = 4f }
                            .clip(RoundedCornerShape(22.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFFF2A3BB), Color(0xFF9B7BE0)))),
                        contentAlignment = Alignment.Center
                    ) { Text("C", style = TextStyle(fontFamily = Sans, fontSize = 54.sp, fontWeight = FontWeight.Bold, color = Color.White)) }
                    // date card
                    val day = remember { SimpleDateFormat("EEEE", Locale.getDefault()).format(Date()) }
                    val date = remember { SimpleDateFormat("MMM d", Locale.getDefault()).format(Date()) }
                    Column(
                        Modifier.align(Alignment.TopStart).offset(x = w * 0.30f, y = hh * 0.68f)
                            .width(w * 0.46f).graphicsLayer { rotationZ = 2f }
                            .shadow(16.dp, RoundedCornerShape(22.dp), spotColor = Color(0x33B05A8A))
                            .clip(RoundedCornerShape(22.dp)).background(Color.White).padding(16.dp)
                    ) {
                        Text(day, style = TextStyle(fontFamily = Sans, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ink))
                        Text(date, style = TextStyle(fontFamily = Sans, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6C6672)))
                        T("Daily overview", 12, Color(0xFF9A93A6))
                    }
                }
            } else {
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(chat.entries) { NoteBubble(it.fromUser, it.text) }
                    if (chat.partial.isNotEmpty()) item { NoteBubble(true, chat.partial) }
                    if (chat.thinking && chat.entries.lastOrNull()?.fromUser != false) item { NoteBubble(false, "…") }
                }
            }

            // input bar
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp).height(58.dp)
                    .glass(CircleShape, dark = false).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Add, null, tint = Pal.Slate,
                    modifier = Modifier.size(26.dp).clickable(enabled = interactive) { Haptics.tick(ctx); chat.draft = "find file " }
                )
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = chat.draft, onValueChange = { chat.draft = it }, singleLine = true, enabled = interactive,
                    textStyle = TextStyle(fontFamily = Sans, fontSize = 17.sp, color = ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { chat.send(chat.draft) }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (chat.draft.isEmpty()) T(if (chat.listening) "Listening…" else "Ask CarfBot…", 17, Color(0xFF9A93A6))
                        inner()
                    }
                )
                Spacer(Modifier.width(10.dp))
                if (chat.draft.isNotBlank()) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send, null, tint = Pal.Slate,
                        modifier = Modifier.size(26.dp).clickable(enabled = interactive) { chat.send(chat.draft) }
                    )
                } else {
                    Icon(
                        Icons.Filled.Mic, null,
                        tint = if (chat.listening) Color(0xFFE0457B) else Pal.Slate,
                        modifier = Modifier.size((26f + chat.level * 8f).dp).clickable(enabled = interactive) { onMic() }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteBubble(user: Boolean, text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (user) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            Modifier.fillMaxWidth(0.86f)
                .then(
                    if (user) Modifier.clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.95f))
                    else Modifier.glass(RoundedCornerShape(22.dp), dark = false)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text,
                style = TextStyle(
                    fontFamily = if (user) Sans else FontFamily.Serif, fontSize = 17.sp, lineHeight = 23.sp,
                    color = Color(0xFF2B2430)
                )
            )
        }
    }
}
