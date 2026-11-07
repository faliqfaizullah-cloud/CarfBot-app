package com.carfbot.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Everyday assistant skills: notification digest, calendar, daily briefing, lists and notes, reminders, texts, drafts. */
object Assist {
    val commands: List<Pair<String, String>> = listOf(
        "Daily briefing" to "daily briefing", "Summarize notifications" to "summarize my notifications",
        "Calendar today" to "what's on my calendar today", "Next event" to "next event",
        "My to-do list" to "show my todo list", "Shopping list" to "show my shopping list",
        "My notes" to "show my notes", "Add a note" to "note ", "Add a to-do" to "add  to my todo list",
        "Remind me" to "remind me to ", "Text someone" to "text ", "Write a message" to "write a message about ",
        "Remember something" to "remember that ", "What do you remember" to "what do you remember"
    )

    private val stopTalkR = Regex("^(?:stop talking|be quiet|stop reading|shut up|quiet)$")
    private val notifR = Regex("^(?:summari[sz]e (?:my )?notifications?|(?:read|show|check) (?:my )?notifications?|what did i miss|catch me up|any (?:new )?notifications?|what(?:'s| is) new)$")
    private val clearNotifR = Regex("^clear (?:my )?notification history$")
    private val calR = Regex("^(?:what(?:'s| is) )?(?:on )?(?:my )?(?:calendar|schedule|agenda)(?: for)?( today| tomorrow)?$|^what do i have( today| tomorrow)$|^(?:my )?(?:events|meetings)( today| tomorrow)?$")
    private val nextEvR = Regex("^(?:what(?:'s| is) )?(?:my )?next (?:event|meeting|appointment)$")
    private val briefR = Regex("^(?:give me )?(?:my )?(?:daily |morning |evening )?(?:briefing|brief)$|^brief me$|^start my day$")
    private val addListR = Regex("^(?:please )?add (.+?) to (?:my |the )?(shopping|grocery|groceries|to-?do|todo|tasks?)(?: list)?$", RegexOption.IGNORE_CASE)
    private val noteR = Regex("^(?:please )?(?:take a note|make a note|note down|write down|jot down|note)[: ,]+(?:that )?(.+)$", RegexOption.IGNORE_CASE)
    private val showListR = Regex("^(?:(?:show|read|list|what(?:'s| is) on)(?: me)?(?: my| the)?|my) (shopping|grocery|groceries|to-?do|todo|tasks?|notes?)(?: list)?$|^(shopping|grocery|groceries|to-?do|todo|tasks?) list$")
    private val removeListR = Regex("^(?:remove|delete|cross off|check off) (.+?) (?:from|off) (?:my |the )?(shopping|grocery|groceries|to-?do|todo|tasks?|notes?)(?: list)?$")
    private val doneR = Regex("^(?:done|finished|completed) (.+)$")
    private val clearListR = Regex("^clear (?:my |the )?(shopping|grocery|groceries|to-?do|todo|tasks?|notes?)(?: list)?$")
    private val smsR = Regex("^(?:please )?(?:text|sms|message)\\s+(.+?)(?:\\s+(?:saying|that says|to say|with)\\s+(.+))?$", RegexOption.IGNORE_CASE)
    private val draftR = Regex("^(?:please )?(?:write|draft|compose|create)(?: me)?(?: an?| the)? (email|message|text|reply|note|post|caption|letter)(?: to (.+?))?(?: (?:about|saying|that|asking|for|regarding|telling) (.+))$", RegexOption.IGNORE_CASE)
    private val remindInR = Regex("^(?:please )?remind me (?:to )?(.+?) in (\\d+(?:\\.\\d+)?) ?(seconds?|secs?|minutes?|mins?|hours?|hrs?)$")
    private val remindFirstInR = Regex("^(?:please )?remind me in (\\d+(?:\\.\\d+)?) ?(seconds?|secs?|minutes?|mins?|hours?|hrs?) (?:to )?(.+)$")
    private val remindAtR = Regex("^(?:please )?remind me (?:to )?(.+?) at (\\d{1,2})(?::(\\d{2}))? ?(am|pm)?$")
    private val remindFirstAtR = Regex("^(?:please )?remind me at (\\d{1,2})(?::(\\d{2}))? ?(am|pm)? (?:to )?(.+)$")

    /** Returns a reply if this was an assistant skill, otherwise null. [l] is lower-case, [input] keeps the original case. */
    suspend fun handle(ctx: Context, input: String, l: String): String? {
        val raw = input.trim().trimEnd('.', '!', '?')

        if (stopTalkR.matches(l)) { Speaker.stop(); return "Okay." }

        if (notifR.matches(l)) return notifications(ctx)
        if (clearNotifR.matches(l)) { NotifStore.clear(); return "Cleared the notification history CarfBot keeps in memory." }

        calR.matchEntire(l)?.let { m ->
            val day = m.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim() ?: "today"
            return calendar(ctx, day)
        }
        if (nextEvR.matches(l)) return nextEvent(ctx)
        if (briefR.matches(l)) return briefing(ctx)

        addListR.matchEntire(raw)?.let { m ->
            val name = listName(m.groupValues[2])
            val item = m.groupValues[1].trim()
            Prefs.setList(ctx, name, Prefs.list(ctx, name) + item)
            return "Added “$item” to your $name list."
        }
        noteR.matchEntire(raw)?.let { m ->
            val item = m.groupValues[1].trim()
            Prefs.setList(ctx, "notes", Prefs.list(ctx, "notes") + item)
            return "Noted: $item"
        }
        showListR.matchEntire(l)?.let { m ->
            return showList(ctx, listName(m.groupValues[1].ifBlank { m.groupValues[2] }))
        }
        removeListR.matchEntire(l)?.let { m ->
            val name = listName(m.groupValues[2])
            return if (removeMatching(ctx, name, m.groupValues[1])) "Removed it from your $name list."
            else "I couldn’t find “${m.groupValues[1]}” on your $name list."
        }
        doneR.matchEntire(l)?.let { m ->
            if (removeMatching(ctx, "todo", m.groupValues[1])) return "Nice. Marked “${m.groupValues[1]}” as done."
        }
        clearListR.matchEntire(l)?.let { m ->
            val name = listName(m.groupValues[1])
            Prefs.setList(ctx, name, emptyList())
            return "Cleared your $name list."
        }

        remind(ctx, l)?.let { return it }

        smsR.matchEntire(raw)?.let { m ->
            sms(ctx, m.groupValues[1], m.groupValues[2])?.let { return it }
        }
        draftR.matchEntire(raw)?.let { m ->
            return draft(ctx, m.groupValues[1].lowercase(), m.groupValues[2].ifBlank { null }, m.groupValues[3])
        }
        return null
    }

    // ───────────── notifications ─────────────

    private suspend fun notifications(ctx: Context): String {
        if (!NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)) {
            Actions.launch(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            return "Turn on notification access for CarfBot, then ask again. I only keep notifications in memory, " +
                "and only send them to the AI when you ask for a summary."
        }
        val items = NotifStore.recent(25)
        if (items.isEmpty()) return "No notifications yet. I start collecting once access is on."
        val lines = items.joinToString("\n") { "- ${it.app}: ${it.title} ${it.text}".trim() }
        if (!AiClient.hasKey(ctx)) return "Recent notifications:\n" + lines.take(1200)
        return AiClient.once(
            ctx, "Summarize these phone notifications as a short digest, grouped by app. " +
                "Put anything urgent or time-sensitive first. Be brief.\n\n" + lines.take(6000)
        )
    }

    // ───────────── calendar ─────────────

    private class Ev(val title: String, val begin: Long, val allDay: Boolean, val place: String)

    private fun events(ctx: Context, start: Long, end: Long): List<Ev>? {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(start.toString()).appendPath(end.toString()).build()
            val out = ArrayList<Ev>()
            ctx.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION
                ),
                null, null, CalendarContract.Instances.BEGIN + " ASC"
            )?.use { c ->
                while (c.moveToNext()) out.add(Ev(c.getString(0) ?: "(no title)", c.getLong(1), c.getInt(3) == 1, c.getString(4) ?: ""))
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    private fun dayRange(offset: Int): Pair<Long, Long> {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, offset)
        val start = c.timeInMillis
        return start to (start + 24L * 3600 * 1000)
    }

    private fun fmt(e: Ev): String {
        val place = if (e.place.isNotBlank()) " (${e.place})" else ""
        return if (e.allDay) "All day: ${e.title}$place"
        else SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(e.begin)) + "  " + e.title + place
    }

    private const val NO_CAL = "Allow calendar access for CarfBot (Settings > Apps > CarfBot > Permissions) and ask again."

    private fun calendar(ctx: Context, day: String): String {
        val (a, b) = dayRange(if (day == "tomorrow") 1 else 0)
        val evs = events(ctx, a, b) ?: return NO_CAL
        if (evs.isEmpty()) return "Nothing on your calendar $day."
        return "Your calendar $day:\n" + evs.take(12).joinToString("\n") { "• " + fmt(it) }
    }

    private fun nextEvent(ctx: Context): String {
        val now = System.currentTimeMillis()
        val evs = events(ctx, now, now + 7L * 24 * 3600 * 1000) ?: return NO_CAL
        val e = evs.firstOrNull() ?: return "Nothing coming up in the next 7 days."
        val day = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(e.begin))
        return "Next up: " + fmt(e) + (if (!e.allDay) " ($day)" else "")
    }

    private suspend fun briefing(ctx: Context): String {
        val now = Date()
        val sb = StringBuilder()
        sb.append("Date: ").append(SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now)).append('\n')
        sb.append("Time: ").append(SimpleDateFormat("h:mm a", Locale.getDefault()).format(now)).append('\n')
        val s = Device.snapshot(ctx)
        if (s.battery >= 0) sb.append("Battery: ${s.battery}%").append(if (s.charging) " (charging)" else "").append('\n')
        val (a, b) = dayRange(0)
        val evs = events(ctx, a, b)
        if (evs == null) sb.append("Calendar: not available (no permission)\n")
        else if (evs.isEmpty()) sb.append("Calendar: nothing scheduled today\n")
        else sb.append("Calendar today:\n").append(evs.take(8).joinToString("\n") { "- " + fmt(it) }).append('\n')
        val todo = Prefs.list(ctx, "todo")
        if (todo.isNotEmpty()) sb.append("To-do: ").append(todo.take(8).joinToString("; ")).append('\n')
        val n = NotifStore.recent(8)
        if (n.isNotEmpty()) sb.append("Recent notifications:\n").append(n.joinToString("\n") { "- ${it.app}: ${it.title}" }).append('\n')
        val facts = sb.toString().trim()
        if (!AiClient.hasKey(ctx)) return facts
        return AiClient.once(
            ctx, "Write a friendly, concise daily briefing (under 120 words) from these facts. " +
                "Mention the most important things first and suggest one useful thing to do.\n\n$facts"
        )
    }

    // ───────────── lists and notes (stored on this phone) ─────────────

    private fun listName(k: String): String = when {
        k.startsWith("shop") || k.startsWith("groc") -> "shopping"
        k.startsWith("note") -> "notes"
        else -> "todo"
    }

    private fun showList(ctx: Context, name: String): String {
        val items = Prefs.list(ctx, name)
        if (items.isEmpty()) return "Your $name list is empty."
        return "Your $name list:\n" + items.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
    }

    private fun removeMatching(ctx: Context, name: String, query: String): Boolean {
        val items = Prefs.list(ctx, name).toMutableList()
        val i = items.indexOfFirst { it.contains(query.trim(), ignoreCase = true) }
        if (i < 0) return false
        items.removeAt(i)
        Prefs.setList(ctx, name, items)
        return true
    }

    // ───────────── reminders (uses the phone's clock app) ─────────────

    private fun quiet(ctx: Context, i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    private fun remind(ctx: Context, l: String): String? {
        remindInR.matchEntire(l)?.let { m -> return reminderIn(ctx, m.groupValues[1], m.groupValues[2].toDouble(), m.groupValues[3]) }
        remindFirstInR.matchEntire(l)?.let { m -> return reminderIn(ctx, m.groupValues[3], m.groupValues[1].toDouble(), m.groupValues[2]) }
        remindAtR.matchEntire(l)?.let { m ->
            return reminderAt(ctx, m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3].ifBlank { "0" }.toInt(), m.groupValues[4])
        }
        remindFirstAtR.matchEntire(l)?.let { m ->
            return reminderAt(ctx, m.groupValues[4], m.groupValues[1].toInt(), m.groupValues[2].ifBlank { "0" }.toInt(), m.groupValues[3])
        }
        return null
    }

    private fun reminderIn(ctx: Context, task: String, amount: Double, unit: String): String {
        val secs = (amount * when {
            unit.startsWith("h") -> 3600
            unit.startsWith("m") -> 60
            else -> 1
        }).roundToInt()
        val i = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, secs)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Reminder: $task").putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (quiet(ctx, i)) "I’ll remind you to $task in ${if (secs >= 60) "${secs / 60} min" else "$secs sec"}."
        else "I couldn’t set that reminder on this phone."
    }

    private fun reminderAt(ctx: Context, task: String, hour: Int, min: Int, ampm: String): String {
        var h = hour
        if (ampm == "pm" && h < 12) h += 12
        if (ampm == "am" && h == 12) h = 0
        if (h !in 0..23 || min !in 0..59) return "That time doesn’t look right."
        val i = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, min).putExtra(AlarmClock.EXTRA_MESSAGE, "Reminder: $task")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (quiet(ctx, i)) String.format(Locale.US, "I’ll remind you to %s at %d:%02d.", task, h, min)
        else "I couldn’t set that reminder on this phone."
    }

    // ───────────── texting and drafting ─────────────

    private fun sms(ctx: Context, whoRaw: String, body: String): String? {
        val who = whoRaw.trim()
        val digits = who.replace(Regex("[\\s-]"), "")
        val name: String
        val number: String
        if (digits.matches(Regex("\\+?\\d{3,}"))) {
            name = digits; number = digits
        } else {
            val found = Actions.lookupNumber(ctx, who) ?: return null
            name = found.first; number = found.second
        }
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).putExtra("sms_body", body.trim())
        return if (Actions.launch(ctx, i)) "Opening a message to $name" +
            (if (body.isNotBlank()) ". Review it and tap send." else ".")
        else null
    }

    private suspend fun draft(ctx: Context, kind: String, to: String?, about: String): String {
        if (!AiClient.hasKey(ctx)) return "Add your free Gemini key in Settings so I can write drafts."
        val text = AiClient.once(
            ctx, "Write a $kind" + (if (to != null) " to $to" else "") + " about: $about\n" +
                "Keep it natural, concise and ready to send. Reply with only the $kind text."
        )
        if (text.startsWith("Gemini ") || text.startsWith("You’ve hit") || text.startsWith("I couldn")) return text
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("CarfBot draft", text))
        return text + "\n\n(Copied to your clipboard.)"
    }
}
