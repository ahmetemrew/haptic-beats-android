package com.basitce.hapticbeats.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.basitce.hapticbeats.core.audio.HapticTimeline
import kotlin.math.pow
import kotlin.math.roundToInt

class VibrationManager(context: Context) {

    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun hasVibrator(): Boolean = vibrator.hasVibrator()

    fun playTimeline(
        timeline: HapticTimeline,
        startOffsetMs: Long = 0L,
        intensityScale: Float = 1.0f
    ) {
        if (!hasVibrator() || timeline.amplitudes.isEmpty()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val startIndex = (startOffsetMs / timeline.stepMs).toInt().coerceAtLeast(0)
        if (startIndex >= timeline.amplitudes.size) {
            cancel()
            return
        }

        val timings = ArrayList<Long>(timeline.amplitudes.size - startIndex + 1)
        val amplitudes = ArrayList<Int>(timeline.amplitudes.size - startIndex + 1)
        timings += 0L
        amplitudes += 0

        var currentAmplitude = scaledAmplitude(timeline.amplitudes[startIndex], intensityScale)
        var currentDuration = timeline.stepMs.toLong()

        for (index in (startIndex + 1) until timeline.amplitudes.size) {
            val nextAmplitude = scaledAmplitude(timeline.amplitudes[index], intensityScale)
            if (nextAmplitude == currentAmplitude) {
                currentDuration += timeline.stepMs.toLong()
            } else {
                timings += currentDuration
                amplitudes += currentAmplitude
                currentAmplitude = nextAmplitude
                currentDuration = timeline.stepMs.toLong()
            }
        }

        timings += currentDuration
        amplitudes += currentAmplitude

        val effect = VibrationEffect.createWaveform(
            timings.toLongArray(),
            amplitudes.toIntArray(),
            -1
        )
        vibrator.vibrate(effect)
    }

    fun cancel() {
        vibrator.cancel()
    }

    private fun scaledAmplitude(amplitude: Int, intensityScale: Float): Int {
        if (amplitude <= 0) return 0
        val normalized = (amplitude / 255f).coerceIn(0f, 1f)
        val curved = normalized.toDouble().pow(0.88)
        val boosted = (curved * 255 * intensityScale * 1.08).roundToInt()
        return boosted
            .coerceAtLeast(32)
            .coerceIn(1, 255)
    }
}
