package com.carfbot.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.delay

/** Runs one command or a chain of commands ("open camera, take a photo and share it on WhatsApp"). */
object Router {
    private class State { var photo: Uri? = null }

    private val verbs = Regex(
        "^(?:please )?(?:open|launch|start|run|call|dial|phone|play|find|search|show|take|snap|capture|share|send|post|" +
        "pause|resume|stop|next|skip|previous|lyrics|translate|summari[sz]e|copy|navigate|directions|read|extract|" +
        "identify|what|organi[sz]e|grab|google|look)\\b"
    )
    private val delimiters = Regex("\\s*(?:,|;|\\band then\\b|\\bthen\\b|\\band\\b)\\s*", RegexOption.IGNORE_CASE)
    private val photoR = Regex("^(?:please )?(?:take|snap|capture)(?: (?:a|the|me a))?(?: (?:new|quick))? (?:photo|picture|pic|selfie|snapshot)$")
    private val shareR = Regex("^(?:please )?(?:share|send|post)(?: (.+?))?(?: (?:on|to|with|via|through|in|using) (.+))?$")
    private val prepR = Regex("^(?:on|to|with|via|through|in|using) (.+)$")

    fun split(text: String): List<String> {
        val parts = text.trim().split(delimiters).map { it.trim() }.filter { it.isNotEmpty() }
        return if (parts.size >= 2 && parts.all { verbs.containsMatchIn(it.lowercase()) }) parts else listOf(text.trim())
    }

    /** Returns the reply, or null when the text isn't a device command (so it goes to AI chat). */
    suspend fun execute(ctx: Context, text: String, capture: (suspend () -> Uri?)?): String? {
        val steps = split(text)
        val st = State()
        if (steps.size == 1) return step(ctx, steps[0], st, capture)

        val out = ArrayList<String>()
        var anyLaunch = false
        for ((i, s) in steps.withIndex()) {
            val r = step(ctx, s, st, capture)
            if (Actions.launched) anyLaunch = true
            out.add("${i + 1}. " + (r ?: "I didn’t understand “$s”."))
            if (r != null && r.startsWith("Photo cancelled")) break
            if (i < steps.lastIndex) delay(700)
        }
        Actions.launched = anyLaunch
        return out.joinToString("\n")
    }

    private suspend fun step(ctx: Context, s: String, st: State, capture: (suspend () -> Uri?)?): String? {
        Actions.launched = false
        val l = s.trim().replace(Regex("[.!?]+$"), "").replace('’', '\'').lowercase()

        Smart.handle(ctx, l)?.let { return it }

        if (photoR.matches(l)) {
            val cap = capture ?: return "I can’t open the camera from here."
            val uri = cap() ?: return "Photo cancelled."
            st.photo = uri
            Actions.launched = true
            Haptics.confirm(ctx)
            return "Photo taken."
        }

        shareR.matchEntire(l)?.let { m ->
            val what = m.groupValues[1]
            var app = m.groupValues[2]
            if (app.isBlank()) prepR.matchEntire(what)?.let { app = it.groupValues[1] }
            val uri = st.photo ?: Smart.latestImage(ctx, what.contains("screenshot"))
                ?: return "I don’t have a photo to share yet."
            return sharePhoto(ctx, uri, app)
        }

        return Actions.handle(ctx, l)
    }

    private fun sharePhoto(ctx: Context, uri: Uri, app: String): String {
        val mime = ctx.contentResolver.getType(uri) ?: "image/*"
        val send = Intent(Intent.ACTION_SEND).setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (app.isBlank()) {
            Actions.launch(ctx, Intent.createChooser(send, "Share via").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            return "Choose where to share it."
        }
        val pm = ctx.packageManager
        val key = app.replace(" ", "")
        val match = pm.queryIntentActivities(send, 0).firstOrNull {
            it.loadLabel(pm).toString().lowercase().replace(" ", "").contains(key) ||
                it.activityInfo.packageName.contains(key)
        } ?: return "I couldn’t find “$app” to share with."
        send.setClassName(match.activityInfo.packageName, match.activityInfo.name)
        return if (Actions.launch(ctx, send)) "Sharing with ${match.loadLabel(pm)}. Pick who to send it to."
        else "I couldn’t open “$app” to share."
    }
}
