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
}

/** Chat fallback for anything that isn't a device command. Uses the Anthropic Messages API. */
object AiClient {
    private const val MODEL = "claude-sonnet-5-5"
    private const val SYSTEM =
        "You are CarfBot, a friendly assistant inside an Android app that helps with daily life. " +
        "Keep answers short and practical. The app itself can open apps, call contacts, play songs, " +
        "find files and photos, and search the web when the user phrases a command like " +
        "\"open camera\", \"call Mom\", \"play <song>\" or \"find document <name>\"."

    private val history = mutableListOf<Pair<String, String>>()

    suspend fun ask(ctx: Context, text: String): String = withContext(Dispatchers.IO) {
        val key = Prefs.apiKey(ctx)
        if (key.isBlank()) {
            return@withContext "To chat with me, add your Anthropic API key in Settings (top right). " +
                "Commands like \"open camera\" work without it."
        }
        history.add("user" to text)
        try {
            val msgs = JSONArray()
            history.takeLast(20).forEach { msgs.put(JSONObject().put("role", it.first).put("content", it.second)) }
            val body = JSONObject()
                .put("model", MODEL).put("max_tokens", 1024)
                .put("system", SYSTEM).put("messages", msgs)

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
            if (code !in 200..299) {
                history.removeAt(history.lastIndex)
                return@withContext "The AI service returned an error ($code). Check your API key in Settings."
            }
            val parts = JSONObject(raw).getJSONArray("content")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.optString("type") == "text") sb.append(part.getString("text"))
            }
            val reply = sb.toString().ifBlank { "…" }
            history.add("assistant" to reply)
            reply
        } catch (e: Exception) {
            if (history.isNotEmpty()) history.removeAt(history.lastIndex)
            "I couldn't reach the AI: ${e.message ?: "network error"}"
        }
    }
}
