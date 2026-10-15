package com.carfbot.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.Settings
import android.view.Display
import java.net.Inet4Address
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Battery, device info, storage, memory, network, screen, flashlight, volume, timers and settings. */
object Device {
    class Snap(
        val battery: Int, val charging: Boolean, val plugged: String, val tempC: Float, val health: String,
        val voltageMv: Int, val name: String, val manufacturer: String, val model: String,
        val android: String, val sdk: Int, val patch: String,
        val storageTotal: Long, val storageFree: Long, val ramTotal: Long, val ramFree: Long,
        val network: String, val uptimeMs: Long, val screen: String, val brightnessPct: Int
    )

    val commands: List<Pair<String, String>> = listOf(
        "Battery" to "battery", "Device info" to "device info", "Device name" to "device name",
        "Storage" to "storage", "Memory" to "memory", "Network" to "network",
        "Screen" to "screen info", "Uptime" to "uptime", "Flashlight" to "flashlight",
        "Volume up" to "volume up", "Volume down" to "volume down", "Set timer" to "set timer 5 minutes",
        "Time" to "what time is it", "Full report" to "device report"
    )

    // ───────────── readings ─────────────

    private fun batteryIntent(ctx: Context): Intent? =
        ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    fun batteryPct(ctx: Context): Int {
        val i = batteryIntent(ctx) ?: return -1
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (l < 0 || s <= 0) -1 else (l * 100f / s).roundToInt()
    }

    fun deviceName(ctx: Context): String {
        val cr = ctx.contentResolver
        val g = try { Settings.Global.getString(cr, "device_name") } catch (e: Exception) { null }
        if (!g.isNullOrBlank()) return g
        val b = try { Settings.Secure.getString(cr, "bluetooth_name") } catch (e: Exception) { null }
        if (!b.isNullOrBlank()) return b
        return Build.MODEL
    }

    private fun healthText(h: Int): String = when (h) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
        else -> "Unknown"
    }

    private fun networkText(ctx: Context): String {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return "Offline"
            val caps = cm.getNetworkCapabilities(net) ?: return "Offline"
            val type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "Connected"
            }
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) type else "$type (no internet)"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun ipAddress(ctx: Context): String? = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
            ?.map { it.address }?.firstOrNull { it is Inet4Address }?.hostAddress
    } catch (e: Exception) { null }

    private fun screenText(ctx: Context): String {
        val m = ctx.resources.displayMetrics
        val hz = try {
            val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            dm.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate?.roundToInt()
        } catch (e: Exception) { null }
        return "${m.widthPixels}x${m.heightPixels}" + (if (hz != null) " · $hz Hz" else "") + " · ${m.densityDpi} dpi"
    }

    private fun brightnessPct(ctx: Context): Int = try {
        val v = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        if (v < 0) -1 else (v * 100f / 255f).roundToInt()
    } catch (e: Exception) { -1 }

    fun snapshot(ctx: Context): Snap {
        val i = batteryIntent(ctx)
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = when (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> ""
        }
        val stat = StatFs(Environment.getDataDirectory().path)
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return Snap(
            battery = if (level < 0 || scale <= 0) -1 else (level * 100f / scale).roundToInt(),
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            plugged = plugged,
            tempC = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            health = healthText(i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0),
            voltageMv = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0,
            name = deviceName(ctx), manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL, android = Build.VERSION.RELEASE, sdk = Build.VERSION.SDK_INT,
            patch = if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else "",
            storageTotal = stat.totalBytes, storageFree = stat.availableBytes,
            ramTotal = mi.totalMem, ramFree = mi.availMem,
            network = networkText(ctx), uptimeMs = SystemClock.elapsedRealtime(),
            screen = screenText(ctx), brightnessPct = brightnessPct(ctx)
        )
    }

    // ───────────── text ─────────────

    fun gb(bytes: Long): String = String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)

    fun uptime(ms: Long): String {
        val mins = ms / 60000
        val d = mins / 1440
        val h = (mins % 1440) / 60
        val m = mins % 60
        return if (d > 0) "${d}d ${h}h ${m}m" else if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun batteryText(ctx: Context): String {
        val s = snapshot(ctx)
        if (s.battery < 0) return "I can’t read the battery on this device."
        val state = when {
            s.charging && s.battery >= 100 -> "full"
            s.charging -> "charging" + (if (s.plugged.isNotEmpty()) " via ${s.plugged}" else "")
            else -> "not charging"
        }
        val sb = StringBuilder("Battery: ${s.battery}% ($state)\n")
        sb.append(String.format(Locale.US, "Temperature %.1f°C · Health %s · %.2f V", s.tempC, s.health, s.voltageMv / 1000f))
        if (s.charging && Build.VERSION.SDK_INT >= 28) {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val t = bm.computeChargeTimeRemaining()
            if (t > 0) sb.append("\nAbout ${max(1L, t / 60000)} min until full")
        }
        return sb.toString()
    }

    private fun infoText(ctx: Context): String {
        val s = snapshot(ctx)
        return "Name: ${s.name}\nModel: ${s.manufacturer} ${s.model}\nAndroid ${s.android} (SDK ${s.sdk})" +
            (if (s.patch.isNotEmpty()) "\nSecurity patch: ${s.patch}" else "") +
            "\nChip: ${Build.HARDWARE} · ${Runtime.getRuntime().availableProcessors()} cores" +
            "\nBuild: ${Build.DISPLAY}"
    }

    private fun storageText(ctx: Context): String {
        val s = snapshot(ctx)
        val used = s.storageTotal - s.storageFree
        val pct = if (s.storageTotal > 0) (used * 100 / s.storageTotal) else 0
        return "Storage: ${gb(used)} used of ${gb(s.storageTotal)} ($pct%)\n${gb(s.storageFree)} free"
    }

    private fun memoryText(ctx: Context): String {
        val s = snapshot(ctx)
        val used = s.ramTotal - s.ramFree
        val pct = if (s.ramTotal > 0) (used * 100 / s.ramTotal) else 0
        return "Memory: ${gb(used)} used of ${gb(s.ramTotal)} ($pct%)\n${gb(s.ramFree)} available"
    }

    private fun netText(ctx: Context): String {
        val ip = ipAddress(ctx)
        return "Network: ${networkText(ctx)}" + (if (ip != null) "\nIP address: $ip" else "")
    }

    private fun screenInfo(ctx: Context): String {
        val b = brightnessPct(ctx)
        return "Screen: ${screenText(ctx)}" + (if (b >= 0) "\nBrightness: $b%" else "")
    }

    private fun report(ctx: Context): String =
        listOf(infoText(ctx), batteryText(ctx), storageText(ctx), memoryText(ctx), netText(ctx),
            screenInfo(ctx), "Uptime: " + uptime(SystemClock.elapsedRealtime())).joinToString("\n\n")

    // ───────────── actions ─────────────

    private var torchOn = false

    private fun torch(ctx: Context, want: Boolean?): String {
        return try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "This device has no flashlight."
            val on = want ?: !torchOn
            cm.setTorchMode(id, on)
            torchOn = on
            Haptics.click(ctx)
            if (on) "Flashlight on" else "Flashlight off"
        } catch (e: Exception) {
            "I couldn’t control the flashlight."
        }
    }

    private fun volume(ctx: Context, how: String, pct: Int = 0): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxV = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val step = max(1, maxV / 8)
        val target = when (how) {
            "up" -> min(maxV, cur + step)
            "down" -> max(0, cur - step)
            "mute" -> 0
            "max" -> maxV
            else -> (pct.coerceIn(0, 100) * maxV / 100f).roundToInt()
        }
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        Haptics.click(ctx)
        return "Volume ${if (maxV > 0) target * 100 / maxV else 0}%"
    }

    private fun startQuietly(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) { false }

    private fun timer(ctx: Context, amount: Double, unit: String): String {
        val secs = (amount * when {
            unit.startsWith("h") -> 3600
            unit.startsWith("m") -> 60
            else -> 1
        }).roundToInt()
        val i = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, secs)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "CarfBot timer").putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (startQuietly(ctx, i)) "Timer set for ${if (secs >= 60) "${secs / 60} min" else "$secs sec"}"
        else "I couldn’t set a timer on this phone."
    }

    private fun alarm(ctx: Context, hour12: Int, min: Int, ampm: String): String {
        var h = hour12
        if (ampm == "pm" && h < 12) h += 12
        if (ampm == "am" && h == 12) h = 0
        val i = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, min).putExtra(AlarmClock.EXTRA_MESSAGE, "CarfBot alarm")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (startQuietly(ctx, i)) String.format(Locale.US, "Alarm set for %d:%02d", h, min)
        else "I couldn’t set an alarm on this phone."
    }

    private val settingsMap = listOf(
        "wifi" to Settings.ACTION_WIFI_SETTINGS, "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "display" to Settings.ACTION_DISPLAY_SETTINGS, "battery" to Intent.ACTION_POWER_USAGE_SUMMARY,
        "storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "sound" to Settings.ACTION_SOUND_SETTINGS,
        "volume" to Settings.ACTION_SOUND_SETTINGS, "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "airplane" to Settings.ACTION_AIRPLANE_MODE_SETTINGS, "nfc" to Settings.ACTION_NFC_SETTINGS,
        "accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS, "date" to Settings.ACTION_DATE_SETTINGS,
        "language" to Settings.ACTION_LOCALE_SETTINGS, "app" to Settings.ACTION_APPLICATION_SETTINGS,
        "privacy" to Settings.ACTION_PRIVACY_SETTINGS, "security" to Settings.ACTION_SECURITY_SETTINGS,
        "network" to Settings.ACTION_WIRELESS_SETTINGS, "developer" to Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        "cast" to Settings.ACTION_CAST_SETTINGS, "keyboard" to Settings.ACTION_INPUT_METHOD_SETTINGS
    )

    private val settingsR = Regex(
        "^(?:open |show |go to |take me to |launch )?(?:the )?(wi-?fi|bluetooth|display|battery|storage|sound|volume|" +
        "location|airplane(?: mode)?|nfc|accessibility|date(?: and time)?|language|apps?|privacy|security|network|" +
        "developer(?: options)?|cast|keyboard) settings$"
    )
    private val toggleR = Regex("^(?:turn|switch) (?:on|off) (wi-?fi|bluetooth|airplane mode|mobile data)$")
    private val flashR = Regex("^(?:turn |switch )?(?:on |off )?(?:the )?(?:flashlight|torch|flash light)(?: on| off)?$")
    private val volUpR = Regex("^(?:volume up|turn (?:the )?volume up|turn it up|louder|increase (?:the )?volume|raise (?:the )?volume)$")
    private val volDownR = Regex("^(?:volume down|turn (?:the )?volume down|turn it down|quieter|decrease (?:the )?volume|lower (?:the )?volume)$")
    private val muteR = Regex("^(?:mute|silence)(?: (?:the )?(?:volume|sound|music))?$")
    private val volMaxR = Regex("^(?:max(?:imum)? volume|volume (?:to )?max(?:imum)?|full volume)$")
    private val volSetR = Regex("^(?:set |change )?(?:the )?volume (?:to |at )?(\\d{1,3})(?: ?%| percent)?$")
    private val timerR = Regex("^(?:set |start |create )?(?:a |an )?(?:timer|countdown)(?: for| of)? (\\d+(?:\\.\\d+)?) ?(seconds?|secs?|minutes?|mins?|hours?|hrs?)$")
    private val alarmR = Regex("^(?:set |create )?(?:an? )?alarm(?: for| at)? (\\d{1,2})(?::(\\d{2}))? ?(am|pm)?$")
    private val timeR = Regex("^(?:what(?:'s| is) )?(?:the )?(?:current )?(?:time|date|day)(?: is it| today| now| right now)?$|^what time is it(?: now)?$|^what(?:'s| is) today(?:'s date)?$|^what day is it(?: today)?$")
    private val reportR = Regex("^(?:full |complete )?(?:device|phone) report$|^everything about my (?:phone|device)$")
    private val infoR = Regex("^(?:device|phone) (?:info|information|specs|details|model)$|^(?:what|which) (?:phone|device) (?:is this|do i have)$|^about my (?:phone|device)$|^(?:what(?:'s| is) )?(?:my )?android version$|^system info$")
    private val nameR = Regex("\\b(?:device|phone) name\\b|what(?:'s| is) my phone called|name of my (?:phone|device)")
    private val batR = Regex("\\b(?:battery|charging|charge level|how charged)\\b")
    private val storR = Regex("\\b(?:storage|free space|disk space|space left)\\b")
    private val ramR = Regex("\\b(?:ram|memory usage|free memory|memory)\\b")
    private val netR = Regex("\\b(?:wifi|wi-fi|internet|network|ip address|connection|online)\\b")
    private val scrR = Regex("\\b(?:screen (?:size|info|resolution)|resolution|refresh rate|brightness|display info)\\b")
    private val upR = Regex("\\buptime\\b|how long has my phone been (?:on|running)|last (?:restart|reboot)")
    private val skipR = Regex("^(?:why|explain|how does|how do|what is a|what are)\\b")
    private val actionStartR = Regex("^(?:open|launch|start|call|play|find|search|share|send|take|set)\\b")
    private val settingsWordR = Regex("\\b(?:settings|saver)\\b")

    /** Returns a reply if this was a device command, otherwise null. [l] is already lower-case. */
    fun handle(ctx: Context, l: String): String? {
        if (l.split(" ").size > 9) return null
        if (skipR.containsMatchIn(l)) return null
        val actionStart = actionStartR.containsMatchIn(l)
        val settingsWord = settingsWordR.containsMatchIn(l)

        settingsR.matchEntire(l)?.let { m ->
            val key = m.groupValues[1].replace("-", "")
            val action = settingsMap.firstOrNull { key.startsWith(it.first) }?.second
            if (action != null && startQuietly(ctx, Intent(action))) return "Opening ${m.groupValues[1]} settings"
        }
        toggleR.matchEntire(l)?.let { m ->
            val what = m.groupValues[1]
            val action = when {
                what.startsWith("wi") -> Settings.ACTION_WIFI_SETTINGS
                what.startsWith("blue") -> Settings.ACTION_BLUETOOTH_SETTINGS
                what.startsWith("air") -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
                else -> Settings.ACTION_WIRELESS_SETTINGS
            }
            startQuietly(ctx, Intent(action))
            return "Android doesn’t let apps switch $what on or off, so I opened its settings."
        }

        if (reportR.matches(l)) return report(ctx)
        if (infoR.matches(l)) return infoText(ctx)
        if (nameR.containsMatchIn(l)) return "This phone is called “${deviceName(ctx)}” (${Build.MANUFACTURER} ${Build.MODEL})."

        flashR.matchEntire(l)?.let {
            val want = when {
                l.contains("off") -> false
                l.contains(" on") || l.startsWith("on ") -> true
                else -> null
            }
            return torch(ctx, want)
        }
        if (volUpR.matches(l)) return volume(ctx, "up")
        if (volDownR.matches(l)) return volume(ctx, "down")
        if (muteR.matches(l)) return volume(ctx, "mute")
        if (volMaxR.matches(l)) return volume(ctx, "max")
        volSetR.matchEntire(l)?.let { return volume(ctx, "set", it.groupValues[1].toInt()) }
        timerR.matchEntire(l)?.let { return timer(ctx, it.groupValues[1].toDouble(), it.groupValues[2]) }
        alarmR.matchEntire(l)?.let {
            val h = it.groupValues[1].toInt()
            val m = it.groupValues[2].ifBlank { "0" }.toInt()
            if (h in 0..23 && m in 0..59) return alarm(ctx, h, m, it.groupValues[3])
        }
        if (timeR.matches(l)) {
            val now = Date()
            return "It’s " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(now) +
                " on " + SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now)
        }

        if (!actionStart && !settingsWord) {
            if (batR.containsMatchIn(l)) return batteryText(ctx)
            if (storR.containsMatchIn(l)) return storageText(ctx)
            if (scrR.containsMatchIn(l)) return screenInfo(ctx)
            if (upR.containsMatchIn(l)) return "Uptime: " + uptime(SystemClock.elapsedRealtime())
            if (netR.containsMatchIn(l)) return netText(ctx)
            if (ramR.containsMatchIn(l) && !l.contains("clipboard")) return memoryText(ctx)
        }
        return null
    }
}
