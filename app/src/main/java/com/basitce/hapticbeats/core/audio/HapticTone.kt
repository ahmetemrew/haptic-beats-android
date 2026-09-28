package com.basitce.hapticbeats.core.audio

/**
 * Discrete instrument tactile classification for hardware-accelerated haptic rendering.
 * Maps directly to Android linear resonant actuator (LRA) primitives on supported devices.
 */
enum class HapticTone(val code: Byte) {
    SILENCE(0),
    KICK(1),       // Deep visceral bass thump -> PRIMITIVE_THUD
    SNARE(2),      // Sharp mechanical crack -> PRIMITIVE_CLICK
    HIHAT(3),      // Crisp micro-click -> PRIMITIVE_TICK
    BASS_NOTE(4),  // Melodic bass / tom -> PRIMITIVE_LOW_TICK
    DROP(5);       // Drop / Riser overdrive -> QUICK_RISE + THUD

    companion object {
        fun fromCode(code: Byte): HapticTone = when (code.toInt()) {
            1 -> KICK
            2 -> SNARE
            3 -> HIHAT
            4 -> BASS_NOTE
            5 -> DROP
            else -> SILENCE
        }
    }
}

/**
 * High-level musical tactile event extracted by AudioAnalyzer.
 */
data class HapticEvent(
    val type: HapticTone,
    val startMs: Long,
    val intensity: Float, // 0.0f..1.0f
    val durationMs: Long = 0L,
    val modHz: Float? = null
)

