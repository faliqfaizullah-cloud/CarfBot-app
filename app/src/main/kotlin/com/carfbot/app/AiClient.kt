package com.carfbot.app

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Prefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("carfbot", Context.MODE_PRIVATE)
    /** Gemini API key (free key from Google AI Studio). */
    fun apiKey(ctx: Context): String = p(ctx).getString("gemini_key", "") ?: ""
    fun setApiKey(ctx: Context, k: String) = p(ctx).edit().putString("gemini_key", k.trim()).apply()
    fun backTap(ctx: Context): Boolean = p(ctx).getBoolean("back_tap", false)
    fun setBackTap(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("back_tap", v).apply()

    /** 0 = Fast, 1 = Accurate (default). */
    fun aiMode(ctx: Context): Int = p(ctx).getInt("ai_mode", 1)
    fun setAiMode(ctx: Context, v: Int) = p(ctx).edit().putInt("ai_mode", v).apply()

    /** Things the user asked CarfBot to remember. Stored only on this phone. */
    fun memories(ctx: Context): List<String> {
        val raw = p(ctx).getString("memories", "[]") ?: "[]"
        return try {
            val a = JSONArray(raw)
            List(a.length()) { a.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addMemory(ctx: Context, m: String) {
        val l = memories(ctx).toMutableList()
        l.add(m.trim().take(300))
        while (l.size > 30) l.removeAt(0)
        val a = JSONArray()
        l.forEach { a.put(it) }
        p(ctx).edit().putString("memories", a.toString()).apply()
    }

    fun clearMemories(ctx: Context) = p(ctx).edit().remove("memories").apply()
}

/**
 * AI through the Google Gemini API (free tier).
 *  - understands free-form requests and turns them into phone actions (function calling)
 *  - uses Google Search grounding for questions that need fresh facts (and shows sources)
 *  - streams answers as they are written
 *  - sees images (screenshots, photos)
 */
object AiClient {
    private val history = mutableListOf<Pair<String, String>>()
    private val main = Handler(Looper.getMainLooper())

    private const val NO_KEY =
        "To use AI, add your free Gemini API key in Settings (menu icon). Get one at aistudio.google.com/apikey. " +
        "Device commands work without it."

    fun hasKey(ctx: Context) = Prefs.apiKey(ctx).isNotBlank()

    private fun models(ctx: Context): List<String> =
        if (Prefs.aiMode(ctx) == 1) listOf("gemini-flash-latest", "gemini-3.1-flash-lite")
        else listOf("gemini-3.1-flash-lite", "gemini-flash-latest")

    private fun systemPrompt(ctx: Context): String {
        val now = Date()
        val date = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(now)
        val time = SimpleDateFormat("h:mm a", Locale.US).format(now)
        val mem = Prefs.memories(ctx)
        return buildString {
            append("You are CarfBot, a friendly and accurate assistant inside an Android app. ")
            append("Be accurate: never invent facts, numbers, names, quotes or links. If you are not sure, say so briefly. ")
            append("For maths or logic, work step by step and then state the final answer clearly. ")
            append("Answer the question that was asked. Keep simple answers short; use short paragraphs or a short list for longer ones. ")
            append("If the request is ambiguous, ask one short clarifying question. ")
            append("Reply in the language the user writes in. ")
            append("Right now it is $time on $date (time zone ${TimeZone.getDefault().id}). ")
            append("The user's phone is a ${Build.MANUFACTURER} ${Build.MODEL} running Android ${Build.VERSION.RELEASE}.")
            if (mem.isNotEmpty()) {
                append(" Things the user asked you to remember: ")
                append(mem.joinToString("; "))
                append(".")
            }
        }
    }

    // ───────────── request building ─────────────

    private fun textPart(t: String) = JSONObject().put("text", t)

    private fun contents(msgs: List<Pair<String, String>>): JSONArray {
        val arr = JSONArray()
        msgs.forEach { (role, text) ->
            arr.put(JSONObject().put("role", role).put("parts", JSONArray().put(textPart(text))))
        }
        return arr
    }

    private fun baseBody(ctx: Context, msgs: List<Pair<String, String>>, temp: Double): JSONObject =
        JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(textPart(systemPrompt(ctx)))))
            .put("contents", contents(msgs))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 4096).put("temperature", temp))

    private fun deviceFunction(): JSONObject = JSONObject()
        .put("name", "device_command")
        .put(
            "description",
            "Runs ONE action on the user's Android phone. Call it only when the user clearly asks the phone to do " +
            "something (not for questions or chat). Pass a short imperative command in this grammar: " +
            "'open <app name>', 'call <contact>', 'play <song or artist>', 'pause', 'next song', 'previous song', " +
            "'what's playing', 'lyrics', 'directions to <place>', 'search <query>', 'set timer <N> minutes', " +
            "'set alarm <H:MM am/pm>', 'flashlight on', 'flashlight off', 'volume up', 'volume down', " +
            "'set volume to <0-100>', 'battery', 'storage', 'memory', 'network', 'device info', 'wifi settings', " +
            "'bluetooth settings', 'find file <name>', 'find photo <name>', 'summarize clipboard', " +
            "'translate clipboard to <language>', 'summarize my screenshot', 'describe my latest photo', " +
            "'take a photo', 'share it on <app>'. Call it once per action; several calls are allowed."
        )
        .put(
            "parameters",
            JSONObject().put("type", "object")
                .put(
                    "properties",
                    JSONObject().put(
                        "command",
                        JSONObject().put("type", "string").put("description", "One short command in the grammar above")
                    )
                )
                .put("required", JSONArray().put("command"))
        )

    private fun withTools(body: JSONObject): JSONObject =
        body.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", JSONArray().put(deviceFunction()))))

    private fun withSearch(body: JSONObject): JSONObject =
        body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))

    // ───────────── response parsing ─────────────

    private class Gen(
        val code: Int,
        val text: String = "",
        val sources: List<Pair<String, String>> = emptyList(),
        val commands: List<String> = emptyList()
    )

    private fun textOf(c: JSONObject): String {
        val parts = c.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.optJSONObject(i) ?: continue
            if (p.optBoolean("thought", false)) continue
            sb.append(p.optString("text", ""))
        }
        return sb.toString()
    }

    private fun commandsOf(c: JSONObject): List<String> {
        val parts = c.optJSONObject("content")?.optJSONArray("parts") ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until parts.length()) {
            val f = parts.optJSONObject(i)?.optJSONObject("functionCall") ?: continue
            if (f.optString("name") != "device_command") continue
            val cmd = f.optJSONObject("args")?.optString("command") ?: continue
            if (cmd.isNotBlank()) out.add(cmd.trim())
        }
        return out
    }

    private fun sourcesOf(c: JSONObject): List<Pair<String, String>> {
        val arr = c.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks") ?: return emptyList()
        val out = ArrayList<Pair<String, String>>()
        for (i in 0 until arr.length()) {
            val w = arr.optJSONObject(i)?.optJSONObject("web") ?: continue
            out.add(w.optString("uri") to w.optString("title"))
        }
        return out
    }

    private fun withSources(g: Gen): String {
        val names = g.sources.map { it.second.ifBlank { it.first } }.filter { it.isNotBlank() }.distinct().take(3)
        return if (names.isEmpty()) g.text.trim() else g.text.trim() + "\n\nSources: " + names.joinToString(", ")
    }

    private fun errorText(code: Int): String = when (code) {
        400, 401, 403 -> "Gemini didn’t accept the API key (error $code). Check it in Settings."
        429 -> "You’ve hit the free Gemini limit for now. Try again in a minute."
        in 500..599 -> "Gemini is busy right now. Try again in a moment."
        else -> "Gemini returned an error ($code)."
    }

    // ───────────── network ─────────────

    private fun generate(key: String, model: String, body: JSONObject, stream: ((String) -> Unit)?): Gen {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:" +
            (if (stream != null) "streamGenerateContent?alt=sse" else "generateContent")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 15000; readTimeout = 60000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", key)
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.errorStream?.close()
            return Gen(code)
        }
        if (stream == null) {
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            val c = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0) ?: return Gen(code)
            return Gen(code, textOf(c), sourcesOf(c), commandsOf(c))
        }
        val text = StringBuilder()
        var sources: List<Pair<String, String>> = emptyList()
        val cmds = ArrayList<String>()
        conn.inputStream.bufferedReader().use { r ->
            while (true) {
                val line = r.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val json = line.substring(5).trim()
                if (json.isEmpty()) continue
                try {
                    val c = JSONObject(json).optJSONArray("candidates")?.optJSONObject(0) ?: continue
                    text.append(textOf(c))
                    cmds.addAll(commandsOf(c))
                    val s = sourcesOf(c)
                    if (s.isNotEmpty()) sources = s
                    if (text.isNotEmpty()) stream(text.toString())
                } catch (e: JSONException) {
                    // ignore a malformed chunk
                }
            }
        }
        return Gen(code, text.toString(), sources, cmds)
    }

    /** Tries each model in turn when one is missing, out of quota or overloaded. */
    private fun gen(ctx: Context, key: String, body: JSONObject, stream: ((String) -> Unit)?): Gen {
        var last = Gen(0)
        for (m in models(ctx)) {
            last = generate(key, m, body, stream)
            if (last.code in 200..299) return last
            if (last.code != 404 && last.code != 429 && last.code != 503) break
        }
        return last
    }

    private fun toMain(cb: ((String) -> Unit)?): ((String) -> Unit)? {
        if (cb == null) return null
        return { t -> main.post { cb(t) } }
    }

    // ───────────── routing helpers ─────────────

    private val actionR = Regex(
        "^(?:(?:please|hey|ok|okay|can you|could you|would you|will you|i want you to|i need you to|i'd like you to)\\s+)*" +
        "(?:open|launch|start|set|turn|switch|call|dial|play|pause|skip|take|snap|share|send|find|navigate|put|mute|unmute|" +
        "increase|decrease|lower|raise|wake me|show me my|check my)\\b",
        RegexOption.IGNORE_CASE
    )
    private val freshR = Regex(
        "\\b(today|tonight|tomorrow|yesterday|now|current|currently|latest|newest|recent|recently|news|score|scores|" +
        "weather|forecast|price|prices|stock|stocks|exchange rate|release date|released|trending|breaking|election|" +
        "results?|202[4-9]|who (?:is|won|are)|when (?:is|does|did|will)|how much (?:is|does|do))\\b",
        RegexOption.IGNORE_CASE
    )

    private fun looksLikeAction(t: String) = actionR.containsMatchIn(t.trim()) || t.contains("wake me", ignoreCase = true)
    private fun needsFresh(t: String) = freshR.containsMatchIn(t)

    private suspend fun runCommands(ctx: Context, g: Gen, capture: (suspend () -> Uri?)?): String {
        val sb = StringBuilder()
        if (g.text.isNotBlank()) sb.append(g.text.trim()).append("\n\n")
        for (c in g.commands.take(5)) {
            val r = withContext(Dispatchers.Main) { Router.execute(ctx, c, capture) }
            sb.append(r ?: "I couldn’t do “$c”.").append('\n')
        }
        return sb.toString().trim()
    }

    // ───────────── public API ─────────────

    /**
     * A conversation turn. Free-form requests can trigger phone actions; fresh-fact questions use web search;
     * onPartial (called on the main thread) receives the answer as it streams in.
     */
    suspend fun ask(
        ctx: Context, text: String,
        onPartial: ((String) -> Unit)? = null,
        capture: (suspend () -> Uri?)? = null
    ): String = withContext(Dispatchers.IO) {
        val key = Prefs.apiKey(ctx)
        if (key.isBlank()) return@withContext NO_KEY
        Actions.launched = false
        history.add("user" to text)
        var failed = false
        val out: String = try {
            val accurate = Prefs.aiMode(ctx) == 1
            val temp = if (accurate) 0.3 else 0.7
            val msgs = history.takeLast(20)
            val stream = toMain(onPartial)
            var result: String? = null

            if (looksLikeAction(text)) {
                val g = gen(ctx, key, withTools(baseBody(ctx, msgs, 0.1)), null)
                if (g.code in 200..299) {
                    if (g.commands.isNotEmpty()) result = runCommands(ctx, g, capture)
                    else if (g.text.isNotBlank()) result = g.text.trim()
                }
            }
            if (result == null && accurate && needsFresh(text)) {
                val g = gen(ctx, key, withSearch(baseBody(ctx, msgs, temp)), stream)
                if (g.code in 200..299 && g.text.isNotBlank()) result = withSources(g)
            }
            if (result == null) {
                val g = gen(ctx, key, baseBody(ctx, msgs, temp), stream)
                if (g.code in 200..299) {
                    result = g.text.ifBlank { "I can’t answer that one." }
                } else {
                    failed = true
                    result = errorText(g.code)
                }
            }
            result ?: "…"
        } catch (e: Exception) {
            failed = true
            "I couldn't reach Gemini: ${e.message ?: "network error"}"
        }
        if (failed) history.removeAt(history.lastIndex) else history.add("model" to out)
        out
    }

    /** One-off request (summaries, translations, rewriting): does not touch the chat history. */
    suspend fun once(ctx: Context, prompt: String): String = withContext(Dispatchers.IO) {
        val key = Prefs.apiKey(ctx)
        if (key.isBlank()) return@withContext NO_KEY
        try {
            val g = gen(ctx, key, baseBody(ctx, listOf("user" to prompt), 0.3), null)
            if (g.code in 200..299) g.text.ifBlank { "I can’t answer that one." } else errorText(g.code)
        } catch (e: Exception) {
            "I couldn't reach Gemini: ${e.message ?: "network error"}"
        }
    }

    /** Asks about an image (base64 JPEG). */
    suspend fun vision(ctx: Context, prompt: String, jpegBase64: String): String = withContext(Dispatchers.IO) {
        val key = Prefs.apiKey(ctx)
        if (key.isBlank()) return@withContext NO_KEY
        try {
            val parts = JSONArray().put(textPart(prompt)).put(
                JSONObject().put(
                    "inline_data",
                    JSONObject().put("mime_type", "image/jpeg").put("data", jpegBase64)
                )
            )
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(textPart(systemPrompt(ctx)))))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
                .put("generationConfig", JSONObject().put("maxOutputTokens", 4096).put("temperature", 0.3))
            val g = gen(ctx, key, body, null)
            if (g.code in 200..299) g.text.ifBlank { "I couldn’t read that image." } else errorText(g.code)
        } catch (e: Exception) {
            "I couldn't reach Gemini: ${e.message ?: "network error"}"
        }
    }

    /** When no installed app matches a spoken name, let the AI choose from the installed apps. */
    suspend fun pickApp(ctx: Context, request: String, labels: List<String>): String? = withContext(Dispatchers.IO) {
        val key = Prefs.apiKey(ctx)
        if (key.isBlank() || labels.isEmpty()) return@withContext null
        try {
            val prompt = "The user wants to open an app on their phone and said: \"$request\".\n" +
                "Installed apps: ${labels.joinToString(" | ")}\n" +
                "Reply with exactly one app name from the list that best matches, or NONE if nothing fits. No other words."
            val body = JSONObject()
                .put("contents", contents(listOf("user" to prompt)))
                .put("generationConfig", JSONObject().put("maxOutputTokens", 512).put("temperature", 0.0))
            val g = gen(ctx, key, body, null)
            if (g.code !in 200..299) return@withContext null
            val t = g.text.trim().trim('"', '.', ' ', '\n')
            labels.firstOrNull { it.equals(t, ignoreCase = true) }
        } catch (e: Exception) {
            null
        }
    }
}
