package com.carfbot.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

data class Msg(val text: String, val fromUser: Boolean)
enum class Screen { Home, Chat }
data class Tip(val title: String, val sub: String, val heading: String, val body: String, val command: String)

val tips = listOf(
    Tip("Open any app", "Today, 9:41 AM", "Today’s command",
        "Say “open camera” and CarfBot launches it for you, no hunting through your app drawer.", "open camera"),
    Tip("Call a contact", "Contacts", "Reach anyone",
        "Say “call Mom” and I’ll find her in your contacts and get the dialer ready.", "call mom"),
    Tip("Play a song", "Music", "Your soundtrack",
        "Say “play Blinding Lights” and I’ll play it from your phone or your music app.", "play blinding lights"),
    Tip("Find a file", "Files", "Find it fast",
        "Say “find photo beach” or “find document invoice” and I’ll open the match.", "find photo beach")
)

@Composable
fun CarfApp(wantListen: Boolean, onListenHandled: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.Home) }
    val msgs = remember { mutableStateListOf<Msg>() }
    var draft by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }
    var tip by remember { mutableIntStateOf(0) }
    var actions by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) { perms.launch(Actions.permissions()) }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        screen = Screen.Chat
        msgs.add(Msg(t, true))
        draft = ""
        val local = Actions.handle(ctx, t)
        if (local != null) {
            actions++
            msgs.add(Msg(local, false))
        } else {
            thinking = true
            scope.launch {
                msgs.add(Msg(AiClient.ask(ctx, t), false))
                thinking = false
            }
        }
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        listening = false
        val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && !said.isNullOrBlank()) send(said)
    }

    fun startListening() {
        screen = Screen.Chat
        listening = true
        try {
            speech.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a command")
            )
        } catch (e: Exception) {
            listening = false
            msgs.add(Msg("Voice input isn’t available on this device. You can type instead.", false))
        }
    }

    LaunchedEffect(wantListen) { if (wantListen) { startListening(); onListenHandled() } }
    BackHandler(enabled = screen == Screen.Chat) { screen = Screen.Home }

    when (screen) {
        Screen.Home -> HomeScreen(
            tip = tip,
            onPrev = { tip = (tip + tips.size - 1) % tips.size },
            onNext = { tip = (tip + 1) % tips.size },
            onChip = { draft = it; screen = Screen.Chat },
            onTip = { draft = tips[tip].command; screen = Screen.Chat },
            onAsk = { screen = Screen.Chat },
            counts = Triple(0, msgs.size, actions)
        )
        Screen.Chat -> ChatScreen(
            msgs = msgs, listening = listening, thinking = thinking, draft = draft,
            onDraft = { draft = it }, onSend = { send(draft) }, onMic = { startListening() },
            onBack = { screen = Screen.Home }, onSettings = { showSettings = true },
            onShortcut = { draft = it }
        )
    }

    if (showSettings) SettingsDialog(onDismiss = { showSettings = false })
}

// ───────────────────────── Home (left phone) ─────────────────────────

@Composable
fun HomeScreen(
    tip: Int, onPrev: () -> Unit, onNext: () -> Unit, onChip: (String) -> Unit,
    onTip: () -> Unit, onAsk: () -> Unit, counts: Triple<Int, Int, Int>
) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Pal.PinkTop, Pal.PinkMid, Pal.Cream, Pal.Lav))
        )
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(44.dp))
            // Blurred hero headline
            T("Just ask.", 54, Color.White, FontWeight.Bold, lineHeight = 56,
                modifier = Modifier.blur(9.dp, BlurredEdgeTreatment.Unbounded))
            Column(Modifier.padding(start = 64.dp)) {
                T("CarfBot opens", 44, Color.White, FontWeight.Bold, lineHeight = 46,
                    modifier = Modifier.blur(1.2.dp, BlurredEdgeTreatment.Unbounded))
                T("it for you.", 44, Color.White, FontWeight.Bold, lineHeight = 46,
                    modifier = Modifier.blur(2.dp, BlurredEdgeTreatment.Unbounded))
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip(Icons.Outlined.Apps, "Apps") { onChip("open ") }
                Chip(Icons.Outlined.Description, "Files") { onChip("find file ") }
                Chip(Icons.Outlined.MusicNote, "Songs") { onChip("play ") }
                Chip(Icons.Outlined.Person, "Contacts") { onChip("call ") }
            }
            Spacer(Modifier.height(26.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                BackCard(top = 0.dp, hPad = 44.dp, alpha = 0.5f)
                BackCard(top = 24.dp, hPad = 22.dp, alpha = 0.75f)
                FrontCard(tips[tip], counts, onTip, Modifier.padding(top = 52.dp, bottom = 0.dp))
            }
            Row(
                Modifier.fillMaxWidth().offset(y = (-28).dp).zIndex(2f),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                GlassCircle(56.dp, onPrev) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Pal.Slate) }
                Spacer(Modifier.width(6.dp))
                Row(
                    Modifier.width(150.dp).height(56.dp).shadow(8.dp, CircleShape, spotColor = Color(0x33B07A99))
                        .clip(CircleShape).background(Pal.Pill).clickable(onClick = onAsk),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Mic, null, tint = Pal.Slate, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    T("Ask CarfBot", 17, Pal.Slate, FontWeight.Medium)
                }
                Spacer(Modifier.width(6.dp))
                GlassCircle(56.dp, onNext) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Pal.Slate) }
            }
        }
    }
}

@Composable
private fun Chip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.30f))
            .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        T(label, 15, Color.White.copy(alpha = 0.95f))
    }
}

@Composable
private fun BackCard(top: Dp, hPad: Dp, alpha: Float) {
    Box(
        Modifier.padding(top = top, start = hPad, end = hPad).fillMaxWidth().height(120.dp)
            .alpha(alpha).clip(RoundedCornerShape(28.dp)).background(Color(0xFFF7EFEB))
    )
}

@Composable
private fun FrontCard(t: Tip, counts: Triple<Int, Int, Int>, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().shadow(14.dp, RoundedCornerShape(32.dp), spotColor = Color(0x33B07A99))
            .clip(RoundedCornerShape(32.dp)).background(Pal.CardGrey).clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Pal.PinkTop, Color(0xFFB8A5E0)))),
                contentAlignment = Alignment.Center
            ) { T("C", 24, Color.White, FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(t.title, 16, Pal.Slate, FontWeight.SemiBold)
                T(t.sub, 14, Pal.Blue)
            }
            GlassCircle(46.dp, {}) { Icon(Icons.Outlined.Share, null, tint = Pal.Slate, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.height(22.dp))
        T(t.heading, 25, Pal.Slate, FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        T(t.body, 17, Pal.Blue, maxLines = 3, lineHeight = 23)
        Box(Modifier.weight(1f).fillMaxWidth()) { Flower(Modifier.fillMaxSize(), blurDp = 16.dp) }
        Row(Modifier.padding(bottom = 30.dp), verticalAlignment = Alignment.CenterVertically) {
            FooterStat(Icons.Outlined.FavoriteBorder, counts.first)
            Spacer(Modifier.width(18.dp))
            FooterStat(Icons.Outlined.ChatBubbleOutline, counts.second)
            Spacer(Modifier.width(18.dp))
            FooterStat(Icons.AutoMirrored.Outlined.Send, counts.third)
        }
    }
}

@Composable
private fun FooterStat(icon: ImageVector, n: Int) {
    Icon(icon, null, tint = Pal.Blue, modifier = Modifier.size(19.dp))
    Spacer(Modifier.width(5.dp))
    T("$n", 14, Pal.Blue)
}

@Composable
fun Flower(modifier: Modifier, blurDp: Dp) {
    Canvas(modifier.blur(blurDp, BlurredEdgeTreatment.Unbounded)) {
        val c = Offset(size.width * 0.5f, size.height * 0.42f)
        val r = size.minDimension * 0.26f
        fun petal(dx: Float, dy: Float, col: Color) =
            drawCircle(col, radius = r, center = Offset(c.x + dx * r, c.y + dy * r))
        petal(-0.8f, -0.3f, Color(0xFFCFA3C9))
        petal(0.8f, -0.3f, Color(0xFFD9A8CE))
        petal(0f, 0.6f, Color(0xFFB98BB8))
        petal(0f, -0.1f, Color(0xFFF1D8E5))
        drawRect(
            Color(0xFFD4DEC9), topLeft = Offset(c.x - r * 0.12f, c.y + r),
            size = Size(r * 0.24f, size.height)
        )
    }
}

// ───────────────────────── Chat (right phone) ─────────────────────────

@Composable
fun ChatScreen(
    msgs: List<Msg>, listening: Boolean, thinking: Boolean, draft: String,
    onDraft: (String) -> Unit, onSend: () -> Unit, onMic: () -> Unit,
    onBack: () -> Unit, onSettings: () -> Unit, onShortcut: (String) -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFE9E3F4), Color(0xFFF3E4EE), Color(0xFFDCD7EE)))
        )
    ) {
        Flower(Modifier.fillMaxWidth().height(340.dp).alpha(0.85f), blurDp = 28.dp)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                GlassCircle(52.dp, onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Pal.Slate) }
                Row(
                    Modifier.clip(CircleShape).background(Pal.Pill).padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Pal.Slate))
                    Spacer(Modifier.width(6.dp))
                    T("CarfBot", 14, Pal.Slate, FontWeight.Medium)
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = Pal.Slate, modifier = Modifier.size(18.dp))
                }
                GlassCircle(52.dp, onSettings) { Icon(Icons.Outlined.Settings, null, tint = Pal.Slate) }
            }
            Spacer(Modifier.height(36.dp))
            T("Hi, how can I help?", 17, Color.White, FontWeight.SemiBold, align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            VoicePill(listening, onMic)
            Spacer(Modifier.height(10.dp))
            T(
                if (listening) "Listening…" else "Tap the mic and say: “open camera”",
                13, Color.White.copy(alpha = 0.92f), align = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))

            if (msgs.isEmpty()) {
                ShortcutGrid(onShortcut, Modifier.weight(1f))
            } else {
                val state = rememberLazyListState()
                LaunchedEffect(msgs.size, thinking) { state.animateScrollToItem(msgs.size) }
                LazyColumn(
                    state = state, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(msgs) { Bubble(it) }
                    item { if (thinking) Bubble(Msg("Thinking…", false)) else Spacer(Modifier.height(4.dp)) }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier.weight(1f).height(56.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.85f)).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Search, null, tint = Pal.Slate)
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = draft, onValueChange = onDraft, singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Sans, fontSize = 17.sp, color = Pal.Slate),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (draft.isEmpty()) T("Ask CarfBot…", 17, Pal.Slate.copy(alpha = 0.8f))
                            inner()
                        }
                    )
                }
                Spacer(Modifier.width(8.dp))
                GlassCircle(52.dp, onMic) { Icon(Icons.Filled.Mic, null, tint = Pal.Slate) }
                Spacer(Modifier.width(8.dp))
                GlassCircle(52.dp, onSend) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = Pal.Slate) }
            }
        }
    }
}

@Composable
private fun VoicePill(listening: Boolean, onMic: () -> Unit) {
    var secs by remember { mutableIntStateOf(0) }
    LaunchedEffect(listening) {
        secs = 0
        while (listening) { delay(1000); secs++ }
    }
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        0f, 6.2832f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "p"
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        GlassCircle(58.dp, onMic) { Icon(Icons.Filled.Mic, null, tint = Pal.Slate, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier.weight(1f).height(58.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.78f))
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(Modifier.weight(1f).height(30.dp)) {
                val n = 38
                val step = size.width / n
                for (i in 0 until n) {
                    val base = (sin(i * 0.9) + sin(i * 0.37 + 1.3) + 2.0) / 4.0
                    val live = if (listening) 0.35 * sin(phase + i * 0.5) else 0.0
                    val h = (size.height * (0.18 + 0.82 * (base + live).coerceIn(0.05, 1.0))).toFloat()
                    drawRect(
                        Pal.Slate, topLeft = Offset(i * step, (size.height - h) / 2f),
                        size = Size(step * 0.45f, h)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            T("%02d:%02d".format(secs / 60, secs % 60), 14, Pal.Slate)
        }
    }
}

@Composable
private fun ShortcutGrid(onShortcut: (String) -> Unit, modifier: Modifier) {
    val date = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date()) }
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ShortcutCard("Apps", date, "open camera", true, Modifier.weight(1f), onShortcut)
            ShortcutCard("Contacts", date, "call mom", false, Modifier.weight(1f), onShortcut)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ShortcutCard("Songs", date, "play blinding lights", false, Modifier.weight(1f), onShortcut)
            ShortcutCard("Files", date, "find photo beach", true, Modifier.weight(1f), onShortcut)
        }
    }
}

@Composable
private fun ShortcutCard(
    title: String, date: String, cmd: String, blue: Boolean, modifier: Modifier, onShortcut: (String) -> Unit
) {
    val bg = if (blue) Modifier.background(Brush.verticalGradient(listOf(Color(0xFF9DB3C6), Color(0xFF8FA6BC))))
    else Modifier.background(Color.White.copy(alpha = 0.95f))
    val fg = if (blue) Color.White else Pal.Slate
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(26.dp)).then(bg)
            .clickable { onShortcut(cmd) }.padding(16.dp)
    ) {
        T(title, 18, fg, FontWeight.SemiBold)
        T(date, 12, fg.copy(alpha = 0.7f))
        Spacer(Modifier.weight(1f))
        T("“$cmd”", 15, fg, lineHeight = 20)
    }
}

@Composable
private fun Bubble(m: Msg) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(24.dp))
                .background(if (m.fromUser) Color.White.copy(alpha = 0.95f) else Color(0xFF8FA9BE).copy(alpha = 0.80f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) { T(m.text, 16, if (m.fromUser) Pal.Slate else Color.White, lineHeight = 22) }
    }
}

@Composable
private fun SettingsDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf(Prefs.apiKey(ctx)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Cream,
        title = { T("CarfBot settings", 20, Pal.Slate, FontWeight.SemiBold) },
        text = {
            Column {
                T("Anthropic API key (for AI chat)", 14, Pal.Blue)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = key, onValueChange = { key = it }, singleLine = true,
                    placeholder = { T("sk-ant-…", 14, Pal.Blue) })
                Spacer(Modifier.height(14.dp))
                TextButton(onClick = { Actions.openAssistantSettings(ctx) }) {
                    T("Set CarfBot as default assistant", 15, Pal.Slate, FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { Prefs.setApiKey(ctx, key); onDismiss() }) { T("Save", 16, Pal.Slate, FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { T("Cancel", 16, Pal.Blue) } }
    )
}
