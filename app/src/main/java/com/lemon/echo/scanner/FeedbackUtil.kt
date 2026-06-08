package com.lemon.echo.scanner

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Play vibration and/or sound feedback. Both enabled by default. */
fun playFeedback(context: Context, sound: Boolean = true, vibration: Boolean = true) {
    if (vibration) vibrate(context)
    if (sound) playBeep()
}

private fun vibrate(context: Context) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
        Handler(Looper.getMainLooper()).post { vibrate(context) }
        return
    }
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_COMMUNICATION_REQUEST)
                    .build()
                v.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE), attrs)
                return@let
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                v.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                return@let
            }
            v.vibrate(VibrationEffect.createOneShot(120, 128))
        }
    } catch (_: Exception) {
        try {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(longArrayOf(0, 120), -1)
        } catch (_: Exception) {
        }
    }
}

private fun playBeep() {
    Thread {
        try {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            generator.startTone(ToneGenerator.TONE_PROP_ACK, 200)
            Thread.sleep(300)
            generator.release()
        } catch (_: Exception) {
        }
    }.start()
}
