package com.basitce.hapticbeats

import com.basitce.hapticbeats.core.audio.HapticTimeline
import com.basitce.hapticbeats.core.haptics.HapticProfile
import com.basitce.hapticbeats.core.haptics.VibrationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticEngineUnitTest {

    @Test
    fun hapticTimeline_amplitudeAt_returnsCorrectAmplitudes() {
        // Step size is 20ms
        val amplitudes = intArrayOf(0, 50, 100, 200, 255, 0)
        val timeline = HapticTimeline(durationMs = 120L, amplitudes = amplitudes, stepMs = 20)

        assertEquals(0, timeline.amplitudeAt(-10L))
        assertEquals(0, timeline.amplitudeAt(0L))
        assertEquals(50, timeline.amplitudeAt(20L))
        assertEquals(100, timeline.amplitudeAt(45L))
        assertEquals(200, timeline.amplitudeAt(60L))
        assertEquals(255, timeline.amplitudeAt(80L))
        assertEquals(0, timeline.amplitudeAt(100L))
        assertEquals(0, timeline.amplitudeAt(500L)) // Out of bounds
    }

    @Test
    fun hapticTimeline_findSeamlessHandoverMs_findsSilencePoint() {
        // 0ms..20ms: 255, 20ms..40ms: 200, 40ms..60ms: 0, 60ms..80ms: 180
        val amplitudes = IntArray(100) { 150 }
        amplitudes[50] = 0 // At 1000ms (50 * 20ms = 1000ms), there is silence

        val timeline = HapticTimeline(durationMs = 2000L, amplitudes = amplitudes, stepMs = 20)

        // Searching around 1020ms with 400ms search window should find 1000ms
        val seamlessMs = timeline.findSeamlessHandoverMs(targetMs = 1020L, searchWindowMs = 400L)
        assertEquals(1000L, seamlessMs)
    }

    @Test
    fun vibrationManager_scaleAmplitude_balancedProfile() {
        // Silence remains 0
        assertEquals(0, VibrationManager.scaleAmplitude(0, 1.0f, HapticProfile.BALANCED))

        // Heavy Kick (255) scales to 255 at 1.0f intensity
        val kickAmp = VibrationManager.scaleAmplitude(255, 1.0f, HapticProfile.BALANCED)
        assertEquals(255, kickAmp)

        // Snare (215) is punchy
        val snareAmp = VibrationManager.scaleAmplitude(215, 1.0f, HapticProfile.BALANCED)
        assertTrue("Snare amplitude should be strong (between 200 and 240)", snareAmp in 200..240)

        // Hi-hat (65) is a crisp tick
        val hiHatAmp = VibrationManager.scaleAmplitude(65, 1.0f, HapticProfile.BALANCED)
        assertTrue("Hi-hat amplitude should be between 70 and 95", hiHatAmp in 70..95)
    }

    @Test
    fun vibrationManager_scaleAmplitude_deepBassProfile() {
        // In Deep Bass profile, bass is heavily amplified compared to balanced
        val balancedBass = VibrationManager.scaleAmplitude(140, 1.0f, HapticProfile.BALANCED)
        val deepBass = VibrationManager.scaleAmplitude(140, 1.0f, HapticProfile.DEEP_BASS)
        assertTrue("Deep bass profile should boost bass higher than balanced", deepBass > balancedBass)

        // Heavy bass >= 205 saturates cleanly to 255
        val heavyBass = VibrationManager.scaleAmplitude(205, 1.0f, HapticProfile.DEEP_BASS)
        assertEquals(255, heavyBass)
    }

    @Test
    fun vibrationManager_scaleAmplitude_percussionProfile() {
        // In Percussion profile, transients >= 170 saturate to 255
        val snareAmp = VibrationManager.scaleAmplitude(175, 1.0f, HapticProfile.PERCUSSION)
        val hiHatAmp = VibrationManager.scaleAmplitude(65, 1.0f, HapticProfile.PERCUSSION)

        assertEquals(255, snareAmp)
        assertTrue("Hi-hat should be crisp and audible", hiHatAmp in 30..120)
    }

    @Test
    fun hapticTimeline_toneAt_returnsCorrectTone() {
        val amplitudes = intArrayOf(255, 200, 70, 150)
        val tones = byteArrayOf(
            com.basitce.hapticbeats.core.audio.HapticTone.KICK.code,
            com.basitce.hapticbeats.core.audio.HapticTone.SNARE.code,
            com.basitce.hapticbeats.core.audio.HapticTone.HIHAT.code,
            com.basitce.hapticbeats.core.audio.HapticTone.BASS_NOTE.code
        )
        val timeline = HapticTimeline(durationMs = 80L, amplitudes = amplitudes, tones = tones, stepMs = 20)

        assertEquals(com.basitce.hapticbeats.core.audio.HapticTone.KICK, timeline.toneAt(0L))
        assertEquals(com.basitce.hapticbeats.core.audio.HapticTone.SNARE, timeline.toneAt(20L))
        assertEquals(com.basitce.hapticbeats.core.audio.HapticTone.HIHAT, timeline.toneAt(40L))
        assertEquals(com.basitce.hapticbeats.core.audio.HapticTone.BASS_NOTE, timeline.toneAt(60L))
        assertEquals(com.basitce.hapticbeats.core.audio.HapticTone.SILENCE, timeline.toneAt(-5L))
        assertEquals(com.basitce.hapticbeats.core.audio.HapticTone.SILENCE, timeline.toneAt(500L))
    }

    @Test
    fun hapticTone_fromCode_handlesAllCodesAndDefaultsToSilence() {
        assertEquals(com.basitce.hapticbeats.core.audio.HapticTone.SILENCE, com.basitce.hapticbeats.core.audio.HapticTone.fromCode(0))
        assertEquals(com.basitce.hapticbeats.core.audio.HapticTone.KICK, com.basitce.hapticbeats.core.audio.HapticTone.fromCode(1))
        assertEquals(com.basitce.hapticbeats.core.audio.HapticTone.SNARE, com.basitce.hapticbeats.core.audio.HapticTone.fromCode(2))
        assertEquals(com.basitce.hapticbeats.core.audio.HapticTone.HIHAT, com.basitce.hapticbeats.core.audio.HapticTone.fromCode(3))
        assertEquals(com.basitce.hapticbeats.core.audio.HapticTone.BASS_NOTE, com.basitce.hapticbeats.core.audio.HapticTone.fromCode(4))
        assertEquals(com.basitce.hapticbeats.core.audio.HapticTone.DROP, com.basitce.hapticbeats.core.audio.HapticTone.fromCode(5))
        assertEquals(com.basitce.hapticbeats.core.audio.HapticTone.SILENCE, com.basitce.hapticbeats.core.audio.HapticTone.fromCode(99))
    }

    @Test
    fun vibrationManager_scaleAmplitude_overdrivePower() {
        // At 1.5f intensity overdrive, strong hits saturate cleanly at 255
        val overdriven = VibrationManager.scaleAmplitude(180, 1.5f, HapticProfile.BALANCED)
        assertEquals(255, overdriven)

        // At 0.5f intensity, amplitude is scaled down gently
        val quiet = VibrationManager.scaleAmplitude(180, 0.5f, HapticProfile.BALANCED)
        assertTrue("Quiet scaling should be within 90..140", quiet in 90..140)
    }
}
