package com.carfbot.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import kotlin.math.min

/**
 * Keeps a ready-made list of launchable apps so "open X" needs no search at tap time.
 * Matching ignores case, spaces and punctuation ("you tube" = YouTube), understands short names
 * ("yt", "insta") and tolerates small typos or mis-heard words. Apps you open often win ties.
 */
object AppIndex {
    class Entry(
        val label: String, val norm: String, val tokens: List<String>,
        val component: ComponentName, val pkg: String
    )

    @Volatile private var entries: List<Entry> = emptyList()
    @Volatile private var builtAt = 0L
    @Volatile private var building = false

    private val aliases = mapOf(
        "yt" to "youtube", "ig" to "instagram", "insta" to "instagram", "fb" to "facebook",
        "wa" to "whatsapp", "tg" to "telegram", "snap" to "snapchat", "gpay" to "googlepay",
        "store" to "playstore", "gmaps" to "maps"
    )
    private val splitR = Regex("[^\\p{L}\\p{N}]+")
    private val stripR = Regex("[^\\p{L}\\p{N}]")

    fun norm(s: String): String = s.lowercase().replace(stripR, "")

    private fun build(ctx: Context): List<Entry> {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, 0).mapNotNull { ri ->
            val label = ri.loadLabel(pm)?.toString()
            if (label.isNullOrBlank() || ri.activityInfo.packageName == ctx.packageName) null
            else Entry(
                label, norm(label), label.lowercase().split(splitR).filter { it.isNotEmpty() },
                ComponentName(ri.activityInfo.packageName, ri.activityInfo.name), ri.activityInfo.packageName
            )
        }.distinctBy { it.component }
    }

    @Synchronized
    private fun rebuild(ctx: Context) {
        entries = build(ctx.applicationContext)
        builtAt = SystemClock.elapsedRealtime()
    }

    /** Call early (app start, assistant start) so the first command is instant. */
    fun warm(ctx: Context) {
        if (building) return
        building = true
        val app = ctx.applicationContext
        Thread {
            try { rebuild(app) } catch (e: Exception) { } finally { building = false }
        }.start()
    }

    fun get(ctx: Context): List<Entry> {
        if (entries.isEmpty()) rebuild(ctx)
        else if (SystemClock.elapsedRealtime() - builtAt > 120_000) warm(ctx)
        return entries
    }

    fun byLabel(ctx: Context, label: String): Entry? = get(ctx).firstOrNull { it.label.equals(label, ignoreCase = true) }

    private fun lev(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    private fun score(q: String, e: Entry, qTokens: List<String>): Int {
        val l = e.norm
        if (l.isEmpty() || q.isEmpty()) return 0
        if (l == q) return 100
        if (q.length >= 3 && l.startsWith(q)) return 85 - min(10, l.length - q.length)
        if (l.length >= 3 && q.startsWith(l)) return 75
        if (qTokens.isNotEmpty() && qTokens.all { t -> e.tokens.any { it.startsWith(t) } }) return 72
        if (q.length >= 3 && l.contains(q)) return 65 - min(10, (l.length - q.length) / 2)
        if (q.length >= 4) {
            val limit = if (q.length <= 5) 1 else 2
            var d = if (kotlin.math.abs(l.length - q.length) <= 2) lev(q, l) else 99
            for (t in e.tokens) if (t.length >= 4 && kotlin.math.abs(t.length - q.length) <= 2) d = min(d, lev(q, t))
            if (d <= limit) return 60 - d * 5
        }
        return 0
    }

    private fun usage(ctx: Context, pkg: String): Int =
        ctx.getSharedPreferences("carfbot_usage", Context.MODE_PRIVATE).getInt(pkg, 0)

    private fun search(ctx: Context, list: List<Entry>, q: String, qTokens: List<String>): Pair<Entry, Int>? {
        var hit: Pair<Entry, Int>? = null
        var hitTotal = -1
        for (e in list) {
            val s = score(q, e, qTokens)
            if (s <= 0) continue
            val total = s + min(usage(ctx, e.pkg), 10) / 2
            if (total > hitTotal) {
                hitTotal = total
                hit = e to s
            }
        }
        return hit
    }

    /** Best matching installed app and its score (100 exact, 85 starts-with, ≥55 usable), or null. */
    fun best(ctx: Context, query: String): Pair<Entry, Int>? {
        val cleaned = query.lowercase().trim().removePrefix("the ").removeSuffix(" app").trim()
        val q0 = norm(cleaned)
        val q = aliases[q0] ?: q0
        if (q.length < 2) return null
        val qTokens = cleaned.split(splitR).filter { it.isNotEmpty() && it != "the" && it != "app" }
        var r = search(ctx, get(ctx), q, qTokens)
        // a just-installed app may be missing: refresh once and look again
        if ((r == null || r.second < 55) && SystemClock.elapsedRealtime() - builtAt > 5_000) {
            rebuild(ctx)
            r = search(ctx, entries, q, qTokens)
        }
        return r
    }

    fun open(ctx: Context, e: Entry): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(e.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val ok = Actions.launch(ctx, i)
        if (ok) {
            val p = ctx.getSharedPreferences("carfbot_usage", Context.MODE_PRIVATE)
            p.edit().putInt(e.pkg, p.getInt(e.pkg, 0) + 1).apply()
        }
        return ok
    }
}
