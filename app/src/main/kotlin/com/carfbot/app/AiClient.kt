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
    fun apiKey(ctx: Context): String = p(ctx).getString("api_key", "") ?: ""
    fun setApiKey(ctx: Context, k: String) = p(ctx).edit().putString("api_key", k.trim()).apply()
    fun backTap(ctx: Context): Boolean = p(ctx).getBoolean("back_tap", false)
    fun setBackTap(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("back_tap", v).apply()
}

/** Chat fallback for anything that isn't a device command. Uses the Anthropic Messages API. */
object AiClient {
    private const val MODEL = "claude-sonnet-5-5"
    private const val SYSTEM =
        "You are CarfBot, a friendly assistant inside an Android app that helps with daily life. " +
        "Keep answers short and practical. The app itself can open apps, call contacts, play songs, " +
        "find files and photos, control music, summarize the clipboard or a screenshot, and chain " +
        "several commands in one sentence."
    private const val NO_KEY =
        "To use AI, add your Anthropic API key in Settings (gear icon). Device commands work without it."

    private val history = mutableListOf<Pair<String, String>>()

    fun hasKey(ctx: Context) = Prefs.apiKey(ctx).isNotBlank()

    private fun request(key: String, msgs: List<Pair<String, String>>, system: String): Pair<Int, String> {
        val arr = JSONArray()
        msgs.forEach { arr.put(JSONObject().put("role", it.first).put("content", it.second)) }
        val body = JSONObject()
            .put("model", MODEL).put("max_tokens", 1024)
            .put("system", system).put("messages", arr)
        val conn = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 15000; readTimeout = 60000
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", key)
            setRequestProperty("anthropic-version", "2023-06-01")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader().use { it.readText() }
        return code to raw
    }

    private fun parse(raw: String): String {
        val parts = JSONObject(raw).getJSONArray("content")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            if (part.optString("type") == "text") sb.append(part.getString("text"))
        }
        return sb.toString().ifBlank { "…" }
    }

    /** Conversation turn with memory of earlier turns. */
    suspend fun ask(ctx: Context, text: String): String = withContext(Dispatchers.IO) {
        val key = Prefs.apiKey(ctx)
        if (key.isBlank()) return@withContext NO_KEY
        history.add("user" to text)
        try {
            val (code, raw) = request(key, history.takeLast(20), SYSTEM)
            if (code !in 200..299) {
                history.removeAt(history.lastIndex)
                return@withContext "The AI service returned an error ($code). Check your API key in Settings."
            }
            val reply = parse(raw)
            history.add("assistant" to reply)
            reply
        } catch (e: Exception) {
            if (history.isNotEmpty()) history.removeAt(history.lastIndex)
            "I couldn't reach the AI: ${e.message ?: "network error"}"
        }
    }

    /** One-off request (summaries, translations): does not touch the chat history. */
    suspend fun once(ctx: Context, prompt: String): String = withContext(Dispatchers.IO) {
        val key = Prefs.apiKey(ctx)
        if (key.isBlank()) return@withContext NO_KEY
        try {
            val (code, raw) = request(key, listOf("user" to prompt), SYSTEM)
            if (code !in 200..299) "The AI service returned an error ($code). Check your API key in Settings."
            else parse(raw)
        } catch (e: Exception) {
            "I couldn't reach the AI: ${e.message ?: "network error"}"
        }
    }
}
