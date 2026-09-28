package com.basitce.hapticbeats.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.basitce.hapticbeats.core.audio.HapticTimeline
import com.basitce.hapticbeats.core.audio.HapticTone
import kotlin.math.pow
import kotlin.math.roundToInt

enum class HapticProfile {
    BALANCED,
    DEEP_BASS,
    PERCUSSION
}

class VibrationManager(context: Context) {

    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun hasVibrator(): Boolean = vibrator.hasVibrator()

    fun hasAmplitudeControl(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.hasAmplitudeControl()
        } else {
            false
        }
    }

    val supportsPrimitives: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK
            )
        } catch (_: Exception) {
            false
        }
    } else {
        false
    }

    /**
     * Plays a discrete instrument tactile primitive.
     * On Android 11+ (API 30+) devices with hardware linear actuators (like Galaxy S24, Pixel),
     * fires native firmware primitives (THUD for Kick, CLICK for Snare, TICK for Hi-hat).
     */
    fun playTone(
        tone: HapticTone,
        intensityScale: Float = 1.0f,
        profile: HapticProfile = HapticProfile.BALANCED
    ) {
        if (!hasVibrator() || tone == HapticTone.SILENCE) return
        val clampedIntensity = intensityScale.coerceIn(0.2f, 1.5f)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && supportsPrimitives) {
            try {
                val composition = VibrationEffect.startComposition()
                when (tone) {
                    HapticTone.KICK -> {
                        val scale = when (profile) {
                            HapticProfile.DEEP_BASS -> 1.0f
                            HapticProfile.BALANCED -> (0.95f * clampedIntensity).coerceIn(0.1f, 1.0f)
                            HapticProfile.PERCUSSION -> (0.80f * clampedIntensity).coerceIn(0.1f, 1.0f)
                        }
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, scale)
                    }
                    HapticTone.SNARE -> {
                        val scale = when (profile) {
                            HapticProfile.PERCUSSION -> 1.0f
                            HapticProfile.BALANCED -> (0.90f * clampedIntensity).coerceIn(0.1f, 1.0f)
                            HapticProfile.DEEP_BASS -> (0.75f * clampedIntensity).coerceIn(0.1f, 1.0f)
                        }
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale)
                    }
                    HapticTone.HIHAT -> {
                        val scale = when (profile) {
                            HapticProfile.PERCUSSION -> (0.70f * clampedIntensity).coerceIn(0.1f, 1.0f)
                            HapticProfile.BALANCED -> (0.50f * clampedIntensity).coerceIn(0.1f, 1.0f)
                            HapticProfile.DEEP_BASS -> (0.35f * clampedIntensity).coerceIn(0.1f, 1.0f)
                        }
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, scale)
                    }
                    HapticTone.BASS_NOTE -> {
                        val scale = when (profile) {
                            HapticProfile.DEEP_BASS -> (0.85f * clampedIntensity).coerceIn(0.1f, 1.0f)
                            HapticProfile.BALANCED -> (0.60f * clampedIntensity).coerceIn(0.1f, 1.0f)
                            HapticProfile.PERCUSSION -> (0.45f * clampedIntensity).coerceIn(0.1f, 1.0f)
                        }
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, scale)
                    }
                    HapticTone.DROP -> {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 1.0f, 0)
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 0)
                    }
                    HapticTone.SILENCE -> return
                }
                vibrator.vibrate(composition.compose())
            } catch (_: Exception) {
                playToneWaveformFallback(tone, clampedIntensity, profile)
            }
        } else {
            playToneWaveformFallback(tone, clampedIntensity, profile)
        }
    }

    private fun playToneWaveformFallback(
        tone: HapticTone,
        clampedIntensity: Float,
        profile: HapticProfile
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val (timings, rawAmps) = when (tone) {
            HapticTone.KICK -> longArrayOf(20L, 35L, 30L) to intArrayOf(255, 180, 80)
            HapticTone.SNARE -> longArrayOf(15L, 20L) to intArrayOf(220, 90)
            HapticTone.HIHAT -> longArrayOf(15L) to intArrayOf(65)
            HapticTone.BASS_NOTE -> longArrayOf(25L) to intArrayOf(110)
            HapticTone.DROP -> longArrayOf(25L, 40L, 35L) to intArrayOf(255, 240, 160)
            HapticTone.SILENCE -> return
        }
        val scaledAmps = IntArray(rawAmps.size) { i ->
            scaleAmplitude(rawAmps[i], clampedIntensity, profile)
        }
        try {
            val effect = VibrationEffect.createWaveform(timings, scaledAmps, -1)
            vibrator.vibrate(effect)
        } catch (_: Exception) {}
    }

    /**
     * Plays the continuous authentic tactile waveform from [startOffsetMs] to the end of the track.
     * Hands the waveform directly to Android's hardware subsystem for zero-latency, stutter-free playback.
     */
    fun playTimeline(
        timeline: HapticTimeline,
        startOffsetMs: Long = 0L,
        intensityScale: Float = 1.0f,
        profile: HapticProfile = HapticProfile.BALANCED
    ) {
        if (!hasVibrator() || timeline.amplitudes.isEmpty()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val stepMs = timeline.stepMs.coerceAtLeast(10)
        val startIndex = (startOffsetMs / stepMs).toInt().coerceAtLeast(0)
        if (startIndex >= timeline.amplitudes.size) {
            cancel()
            return
        }

        val timings = ArrayList<Long>(timeline.amplitudes.size - startIndex + 1)
        val amplitudes = ArrayList<Int>(timeline.amplitudes.size - startIndex + 1)
        timings += 0L
        amplitudes += 0

        var currentAmplitude = scaledAmplitude(timeline.amplitudes[startIndex], intensityScale, profile)
        var currentDuration = stepMs.toLong()

        for (index in (startIndex + 1) until timeline.amplitudes.size) {
            val nextAmplitude = scaledAmplitude(timeline.amplitudes[index], intensityScale, profile)
            if (nextAmplitude == currentAmplitude) {
                currentDuration += stepMs.toLong()
            } else {
                timings += currentDuration
                amplitudes += currentAmplitude
                currentAmplitude = nextAmplitude
                currentDuration = stepMs.toLong()
            }
        }

        timings += currentDuration
        amplitudes += currentAmplitude

        try {
            val effect = VibrationEffect.createWaveform(
                timings.toLongArray(),
                amplitudes.toIntArray(),
                -1
            )
            vibrator.vibrate(effect)
        } catch (_: Exception) {
        }
    }

    /**
     * Plays a sliding window (chunk) of haptics starting from [startOffsetMs] for [windowMs].
     * Guarantees positive timing entries, run-length compression, and supports devices with or without amplitude control.
     */
    fun playWindow(
        timeline: HapticTimeline,
        startOffsetMs: Long,
        windowMs: Long = 2000L,
        intensityScale: Float = 1.0f,
        profile: HapticProfile = HapticProfile.BALANCED
    ) {
        if (!hasVibrator() || timeline.amplitudes.isEmpty()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val stepMs = timeline.stepMs.coerceAtLeast(10)
        val startIndex = (startOffsetMs / stepMs).toInt().coerceAtLeast(0)
        if (startIndex >= timeline.amplitudes.size) {
            cancel()
            return
        }

        val windowCount = (windowMs / stepMs).toInt().coerceAtLeast(2)
        val endIndex = (startIndex + windowCount).coerceAtMost(timeline.amplitudes.size)
        if (startIndex >= endIndex) {
            cancel()
            return
        }

        var hasActivity = false
        for (i in startIndex until endIndex) {
            if (timeline.amplitudes[i] > 0) {
                hasActivity = true
                break
            }
        }

        if (!hasActivity) {
            cancel()
            return
        }

        if (hasAmplitudeControl()) {
            playWaveformWithAmplitude(
                timeline = timeline,
                startIndex = startIndex,
                endIndex = endIndex,
                stepMs = stepMs,
                intensityScale = intensityScale,
                profile = profile
            )
        } else {
            playPwmFallback(
                timeline = timeline,
                startIndex = startIndex,
                endIndex = endIndex,
                stepMs = stepMs,
                intensityScale = intensityScale,
                profile = profile
            )
        }
    }

    private fun playWaveformWithAmplitude(
        timeline: HapticTimeline,
        startIndex: Int,
        endIndex: Int,
        stepMs: Int,
        intensityScale: Float,
        profile: HapticProfile
    ) {
        val timings = ArrayList<Long>()
        val amplitudes = ArrayList<Int>()

        var currentAmplitude = scaledAmplitude(timeline.amplitudes[startIndex], intensityScale, profile)
        var currentDuration = stepMs.toLong()

        for (index in (startIndex + 1) until endIndex) {
            val nextAmplitude = scaledAmplitude(timeline.amplitudes[index], intensityScale, profile)
            if (nextAmplitude == currentAmplitude) {
                currentDuration += stepMs.toLong()
            } else {
                timings += currentDuration
                amplitudes += currentAmplitude
                currentAmplitude = nextAmplitude
                currentDuration = stepMs.toLong()
            }
        }

        timings += currentDuration
        amplitudes += currentAmplitude

        if (timings.isEmpty() || timings.size != amplitudes.size) return

        try {
            val effect = VibrationEffect.createWaveform(
                timings.toLongArray(),
                amplitudes.toIntArray(),
                -1
            )
            vibrator.vibrate(effect)
        } catch (_: Exception) {
        }
    }

    private fun playPwmFallback(
        timeline: HapticTimeline,
        startIndex: Int,
        endIndex: Int,
        stepMs: Int,
        intensityScale: Float,
        profile: HapticProfile
    ) {
        val pattern = ArrayList<Long>()
        pattern += 0L // initial delay

        for (index in startIndex until endIndex) {
            val amp = scaledAmplitude(timeline.amplitudes[index], intensityScale, profile)
            if (amp > 0) {
                val onDuration = when {
                    amp >= 180 -> (stepMs * 0.85).toLong().coerceAtLeast(1)
                    amp >= 100 -> (stepMs * 0.55).toLong().coerceAtLeast(1)
                    else -> (stepMs * 0.30).toLong().coerceAtLeast(1)
                }
                val offDuration = (stepMs - onDuration).coerceAtLeast(1)
                pattern += onDuration
                pattern += offDuration
            } else {
                if (pattern.size > 1 && pattern.size % 2 == 1) {
                    val lastIdx = pattern.lastIndex
                    pattern[lastIdx] = pattern[lastIdx] + stepMs
                } else {
                    pattern += stepMs.toLong()
                }
            }
        }

        if (pattern.size <= 1) return

        try {
            val effect = VibrationEffect.createWaveform(pattern.toLongArray(), -1)
            vibrator.vibrate(effect)
        } catch (_: Exception) {
        }
    }

    fun cancel() {
        try {
            vibrator.cancel()
        } catch (_: Exception) {
        }
    }

    fun scaledAmplitude(
        amplitude: Int,
        intensityScale: Float,
        profile: HapticProfile = HapticProfile.BALANCED
    ): Int = scaleAmplitude(amplitude, intensityScale, profile)

    companion object {
        fun scaleAmplitude(
            amplitude: Int,
            intensityScale: Float,
            profile: HapticProfile = HapticProfile.BALANCED
        ): Int {
            if (amplitude <= 0) return 0
            val clampedIntensity = intensityScale.coerceIn(0.2f, 1.5f)
            val normalized = (amplitude / 255f).coerceIn(0f, 1f)
            val curved = normalized.toDouble().pow(0.85)
            val boosted = (curved * 255 * clampedIntensity * 1.08).roundToInt()

            val rawScaled = when (profile) {
                HapticProfile.BALANCED -> boosted.coerceAtLeast(32)
                HapticProfile.DEEP_BASS -> (boosted * 1.15).roundToInt().coerceAtLeast(45)
                HapticProfile.PERCUSSION -> if (amplitude >= 170) 255 else (boosted * 0.9).roundToInt().coerceAtLeast(30)
            }
            return rawScaled.coerceIn(1, 255)
        }
    }
}
