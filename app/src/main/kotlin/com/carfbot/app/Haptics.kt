package com.carfbot.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Small haptic vocabulary used across the app and the assistant overlay. */
object Haptics {
    private fun vib(ctx: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31)
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } catch (e: Exception) { null }

    private fun play(ctx: Context, effect: Int, ms: Long, amp: Int) {
        val v = vib(ctx) ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(effect))
            else v.vibrate(VibrationEffect.createOneShot(ms, amp))
        } catch (e: Exception) { }
    }

    /** Light tap: buttons, sending, mic toggle. */
    fun tick(ctx: Context) = play(ctx, VibrationEffect.EFFECT_TICK, 12, 80)

    /** Medium tap: a reply arrived. */
    fun click(ctx: Context) = play(ctx, VibrationEffect.EFFECT_CLICK, 25, 150)

    /** Strong tap: an app, contact, song or file was opened. */
    fun confirm(ctx: Context) = play(ctx, VibrationEffect.EFFECT_HEAVY_CLICK, 45, 255)

    /** Double tap: something went wrong. */
    fun reject(ctx: Context) = play(ctx, VibrationEffect.EFFECT_DOUBLE_CLICK, 60, 200)

    /** Rising pulse when the assistant is summoned (power button hold). */
    fun summon(ctx: Context) {
        val v = vib(ctx) ?: return
        if (!v.hasVibrator()) return
        try {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 16, 36, 22, 30, 40), intArrayOf(0, 70, 0, 150, 0, 255), -1))
        } catch (e: Exception) { }
    }
}
