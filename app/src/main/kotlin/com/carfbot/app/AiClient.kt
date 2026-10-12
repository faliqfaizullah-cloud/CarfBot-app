package com.carfbot.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Prefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("carfbot", Context.MODE_PRIVATE)
    /** Gemini API key (free key from Google AI Studio). */
    fun apiKey(ctx: Context): String = p(ctx).getString("gemini_key", "") ?: ""
    fun setApiKey(ctx: Context, k: String) = p(ctx).edit().putString("gemini_key", k.trim()).apply()
    fun backTap(ctx: Context): Boolean = p(ctx).getBoolean("back_tap", false)
    fun setBackTap(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("back_tap", v).apply()
}

/** AI chat through the Google Gemini API (free tier). */
object AiClient {
    /** Tried in order. "gemini-flash-latest" always points at Google's current Flash model. */
    private val MODELS = listOf("gemini-flash-latest", "gemini-3.1-flash-lite")

    private const val SYSTEM =
        "You are CarfBot, a friendly assistant inside an Android app that helps with daily life. " +
        "Keep answers short and practical. The app itself can open apps, call contacts, play songs, " +
        "find files and photos, control music, summarize the clipboard or a screenshot, and chain " +
        "several commands in one sentence."
    private const val NO_KEY =
        "To use AI, add your free Gemini API key in Settings (menu icon). Get one at aistudio.google.com/apikey. " +
        "Device commands work without it."

    /** role is "user" or "model" */
    private val history = mutableListOf<Pair<String, String>>()

    fun hasKey(ctx: Context) = Prefs.apiKey(ctx).isNotBlank()

    private class Resp(val code: Int, val body: String)

    private fun request(key: String, model: String, msgs: List<Pair<String, String>>): Resp {
        val contents = JSONArray()
        msgs.forEach { (role, text) ->
            contents.put(
                JSONObject().put("role", role)
                    .put("parts", JSONArray().put(JSONObject().put("text", text)))
            )
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("maxOutputTokens", 1024))
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 15000; readTimeout = 60000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", key)
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        return Resp(code, raw)
    }

    private fun parse(raw: String): String {
        val root = JSONObject(raw)
        val cands = root.optJSONArray("candidates")
        if (cands == null || cands.length() == 0) return "I can’t answer that one."
        val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
            ?: return "I can’t answer that one."
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        return sb.toString().ifBlank { "…" }
    }

    private fun errorText(code: Int): String = when (code) {
        400, 401, 403 -> "Gemini didn’t accept the API key (error $code). Check it in Settings."
        429 -> "You’ve hit the free Gemini limit for now. Try again in a minute."
        in 500..599 -> "Gemini is busy right now. Try again in a moment."
        else -> "Gemini returned an error ($code)."
    }

    /** Runs the request, falling back to the next model if one is missing or out of quota. */
    private fun run(key: String, msgs: List<Pair<String, String>>): String {
        var last = Resp(0, "")
        for (m in MODELS) {
            last = request(key, m, msgs)
            if (last.code in 200..299) return parse(last.body)
            if (last.code != 404 && last.code != 429) break
        }
        return errorText(last.code)
    }

    private fun ok(reply: String) = !reply.startsWith("Gemini ") && !reply.startsWith("You’ve hit")

    /** Conversation turn with memory of earlier turns. */
    suspend fun ask(ctx: Context, text: String): String = withContext(Dispatchers.IO) {
        val key = Prefs.apiKey(ctx)
        if (key.isBlank()) return@withContext NO_KEY
        history.add("user" to text)
        try {
            val reply = run(key, history.takeLast(20))
            if (ok(reply)) history.add("model" to reply) else history.removeAt(history.lastIndex)
            reply
        } catch (e: Exception) {
            if (history.isNotEmpty()) history.removeAt(history.lastIndex)
            "I couldn't reach Gemini: ${e.message ?: "network error"}"
        }
    }

    /** One-off request (summaries, translations): does not touch the chat history. */
    suspend fun once(ctx: Context, prompt: String): String = withContext(Dispatchers.IO) {
        val key = Prefs.apiKey(ctx)
        if (key.isBlank()) return@withContext NO_KEY
        try {
            run(key, listOf("user" to prompt))
        } catch (e: Exception) {
            "I couldn't reach Gemini: ${e.message ?: "network error"}"
        }
    }
}
