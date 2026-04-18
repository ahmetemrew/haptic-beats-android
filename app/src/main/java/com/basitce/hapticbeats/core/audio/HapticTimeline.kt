package com.basitce.hapticbeats.core.audio

const val HAPTIC_TIMELINE_STEP_MS = 20
const val CURRENT_ANALYSIS_VERSION = 3

data class HapticTimeline(
    val durationMs: Long,
    val amplitudes: IntArray,
    val stepMs: Int = HAPTIC_TIMELINE_STEP_MS
) {
    fun isEmpty(): Boolean = amplitudes.isEmpty() || amplitudes.all { it <= 0 }

    fun previewBars(count: Int = 36): List<Int> {
        if (amplitudes.isEmpty() || count <= 0) return emptyList()
        val bucketSize = (amplitudes.size.toDouble() / count).coerceAtLeast(1.0)
        return List(count) { index ->
            val start = (index * bucketSize).toInt().coerceAtMost(amplitudes.lastIndex)
            val endExclusive = ((index + 1) * bucketSize).toInt().coerceAtMost(amplitudes.size)
            var maxAmplitude = 0
            for (sampleIndex in start until endExclusive) {
                maxAmplitude = maxOf(maxAmplitude, amplitudes[sampleIndex])
            }
            maxAmplitude
        }
    }
}
