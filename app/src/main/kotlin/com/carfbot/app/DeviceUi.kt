package com.carfbot.app

import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

private val Ink = Color(0xFF2B2430)
private val Muted = Color(0xFF6C6672)

/** Full-screen "My Device" dashboard: live battery ring, wavy storage/memory bars, info tiles, quick commands. */
@Composable
fun DeviceDetail(onRun: (String) -> Unit) {
    val ctx = LocalContext.current
    var snap by remember { mutableStateOf(Device.snapshot(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            snap = Device.snapshot(ctx)
        }
    }
    // darker Material You accents so they read on the light peach background
    val scheme: ColorScheme = remember {
        if (Build.VERSION.SDK_INT >= 31) dynamicLightColorScheme(ctx)
        else lightColorScheme(primary = Color(0xFFB0437A), secondary = Color(0xFFC96A2D), tertiary = Color(0xFF6F4FD0))
    }

    Box(Modifier.fillMaxSize().background(peachBrush())) {
        Glow()
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Column { Spacer(Modifier.height(16.dp)); BigTitle("My\nDevice", 56) } }
            item { BatteryPanel(snap, scheme) }
            item { MeterPanel("Storage", snap.storageTotal - snap.storageFree, snap.storageTotal, scheme.secondary) }
            item { MeterPanel("Memory", snap.ramTotal - snap.ramFree, snap.ramTotal, scheme.tertiary) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile("Network", snap.network, Modifier.weight(1f))
                    Tile("Android", "${snap.android} · SDK ${snap.sdk}", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile("Uptime", Device.uptime(snap.uptimeMs), Modifier.weight(1f))
                    Tile("Screen", snap.screen, Modifier.weight(1f))
                }
            }
            item { T("Ask CarfBot", 15, Muted, FontWeight.Medium) }
            items(Device.commands.chunked(2)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (l, c) -> NoteCard(l, c, Modifier.weight(1f)) { onRun(c) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BatteryPanel(s: Device.Snap, scheme: ColorScheme) {
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(32.dp), dark = false).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WavyRing(
            progress = if (s.battery < 0) 0f else s.battery / 100f,
            color = scheme.primary, track = scheme.primary.copy(alpha = 0.18f),
            modifier = Modifier.size(128.dp), fastWave = s.charging
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                T(if (s.battery < 0) "--" else "${s.battery}%", 30, Ink, FontWeight.ExtraBold)
                T(if (s.charging) "Charging" else "Battery", 12, Muted)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            T(s.name, 20, Ink, FontWeight.ExtraBold, maxLines = 2)
            T("${s.manufacturer} ${s.model}", 13, Muted, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            T(String.format(Locale.US, "%.1f°C · %s", s.tempC, s.health), 13, Ink)
            T(if (s.plugged.isNotEmpty()) "Power: ${s.plugged}" else "On battery", 13, Ink)
        }
    }
}

@Composable
private fun MeterPanel(label: String, used: Long, total: Long, color: Color) {
    val frac = if (total > 0) used.toFloat() / total else 0f
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp), dark = false).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(label, 17, Ink, FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            T("${(frac * 100).toInt()}%", 17, Ink, FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        T("${Device.gb(used)} used of ${Device.gb(total)}", 13, Muted)
        Spacer(Modifier.height(10.dp))
        WavyLinearProgress(frac, color, color.copy(alpha = 0.18f))
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Column(modifier.glass(RoundedCornerShape(24.dp), dark = false).padding(16.dp)) {
        T(label, 12, Muted)
        Spacer(Modifier.height(4.dp))
        T(value, 16, Ink, FontWeight.Bold, maxLines = 2)
    }
}

/** Small glass tiles shown on the "My Device" card in the stack. */
@Composable
fun DeviceMini(modifier: Modifier) {
    val ctx = LocalContext.current
    val pct = remember { Device.batteryPct(ctx) }
    val name = remember { Device.deviceName(ctx) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.glass(RoundedCornerShape(18.dp), dark = true).padding(horizontal = 14.dp, vertical = 8.dp)) {
            T(if (pct < 0) "--" else "$pct%", 22, Color.White, FontWeight.ExtraBold)
            T("battery", 11, Color(0xFFBFE3F5))
        }
        Column(Modifier.glass(RoundedCornerShape(18.dp), dark = true).padding(horizontal = 14.dp, vertical = 8.dp)) {
            T(name, 15, Color.White, FontWeight.Bold, maxLines = 1)
            T("device", 11, Color(0xFFBFE3F5))
        }
    }
}
