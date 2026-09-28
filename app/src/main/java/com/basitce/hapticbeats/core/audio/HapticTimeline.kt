package com.basitce.hapticbeats.core.audio

const val HAPTIC_TIMELINE_STEP_MS = 20
const val CURRENT_ANALYSIS_VERSION = 9

data class HapticTimeline(
    val durationMs: Long,
    val amplitudes: IntArray,
    val tones: ByteArray = ByteArray(amplitudes.size),
    val stepMs: Int = HAPTIC_TIMELINE_STEP_MS
) {
    fun isEmpty(): Boolean = amplitudes.isEmpty() || amplitudes.all { it <= 0 }

    fun amplitudeAt(positionMs: Long): Int {
        if (positionMs < 0 || amplitudes.isEmpty()) return 0
        val index = (positionMs / stepMs).toInt()
        return amplitudes.getOrElse(index) { 0 }
    }

    fun toneAt(positionMs: Long): HapticTone {
        if (positionMs < 0 || tones.isEmpty()) return HapticTone.SILENCE
        val index = (positionMs / stepMs).toInt()
        val code = tones.getOrElse(index) { 0 }
        return HapticTone.fromCode(code)
    }

    /**
     * Finds the nearest silence point (amplitude == 0) around targetMs within searchWindowMs.
     * Used for seamless chunk handovers without motor clicks or HAL interruptions.
     */
    fun findSeamlessHandoverMs(targetMs: Long, searchWindowMs: Long = 400L): Long {
        if (amplitudes.isEmpty()) return targetMs
        val targetIdx = (targetMs / stepMs).toInt().coerceIn(0, amplitudes.lastIndex)
        val windowIndices = (searchWindowMs / stepMs).toInt()
        val minIdx = (targetIdx - windowIndices / 2).coerceAtLeast(0)
        val maxIdx = (targetIdx + windowIndices / 2).coerceAtMost(amplitudes.lastIndex)

        for (i in minIdx..maxIdx) {
            if (amplitudes[i] == 0) {
                return i.toLong() * stepMs
            }
        }
        return targetMs
    }

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HapticTimeline

        if (durationMs != other.durationMs) return false
        if (stepMs != other.stepMs) return false
        if (!amplitudes.contentEquals(other.amplitudes)) return false
        if (!tones.contentEquals(other.tones)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = durationMs.hashCode()
        result = 31 * result + amplitudes.contentHashCode()
        result = 31 * result + tones.contentHashCode()
        result = 31 * result + stepMs
        return result
    }
}
