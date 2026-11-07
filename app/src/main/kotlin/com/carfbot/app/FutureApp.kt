package com.carfbot.app

import android.Manifest
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay

enum class FScreen { Home, Chat, Studio, Tools, Device }

/** Top-to-bottom order of the side bar. */
private val navOrder = listOf(
    FScreen.Home to NavGlyph.Sparkle, FScreen.Tools to NavGlyph.Calendar, FScreen.Chat to NavGlyph.Chat,
    FScreen.Studio to NavGlyph.Folder, FScreen.Device to NavGlyph.Chart
)

/** The Futuristic Blur shell: pastel home, AI Studio, tools and device screens, with "focus pull" transitions. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun FutureApp(wantListen: Boolean, onListenHandled: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val capture = rememberPhotoCapture()
    val chat = remember { ChatState(ctx, scope, capture) }
    var screen by remember { mutableStateOf(FScreen.Home) }
    var showSettings by remember { mutableStateOf(false) }
    var splash by rememberSaveable { mutableStateOf(true) }
    val splashP = remember { Animatable(if (splash) 0f else 1f) }

    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) chat.startListening()
        else chat.entries.add(Msg("Allow microphone access to talk to me. You can still type.", false))
    }
    LaunchedEffect(Unit) { perms.launch(Actions.permissions() + arrayOf(Manifest.permission.RECORD_AUDIO)) }

    SideEffect {
        val w = (view.context as? Activity)?.window
        if (w != null) WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = true
    }

    LaunchedEffect(Unit) {
        if (splash) {
            splashP.animateTo(1f, tween(1700, easing = LinearEasing))
            splash = false
        }
    }

    fun toggleMic() {
        Haptics.tick(ctx)
        when {
            chat.listening -> chat.stopListening()
            chat.hasMic() -> chat.startListening()
            else -> micPerm.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun run(cmd: String) {
        if (cmd.endsWith(" ")) chat.draft = cmd else chat.send(cmd)
        screen = FScreen.Chat
    }

    LaunchedEffect(wantListen) {
        if (wantListen) {
            while (splash) delay(100)
            screen = FScreen.Chat
            if (chat.hasMic()) chat.startListening()
            onListenHandled()
        }
    }

    BackHandler(enabled = screen != FScreen.Home && !splash) {
        chat.cancelVoice()
        screen = FScreen.Home
    }

    val toolbarVisible = screen == FScreen.Home || screen == FScreen.Tools || screen == FScreen.Device
    val navPad by animateDpAsState(if (toolbarVisible && !splash) 84.dp else 0.dp, tween(400), label = "navPad")

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(Look.skyColors()))) {
        FutureGlow()

        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (fadeIn(tween(480)) + scaleIn(initialScale = 0.94f, animationSpec = tween(520, easing = FastOutSlowInEasing))) togetherWith
                    (fadeOut(tween(260)) + scaleOut(targetScale = 1.05f, animationSpec = tween(300)))
            },
            label = "screens"
        ) { s ->
            // the new screen starts out of focus and sharpens as it arrives
            val focusBlur by transition.animateDp(label = "focus") { st ->
                if (st == EnterExitState.Visible) 0.dp else 24.dp
            }
            Box(Modifier.fillMaxSize().padding(end = navPad).blur(focusBlur, BlurredEdgeTreatment.Unbounded)) {
                when (s) {
                    FScreen.Home -> FutureHome(
                        chat = chat, enter = !splash,
                        onOpen = { screen = it }, onRun = { run(it) }, onSettings = { showSettings = true }
                    )
                    FScreen.Chat -> ChatDetail(
                        chat, true, { toggleMic() }, { run(it) }, { showSettings = true },
                        { chat.cancelVoice(); screen = FScreen.Home }
                    )
                    FScreen.Studio -> StudioScreen(onBack = { screen = FScreen.Home })
                    FScreen.Tools -> Box(Modifier.fillMaxSize()) { ToolsScreen { run(it) } }
                    FScreen.Device -> Box(Modifier.fillMaxSize()) { DeviceDetail { run(it) } }
                }
            }
        }

        AnimatedVisibility(
            visible = toolbarVisible && !splash,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
            enter = fadeIn(tween(400)) + slideInHorizontally(tween(450)) { it },
            exit = fadeOut(tween(200)) + slideOutHorizontally(tween(250)) { it }
        ) {
            SideNav(
                items = navOrder.map { it.second },
                selected = navOrder.indexOfFirst { it.first == screen }.coerceAtLeast(0),
                onSelect = { screen = navOrder[it].first },
                light = Look.navLight
            )
        }

        if (splash) SplashFocus(splashP.value)
    }

    if (showSettings) SettingsDialog(onDismiss = { showSettings = false })
}

/** Launch animation: the CarfBot wordmark pulls into focus, holds, then dissolves into the home screen. */
@Composable
private fun SplashFocus(p: Float) {
    val inP = (p / 0.45f).coerceIn(0f, 1f)
    val outP = ((p - 0.7f) / 0.3f).coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxSize().zIndex(5f).graphicsLayer { alpha = 1f - outP }
            .background(Brush.verticalGradient(Look.skyColors()))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        contentAlignment = Alignment.Center
    ) {
        FutureGlow()
        Column(
            Modifier.blur((24f * (1f - inP)).dp, BlurredEdgeTreatment.Unbounded)
                .graphicsLayer {
                    val s = 1.25f - 0.25f * inP - 0.10f * outP
                    scaleX = s; scaleY = s
                    alpha = inP
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Text(
                "CarfBot",
                style = TextStyle(fontFamily = Sans, fontSize = 64.sp, fontWeight = FontWeight.Light, letterSpacing = (-3).sp, color = Look.ink)
            )
            T("your AI assistant", 16, Look.ink.copy(alpha = 0.6f))
        }
    }
}

// ═══════════════════════════ Home ═══════════════════════════

private class HomeTile(val label: String, val big: String, val icon: ImageVector, val action: () -> Unit)

@Composable
private fun FutureHome(
    chat: ChatState, enter: Boolean,
    onOpen: (FScreen) -> Unit, onRun: (String) -> Unit, onSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(enter) { if (enter) appear.animateTo(1f, tween(1500, easing = FastOutSlowInEasing)) }
    // staggered focus-in: each row starts a little later
    fun st(i: Int): Float = (appear.value * 1.8f - i * 0.18f).coerceIn(0f, 1f)

    var tipIdx by remember { mutableIntStateOf(0) }
    val tip = tips[tipIdx % tips.size]
    val tiles = listOf(
        HomeTile("Chat", "${chat.entries.size}", Icons.Outlined.AutoAwesome) { onOpen(FScreen.Chat) },
        HomeTile("Notes", "${Prefs.list(ctx, "notes").size}", Icons.Outlined.ChatBubbleOutline) { onRun("show my notes") },
        HomeTile("To-do", "${Prefs.list(ctx, "todo").size}", Icons.Outlined.GridView) { onRun("show my todo list") },
        HomeTile("Briefing", "Today", Icons.Outlined.AutoAwesome) { onRun("daily briefing") },
        HomeTile("Inbox", "Digest", Icons.Outlined.ChatBubbleOutline) { onRun("summarize my notifications") },
        HomeTile("Device", Device.batteryPct(ctx).let { if (it < 0) "--" else "$it%" }, Icons.Outlined.PhoneAndroid) { onOpen(FScreen.Device) }
    )

    Box(Modifier.fillMaxSize()) {
        GhostFigure(Modifier.fillMaxSize().graphicsLayer { alpha = st(1) })
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp).focusIn(st(0), 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.glass(CircleShape, dark = false).padding(start = 5.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFFF2A3BB), Color(0xFF9B7BE0)))),
                        contentAlignment = Alignment.Center
                    ) { T("C", 16, Color.White, FontWeight.Bold) }
                    Spacer(Modifier.width(8.dp))
                    T("CarfBot", 15, Look.ink, FontWeight.Medium)
                }
                Spacer(Modifier.weight(1f))
                GlassIconButton(Icons.Outlined.AutoAwesome, { onOpen(FScreen.Chat) }, tint = Color(0xFFE5484D))
                Spacer(Modifier.width(8.dp))
                GlassIconButton(Icons.Outlined.MoreHoriz, onSettings)
            }

            Spacer(Modifier.weight(1f))

            GlassChip("CarfBot AI Plus", Modifier.focusIn(st(1), 12.dp), icon = Icons.Outlined.AutoAwesome)
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.Text(
                heroTitle().replace(" ", "\n"),
                modifier = Modifier.focusIn(st(2), 26.dp),
                style = TextStyle(
                    fontFamily = Sans, fontSize = 60.sp, fontWeight = FontWeight.Light, lineHeight = 58.sp,
                    letterSpacing = (-3).sp, color = Color.White
                )
            )
            Spacer(Modifier.height(8.dp))
            T(tip.body, 15, Color.White.copy(alpha = 0.88f), maxLines = 2, lineHeight = 21, modifier = Modifier.focusIn(st(3), 14.dp))
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().focusIn(st(4), 14.dp), verticalAlignment = Alignment.CenterVertically) {
                MiniBadge("C", Color(0xFFF2A3BB), Color(0xFF9B7BE0), 0.dp) { onOpen(FScreen.Chat) }
                MiniBadge("S", Color(0xFF8FB6E8), Color(0xFFB59BE8), (-12).dp) { onOpen(FScreen.Studio) }
                MiniBadge("D", Color(0xFFFFA874), Color(0xFFE9A9C8), (-12).dp) { onOpen(FScreen.Device) }
                Spacer(Modifier.weight(1f))
                GlassIconButton(Icons.Outlined.AutoFixHigh, { onOpen(FScreen.Studio) }, size = 56.dp, solid = true)
                Spacer(Modifier.width(10.dp))
                GlassIconButton(Icons.Outlined.Refresh, { tipIdx++ }, size = 46.dp)
            }
            Spacer(Modifier.height(14.dp))
            LazyRow(
                Modifier.fillMaxWidth().focusIn(st(5), 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)
            ) {
                items(tiles) { t ->
                    ExpressiveSurface(t.action, Modifier.width(150.dp).height(112.dp), dark = false, rest = 28.dp) {
                        Column(Modifier.fillMaxSize().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(t.icon, null, tint = Look.ink, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.weight(1f))
                                T(t.big, 26, Look.ink, FontWeight.Light)
                            }
                            Spacer(Modifier.weight(1f))
                            T(t.label, 15, Look.ink.copy(alpha = 0.75f), FontWeight.Medium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun MiniBadge(letter: String, a: Color, b: Color, overlap: Dp, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Box(
        Modifier.offset(x = overlap).size(40.dp).clip(CircleShape)
            .background(Brush.linearGradient(listOf(a, b)))
            .clickable { Haptics.tick(ctx); onClick() },
        contentAlignment = Alignment.Center
    ) { T(letter, 16, Color.White, FontWeight.Bold) }
}

// ═══════════════════════════ Tools ═══════════════════════════

@Composable
private fun ToolsScreen(onRun: (String) -> Unit) {
    val cats = remember {
        listOf(
            "Assistant" to Assist.commands,
            "Shortcuts" to itemsFor(CardKind.SHORTCUTS),
            "Apps & Files" to itemsFor(CardKind.FILES),
            "Music" to itemsFor(CardKind.MUSIC),
            "Clipboard" to itemsFor(CardKind.CLIPBOARD)
        )
    }
    var sel by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        BigTitle("Tools", 64)
        Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(cats) { i, c -> GlassChip(c.first, selected = i == sel) { sel = i } }
        }
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(cats[sel].second.chunked(2)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (l, c) -> NoteCard(l, c, Modifier.weight(1f)) { onRun(c) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
