package com.carfbot.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/** Registers CarfBot under Settings > Apps > Default apps > Digital assistant app. */
class CarfVoiceService : VoiceInteractionService()

class CarfSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = CarfSession(this)
}

/** Fires when the user holds the power button / long-presses Home. */
class CarfSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        current = this
        val i = Intent(context, AssistantActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            startAssistantActivity(i)
        } catch (e: Exception) {
            try { context.startActivity(i) } catch (e2: Exception) { hide() }
        }
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    companion object {
        /** The overlay calls hide() on this when it closes so the next power-button hold works. */
        @Volatile var current: CarfSession? = null
    }
}

/** Required by the assistant framework; real speech capture happens in the overlay (VoiceController). */
class CarfRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }
    override fun onCancel(listener: Callback?) {}
    override fun onStopListening(listener: Callback?) {}
}
