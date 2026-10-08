package com.carfbot.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/** The overlay that appears when CarfBot is the default assistant and the user holds the power button. */
class AssistantActivity : ComponentActivity() {
    private var ping by mutableIntStateOf(0)

    /** Power button held again while the overlay is already open: restart listening. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ping++
    }

    override fun onDestroy() {
        try { CarfSession.current?.hide() } catch (e: Exception) { }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        // Full screen, including the camera cutout area
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        // Frosted blur of whatever is behind the overlay (Android 12+ when the device allows it)
        var blurOk = false
        if (Build.VERSION.SDK_INT >= 31 && windowManager.isCrossWindowBlurEnabled) {
            blurOk = true
            window.setBackgroundBlurRadius((48 * resources.displayMetrics.density).toInt())
        }
        setContent {
            AssistantScreen(blurOk = blurOk, ping = ping, onClose = { finish(); overridePendingTransition(0, 0) })
        }
    }
}

// ───────────────────────── Voice (live level + partial text) ─────────────────────────

class VoiceController(private val ctx: Context) {
    var onLevel: (Float) -> Unit = {}
    var onPartial: (String) -> Unit = {}
    var onFinal: (String) -> Unit = {}
    var onError: (String) -> Unit = {}
    var onState: (Boolean) -> Unit = {}

    private var rec: SpeechRecognizer? = null
    private var queue: ArrayDeque<ComponentName?> = ArrayDeque()
    private var session = 0

    /**
     * When CarfBot is the default assistant, Android makes CarfBot's own (stub) recognition service the
     * system default. So we try the other installed recognizers one by one (Google first), and the
     * system default last.
     */
    private fun candidates(): List<ComponentName?> {
        val others = try {
            ctx.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
                .map { it.serviceInfo }
                .filter { it.packageName != ctx.packageName }
                .sortedBy {
                    when {
                        it.packageName == "com.google.android.googlequicksearchbox" -> 0
                        it.packageName.contains("google") -> 1
                        else -> 2
                    }
                }
                .map { ComponentName(it.packageName, it.name) }
        } catch (e: Exception) { emptyList() }
        return others + listOf<ComponentName?>(null)
    }

    private fun message(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "I didn’t catch that. Tap the mic to try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Allow microphone access to talk to me. You can still type."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Voice recognition needs an internet connection. You can still type."
        SpeechRecognizer.ERROR_AUDIO ->
            "I couldn’t use the microphone. Close other apps that use it and try again."
        else -> "Voice input isn’t working (code $error). Make sure the Google app is installed, or type instead."
    }

    fun start() {
        cancel()
        queue = ArrayDeque(candidates())
        next()
    }

    private fun next() {
        if (queue.isEmpty()) {
            finishState()
            onError("Voice input isn’t available on this device. You can type instead.")
            return
        }
        val comp = queue.removeFirst()
        val my = ++session
        try {
            val r = if (comp != null) SpeechRecognizer.createSpeechRecognizer(ctx, comp)
            else SpeechRecognizer.createSpeechRecognizer(ctx)
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    if (my == session) this@VoiceController.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { if (my == session) this@VoiceController.onLevel(0f) }
                override fun onError(error: Int) {
                    if (my != session) return
                    destroyQuiet()
                    // client / server / busy / too many requests / disconnected: try the next recognizer
                    val retry = error == 5 || error == 4 || error == 8 || error == 10 || error == 11
                    if (retry && queue.isNotEmpty()) { next(); return }
                    finishState()
                    this@VoiceController.onError(message(error))
                }
                override fun onResults(results: Bundle?) {
                    if (my != session) return
                    destroyQuiet()
                    finishState()
                    val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (t.isNullOrBlank()) this@VoiceController.onError(message(SpeechRecognizer.ERROR_NO_MATCH))
                    else this@VoiceController.onFinal(t)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (my != session) return
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        ?.let { this@VoiceController.onPartial(it) }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            r.startListening(i)
            rec = r
            Haptics.tick(ctx)
            onState(true)
        } catch (e: Exception) {
            destroyQuiet()
            next()
        }
    }

    /** Stop listening and let the recognizer deliver the final text. */
    fun finish() { try { rec?.stopListening() } catch (e: Exception) { } }

    /** Drop everything without a result. */
    fun cancel() {
        session++
        queue.clear()
        destroyQuiet()
        finishState()
    }

    private fun destroyQuiet() {
        try { rec?.destroy() } catch (e: Exception) { }
        rec = null
    }

    private fun finishState() {
        onLevel(0f)
        onState(false)
    }
}

// ───────────────────────── Screen ─────────────────────────

private val Gray = Color(0xFF8E8E93)
private val Panel = Color(0xFF2C2C2E)

enum class Mode { Idle, Listening, Thinking }

@Composable
fun AssistantScreen(blurOk: Boolean, ping: Int, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries = remember { mutableStateListOf<Msg>() }
    var partial by remember { mutableStateOf("") }
    var level by remember { mutableFloatStateOf(0f) }
    var mode by remember { mutableStateOf(Mode.Idle) }
    var draft by remember { mutableStateOf("") }
    val appear = remember { Animatable(0f) }
    val listState = rememberLazyListState()
    val voice = remember { VoiceController(ctx) }

    fun close() { scope.launch { appear.animateTo(0f, tween(260)); onClose() } }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        partial = ""; draft = ""
        Haptics.tick(ctx)
        entries.add(Msg(t, true))
        val local = Actions.handle(ctx, t)
        if (local != null) {
            entries.add(Msg(local, false))
            mode = Mode.Idle
            if (Actions.launched) scope.launch { delay(900); close() } else Haptics.click(ctx)
        } else {
            mode = Mode.Thinking
            scope.launch {
                entries.add(Msg(AiClient.ask(ctx, t), false))
                mode = Mode.Idle
                Haptics.click(ctx)
            }
        }
    }

    DisposableEffect(Unit) {
        voice.onLevel = { level = it }
        voice.onPartial = { partial = it }
        voice.onFinal = { send(it) }
        voice.onError = { msg -> partial = ""; mode = Mode.Idle; Haptics.reject(ctx); entries.add(Msg(msg, false)) }
        voice.onState = { on -> if (on) mode = Mode.Listening else if (mode == Mode.Listening) mode = Mode.Idle }
        onDispose { voice.cancel() }
    }

    fun hasMic() = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun startListening() { partial = ""; voice.start() }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startListening()
        else entries.add(Msg("Allow microphone access to talk to me. You can still type.", false))
    }

    fun toggleMic() {
        Haptics.tick(ctx)
        when {
            mode == Mode.Listening -> voice.finish()
            hasMic() -> startListening()
            else -> permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        Haptics.summon(ctx)
        launch { appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) }
        delay(320)
        if (hasMic()) startListening() else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(ping) {
        if (ping > 0) {
            Haptics.summon(ctx)
            if (hasMic()) startListening() else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    BackHandler { close() }

    LaunchedEffect(entries.size, partial.isNotEmpty()) {
        val n = listState.layoutInfo.totalItemsCount
        if (n > 0) listState.animateScrollToItem(max(0, n - 1))
    }
    val atEnd by remember { derivedStateOf { !listState.canScrollForward } }
    val a = appear.value.coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize()) {
        // Panel
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    translationY = (1f - appear.value) * size.height * 0.12f
                    alpha = a
                }
                .background(
                    // Translucent so the system blur shows through (more opaque when blur is unavailable)
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF2A2A2E).copy(alpha = if (blurOk) 0.62f else 0.95f),
                            Color(0xFF0B0B0C).copy(alpha = if (blurOk) 0.70f else 0.97f),
                            Color(0xFF0A0A0B).copy(alpha = if (blurOk) 0.72f else 0.97f),
                            Color(0xFF1E1E20).copy(alpha = if (blurOk) 0.66f else 0.95f)
                        )
                    )
                )
        ) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    DarkCircle(50.dp, {
                        ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        close()
                    }) { Icon(Icons.Filled.Menu, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                    T(
                        when (mode) {
                            Mode.Listening -> "Listening"; Mode.Thinking -> "Thinking"; else -> "CarfBot"
                        },
                        20, Color.White, FontWeight.SemiBold, align = TextAlign.Center, modifier = Modifier.weight(1f)
                    )
                    DarkCircle(50.dp, {
                        voice.cancel(); entries.clear(); partial = ""; mode = Mode.Idle
                    }) { Icon(Icons.Filled.Edit, null, tint = Color.White, modifier = Modifier.size(21.dp)) }
                }
                Spacer(Modifier.height(34.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Orb(level, mode) }
                Spacer(Modifier.height(56.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    DarkCircle(48.dp, { close() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    T("Conversation", 19, Color.White, FontWeight.Medium)
                }
                Spacer(Modifier.height(20.dp))
                LazyColumn(
                    state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (entries.isEmpty() && partial.isEmpty()) {
                        item {
                            T("Try “open camera”, “call Mom”, “play a song” or “find photo beach”.",
                                17, Gray, lineHeight = 24)
                        }
                    }
                    items(entries) { TranscriptItem(it.fromUser, it.text) }
                    if (partial.isNotEmpty()) item { TranscriptItem(true, partial) }
                }
                Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                    if (!atEnd) {
                        DarkCircle(46.dp, {
                            scope.launch { listState.animateScrollToItem(max(0, listState.layoutInfo.totalItemsCount - 1)) }
                        }) { Icon(Icons.Filled.ArrowDownward, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(60.dp).clip(CircleShape).background(Panel.copy(alpha = 0.85f))
                        .padding(start = 24.dp, end = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = draft, onValueChange = { draft = it }, singleLine = true,
                        textStyle = TextStyle(fontFamily = Sans, fontSize = 18.sp, color = Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send(draft) }),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (draft.isEmpty()) T("Continue conversation", 18, Gray)
                            inner()
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    if (draft.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White,
                            modifier = Modifier.size(24.dp).clickable { send(draft) })
                    } else {
                        Icon(Icons.Filled.Mic, null,
                            tint = if (mode == Mode.Listening) Color.White else Gray,
                            modifier = Modifier.size(26.dp).clickable { toggleMic() })
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
        // Apple Intelligence style glow around the screen edge
        GlowBorder(Modifier.fillMaxSize(), a, mode, level)
    }
}

@Composable
private fun TranscriptItem(user: Boolean, text: String) {
    Column {
        T(if (user) "You:" else "CarfBot:", 20, Color.White, FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        T("“$text”", 17, Gray, lineHeight = 24)
    }
}

@Composable
private fun DarkCircle(size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    Box(
        Modifier.size(size).clip(CircleShape).background(Panel)
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .clickable { Haptics.tick(ctx); onClick() },
        contentAlignment = Alignment.Center
    ) { content() }
}

// ───────────────────────── Orb ─────────────────────────

@Composable
private fun Orb(level: Float, mode: Mode) {
    val t = rememberInfiniteTransition(label = "orb")
    val phase by t.animateFloat(
        0f, 6.2832f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "phase"
    )
    val breathe by t.animateFloat(
        1f, 1.05f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe"
    )
    val smooth by animateFloatAsState(level, tween(90), label = "level")

    Box(
        Modifier.size(150.dp).scale(breathe)
            .shadow(26.dp, CircleShape, spotColor = Color(0x55FFFFFF), ambientColor = Color(0x22FFFFFF))
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF454548), Color(0xFF121213))))
            .border(1.5.dp, Brush.verticalGradient(listOf(Color(0x77FFFFFF), Color(0x11FFFFFF))), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(width = 96.dp, height = 54.dp)) {
            val n = 21
            val gap = size.width / n
            val bar = 3.dp.toPx()
            for (i in 0 until n) {
                val x = (i - n / 2).toFloat()
                val bell = exp(-(x * x) / 28f)
                val amp = when (mode) {
                    Mode.Listening -> 0.18f + 0.82f * smooth
                    Mode.Thinking -> 0.35f + 0.2f * sin(phase * 2f + i * 0.6f)
                    Mode.Idle -> 0.14f
                }
                val wobble = 0.6f + 0.4f * abs(sin(phase + i * 0.7f))
                val h = size.height * (0.10f + bell * amp * wobble).coerceIn(0.08f, 1f)
                drawRoundRect(
                    Color.White, topLeft = Offset(i * gap + (gap - bar) / 2f, (size.height - h) / 2f),
                    size = Size(bar, h), cornerRadius = CornerRadius(bar / 2f)
                )
            }
        }
    }
}

// ───────────────────────── Glow border ─────────────────────────

@Composable
private fun GlowBorder(modifier: Modifier, appear: Float, mode: Mode, level: Float) {
    val t = rememberInfiniteTransition(label = "glow")
    val angle by t.animateFloat(
        0f, 360f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "angle"
    )
    val pulse by t.animateFloat(
        0.75f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )
    val colors = remember {
        listOf(
            Color(0xFFFF6BA8), Color(0xFFFFA24C), Color(0xFFB36BFF),
            Color(0xFF4C8DFF), Color(0xFF3FE0C8), Color(0xFFFF6BA8)
        )
    }
    val strength = appear * when (mode) {
        Mode.Listening -> (0.7f + 0.3f * level) * pulse
        Mode.Thinking -> pulse
        Mode.Idle -> 0.5f
    }
    Box(modifier) {
        GlowLayer(Modifier.fillMaxSize().blur(26.dp), 30.dp, angle, colors, strength * 0.9f)
        GlowLayer(Modifier.fillMaxSize().blur(8.dp), 10.dp, angle + 40f, colors, strength * 0.8f)
        GlowLayer(Modifier.fillMaxSize(), 3.dp, angle + 90f, colors, strength)
    }
}

@Composable
private fun GlowLayer(modifier: Modifier, stroke: Dp, angle: Float, colors: List<Color>, alpha: Float) {
    Canvas(modifier) {
        val sw = stroke.toPx()
        val shader = android.graphics.SweepGradient(
            center.x, center.y, colors.map { it.toArgb() }.toIntArray(), null
        )
        val m = android.graphics.Matrix().apply { postRotate(angle, center.x, center.y) }
        shader.setLocalMatrix(m)
        drawRoundRect(
            brush = ShaderBrush(shader),
            topLeft = Offset(sw / 2f, sw / 2f),
            size = Size(size.width - sw, size.height - sw),
            cornerRadius = CornerRadius(44.dp.toPx()),
            style = Stroke(sw),
            alpha = alpha.coerceIn(0f, 1f)
        )
    }
}
