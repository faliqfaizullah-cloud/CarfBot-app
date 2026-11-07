package com.carfbot.app

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Reads answers aloud with the phone's own text-to-speech voice. */
object Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    fun speak(ctx: Context, text: String) {
        val clean = text.replace(Regex("[*_#`•]"), "").replace("\n", ". ").take(700)
        if (clean.isBlank()) return
        if (tts == null) {
            pending = clean
            tts = TextToSpeech(ctx.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.getDefault()
                    ready = true
                    pending?.let { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "carfbot") }
                    pending = null
                }
            }
            return
        }
        if (ready) tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "carfbot") else pending = clean
    }

    fun stop() {
        pending = null
        tts?.stop()
    }
}
