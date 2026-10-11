package com.carfbot.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Registers CarfBot under Settings > Apps > Default apps > Digital assistant app.
 * Also hosts the experimental "double-tap the back of the phone" trigger: while the screen is on and
 * the option is enabled, two sharp taps in quick succession open the assistant overlay.
 */
class CarfVoiceService : VoiceInteractionService(), SensorEventListener {
    private var sm: SensorManager? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var prefListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var registered = false

    private var last = 0f
    private var lastSpike = 0L
    private var firstTap = 0L
    private var taps = 0
    private var cooldownUntil = 0L

    override fun onReady() {
        super.onReady()
        sm = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { refresh() }
        }
        screenReceiver = receiver
        ContextCompat.registerReceiver(
            this, receiver,
            IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "back_tap") refresh()
        }
        prefListener = listener
        getSharedPreferences("carfbot", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener)
        refresh()
    }

    override fun onShutdown() {
        stopSensor()
        try { screenReceiver?.let { unregisterReceiver(it) } } catch (e: Exception) { }
        prefListener?.let { getSharedPreferences("carfbot", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(it) }
        super.onShutdown()
    }

    private fun refresh() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Prefs.backTap(this) && pm.isInteractive) startSensor() else stopSensor()
    }

    private fun startSensor() {
        if (registered) return
        val s = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sm?.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
        registered = true
    }

    private fun stopSensor() {
        if (!registered) return
        sm?.unregisterListener(this)
        registered = false
        taps = 0
    }

    override fun onSensorChanged(e: SensorEvent) {
        val m = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
        val d = abs(m - last)
        last = m
        val now = SystemClock.elapsedRealtime()
        if (now < cooldownUntil) return
        if (d > 5.5f && now - lastSpike > 90) {
            lastSpike = now
            if (taps == 1 && now - firstTap in 100..500) {
                taps = 0
                cooldownUntil = now + 2500
                trigger()
            } else {
                taps = 1
                firstTap = now
            }
        } else if (taps == 1 && now - firstTap > 500) {
            taps = 0
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun trigger() {
        Haptics.tick(this)
        try { showSession(Bundle(), VoiceInteractionSession.SHOW_WITH_ASSIST) } catch (e: Exception) { }
    }
}

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
