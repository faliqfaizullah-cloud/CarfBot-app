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
    private val openR = Regex("^(?:please )?(?:open|launch|start|run)\\s+(?:the\\s+)?(.+?)(?:\\s+app)?$")
    private val switchR = Regex("^(?:please )?(?:go to|switch to|jump to|take me to)\\s+(?:the\\s+)?(.+?)(?:\\s+app)?$")
    private val describePhotoR = Regex(
        "^(?:please )?(describe|explain|identify|analy[sz]e|read|translate|solve|summari[sz]e)" +
        "(?: (?:it|this|that|the (?:photo|picture|image|pic)))(?: (?:to|into) (.+))?$" +
        "|^(?:tell me )?what(?:'s| is) (?:it|this|that)$|^what(?:'s| is) in (?:it|the photo|the picture)$"
    )

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

        Smart.handle(ctx, s.trim())?.let { return it }

        if (photoR.matches(l)) {
            val cap = capture ?: return "I can’t open the camera from here."
            val uri = cap() ?: return "Photo cancelled."
            st.photo = uri
            Actions.launched = true
            Haptics.confirm(ctx)
            return "Photo taken."
        }

        describePhotoR.matchEntire(l)?.let { m ->
            val uri = st.photo
            if (uri != null) {
                val verb = m.groupValues[1]
                val lang = m.groupValues[2].ifBlank { "English" }
                val prompt = when {
                    verb.startsWith("read") -> Smart.P_READ
                    verb.startsWith("translate") -> Smart.translatePrompt(lang)
                    verb.startsWith("solve") -> Smart.P_SOLVE
                    verb.startsWith("summar") -> Smart.P_SUMMARY
                    else -> Smart.P_DESCRIBE
                }
                return Smart.visionOn(ctx, uri, prompt)
            }
        }

        shareR.matchEntire(l)?.let { m ->
            val what = m.groupValues[1]
            var app = m.groupValues[2]
            if (app.isBlank()) prepR.matchEntire(what)?.let { app = it.groupValues[1] }
            val uri = st.photo ?: Smart.latestImage(ctx, what.contains("screenshot"))
                ?: return "I don’t have a photo to share yet."
            return sharePhoto(ctx, uri, app)
        }

        // "go to youtube", "switch to whatsapp": open the app when one matches strongly
        switchR.matchEntire(l)?.let { m ->
            val hit = AppIndex.best(ctx, m.groupValues[1])
            if (hit != null && hit.second >= 85) {
                AppIndex.open(ctx, hit.first)
                return "Opening ${hit.first.label}"
            }
        }

        val r = Actions.handle(ctx, l)
        if (r != null) {
            // no app with that name: let the AI pick the closest installed app
            if (r.startsWith("I couldn't find an app called") && AiClient.hasKey(ctx)) {
                val name = openR.matchEntire(l)?.groupValues?.get(1) ?: l
                val labels = AppIndex.get(ctx).map { it.label }
                val pick = AiClient.pickApp(ctx, name, labels)
                val entry = if (pick != null) AppIndex.byLabel(ctx, pick) else null
                if (entry != null && AppIndex.open(ctx, entry)) return "Opening ${entry.label}"
            }
            return r
        }

        // a bare app name ("youtube", "whatsapp") opens instantly, without asking the AI
        if (l.length >= 3 && l.split(" ").size <= 2) {
            val hit = AppIndex.best(ctx, l)
            if (hit != null && hit.second >= 85 && AppIndex.open(ctx, hit.first)) return "Opening ${hit.first.label}"
        }
        return null
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
