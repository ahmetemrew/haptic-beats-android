package com.basitce.hapticbeats.core.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jtransforms.fft.DoubleFFT_1D
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class AudioAnalyzer(private val context: Context) {

    suspend fun analyzeAudio(uri: Uri): HapticTimeline = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (_: Exception) {
            return@withContext HapticTimeline(durationMs = 0L, amplitudes = IntArray(0))
        }

        var selectedFormat: MediaFormat? = null
        var selectedMime: String? = null
        var durationUs = 0L

        for (trackIndex in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(trackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) {
                extractor.selectTrack(trackIndex)
                selectedFormat = trackFormat
                selectedMime = mime
                durationUs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
                    trackFormat.getLong(MediaFormat.KEY_DURATION)
                } else {
                    0L
                }
                break
            }
        }

        val format = selectedFormat
        val mime = selectedMime
        if (format == null || mime.isNullOrBlank()) {
            extractor.release()
            return@withContext HapticTimeline(durationMs = 0L, amplitudes = IntArray(0))
        }

        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        val stepSamples = ((sampleRate * HAPTIC_TIMELINE_STEP_MS) / 1000).coerceAtLeast(1)
        val windowSize = nextPowerOfTwo((sampleRate * 0.05).roundToInt().coerceAtLeast(stepSamples * 3))
        val hopSize = stepSamples.coerceAtMost(windowSize / 2)

        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (_: Exception) {
            extractor.release()
            return@withContext HapticTimeline(durationMs = durationUs / 1000, amplitudes = IntArray(0))
        }

        val hannWindow = DoubleArray(windowSize) { index ->
            0.5 - (0.5 * cos((2 * PI * index) / (windowSize - 1)))
        }
        val fftBuffer = DoubleArray(windowSize * 2)
        val waveformBuffer = DoubleArray(windowSize)
        val previousMagnitudes = DoubleArray(windowSize / 2)
        val lowEnergyHistory = ArrayDeque<Double>(64)
        val pulseFluxHistory = ArrayDeque<Double>(64)
        val percussionFluxHistory = ArrayDeque<Double>(64)
        val vocalEnergyHistory = ArrayDeque<Double>(64)
        val rmsHistory = ArrayDeque<Double>(64)
        val amplitudes = mutableListOf<Int>()
        val fft = DoubleFFT_1D(windowSize.toLong())

        var bufferedSamples = 0
        var grooveHold = 0.0
        var refractorySteps = 0
        var previousAmplitude = 0

        codec.configure(format, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false

        fun analyzeWindow(): Int {
            var rmsAccumulator = 0.0
            for (index in 0 until windowSize) {
                val sample = waveformBuffer[index]
                rmsAccumulator += sample * sample
                fftBuffer[index * 2] = sample * hannWindow[index]
                fftBuffer[(index * 2) + 1] = 0.0
            }

            fft.complexForward(fftBuffer)

            var subBassEnergy = 0.0
            var bassEnergy = 0.0
            var percussionEnergy = 0.0
            var vocalEnergy = 0.0
            var airEnergy = 0.0
            var subBassFlux = 0.0
            var bassFlux = 0.0
            var percussionFlux = 0.0
            var vocalFlux = 0.0
            var airFlux = 0.0
            var totalEnergy = 0.0

            for (bin in 1 until windowSize / 2) {
                val real = fftBuffer[bin * 2]
                val imaginary = fftBuffer[(bin * 2) + 1]
                val magnitude = sqrt((real * real) + (imaginary * imaginary))
                val energy = magnitude * magnitude
                val positiveDelta = (magnitude - previousMagnitudes[bin]).coerceAtLeast(0.0)
                previousMagnitudes[bin] = magnitude

                val frequency = (bin.toDouble() * sampleRate) / windowSize
                totalEnergy += energy

                when {
                    frequency < 55.0 -> {
                        subBassEnergy += energy
                        subBassFlux += positiveDelta
                    }
                    frequency < 140.0 -> {
                        bassEnergy += energy
                        bassFlux += positiveDelta
                    }
                    frequency < 420.0 -> {
                        percussionEnergy += energy
                        percussionFlux += positiveDelta
                    }
                    frequency < 2_200.0 -> {
                        vocalEnergy += energy
                        vocalFlux += positiveDelta
                    }
                    else -> {
                        airEnergy += energy
                        airFlux += positiveDelta
                    }
                }
            }

            val lowEnergy = (subBassEnergy * 1.35) + (bassEnergy * 1.18) + (percussionEnergy * 0.55)
            val pulseFlux = (subBassFlux * 1.45) + (bassFlux * 1.22) + (percussionFlux * 0.72)
            val percussionLift = (bassFlux * 0.78) + percussionFlux
            val vocalMaskEnergy = vocalEnergy + (airEnergy * 0.35)
            val vocalMaskFlux = vocalFlux + (airFlux * 0.25)
            val lowShare = lowEnergy / totalEnergy.coerceAtLeast(1e-9)
            val vocalDominance = vocalMaskEnergy / (lowEnergy + percussionEnergy + 1e-9)
            val rms = sqrt(rmsAccumulator / windowSize)

            val lowDrive = (lowEnergy / referenceMean(lowEnergyHistory, lowEnergy)).coerceIn(0.0, 2.5)
            val pulseOnset = normalizedSpike(pulseFlux, pulseFluxHistory, 1.85)
            val percussionAccent = normalizedSpike(percussionLift, percussionFluxHistory, 2.05)
            val rmsDrive = (rms / referenceMean(rmsHistory, rms)).coerceIn(0.0, 1.9)
            val lowShareBoost = ((lowShare - 0.08) / 0.30).coerceIn(0.0, 1.1)

            val vocalPenalty = when {
                vocalDominance > 1.10 && lowDrive < 0.95 && pulseOnset < 0.80 -> 0.42
                vocalDominance > 0.92 && vocalMaskFlux > pulseFlux * 0.84 -> 0.58
                lowShare < 0.12 && vocalDominance > 0.76 -> 0.72
                else -> 1.0
            }

            grooveHold = max((lowDrive * 0.88) + (lowShareBoost * 0.24), grooveHold * 0.86)

            var score =
                (grooveHold * 0.50) +
                    (lowDrive * 0.38) +
                    (pulseOnset * 0.92) +
                    (percussionAccent * 0.34) +
                    (rmsDrive * 0.14)

            score *= vocalPenalty

            if (lowDrive > 1.08 && pulseOnset > 0.86) {
                score += 0.22
            }
            if (lowShareBoost < 0.12 && percussionAccent < 0.35) {
                score *= 0.58
            }
            if (refractorySteps > 0 && score < 1.2) {
                score *= 0.72
            }

            score = score.coerceIn(0.0, 1.6)
            var amplitude = when {
                score < 0.20 -> 0
                else -> (52 + (score.pow(1.12) * 203)).roundToInt().coerceIn(0, 255)
            }

            if (amplitude > 0 && previousAmplitude > 0) {
                amplitude = max(amplitude, (previousAmplitude * 0.76).roundToInt())
            }
            if (amplitude in 1..72 && pulseOnset < 0.28 && lowDrive < 0.90 && lowShareBoost < 0.22) {
                amplitude = 0
            }

            pushHistory(lowEnergyHistory, lowEnergy)
            pushHistory(pulseFluxHistory, pulseFlux)
            pushHistory(percussionFluxHistory, percussionLift)
            pushHistory(vocalEnergyHistory, vocalMaskEnergy)
            pushHistory(rmsHistory, rms)

            previousAmplitude = amplitude
            refractorySteps = when {
                amplitude >= 180 -> 2
                amplitude >= 130 -> 1
                refractorySteps > 0 -> refractorySteps - 1
                else -> 0
            }
            return amplitude
        }

        try {
            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inputBufferIndex = codec.dequeueInputBuffer(5_000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                        val sampleSize = extractor.readSampleData(inputBuffer ?: return@withContext HapticTimeline(0, IntArray(0)), 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime,
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 5_000)
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    continue
                }
                if (outputBufferIndex < 0) {
                    continue
                }

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEos = true
                }

                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    val shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val decodedSamples = ShortArray(shortBuffer.remaining())
                    shortBuffer.get(decodedSamples)

                    var index = 0
                    while (index + channelCount <= decodedSamples.size) {
                        var monoSample = 0.0
                        for (channelIndex in 0 until channelCount) {
                            monoSample += decodedSamples[index + channelIndex] / 32768.0
                        }
                        monoSample /= channelCount
                        waveformBuffer[bufferedSamples++] = monoSample
                        index += channelCount

                        if (bufferedSamples == windowSize) {
                            amplitudes += analyzeWindow()
                            val retainedSamples = windowSize - hopSize
                            System.arraycopy(
                                waveformBuffer,
                                hopSize,
                                waveformBuffer,
                                0,
                                retainedSamples
                            )
                            bufferedSamples = retainedSamples
                        }
                    }
                }
                codec.releaseOutputBuffer(outputBufferIndex, false)
            }

            if (bufferedSamples > hopSize) {
                while (bufferedSamples > hopSize) {
                    for (index in bufferedSamples until windowSize) {
                        waveformBuffer[index] = 0.0
                    }
                    amplitudes += analyzeWindow()
                    val retainedSamples = (bufferedSamples - hopSize).coerceAtLeast(0)
                    if (retainedSamples > 0) {
                        System.arraycopy(
                            waveformBuffer,
                            hopSize,
                            waveformBuffer,
                            0,
                            retainedSamples
                        )
                    }
                    bufferedSamples = retainedSamples
                }
            }
        } catch (_: Exception) {
            return@withContext HapticTimeline(durationMs = durationUs / 1000, amplitudes = postProcessAmplitudes(amplitudes))
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { extractor.release() }
        }

        val processedAmplitudes = postProcessAmplitudes(amplitudes)
        val durationMs = (durationUs / 1000).coerceAtLeast((processedAmplitudes.size * HAPTIC_TIMELINE_STEP_MS).toLong())
        val expectedSize = ceil(durationMs / HAPTIC_TIMELINE_STEP_MS.toDouble()).toInt().coerceAtLeast(processedAmplitudes.size)
        val paddedAmplitudes = if (expectedSize > processedAmplitudes.size) {
            IntArray(expectedSize) { index ->
                processedAmplitudes.getOrElse(index) { 0 }
            }
        } else {
            processedAmplitudes
        }

        HapticTimeline(
            durationMs = durationMs,
            amplitudes = paddedAmplitudes,
            stepMs = HAPTIC_TIMELINE_STEP_MS
        )
    }

    private fun nextPowerOfTwo(value: Int): Int {
        var candidate = 1
        while (candidate < value) {
            candidate = candidate shl 1
        }
        return candidate.coerceIn(1024, 4096)
    }

    private fun postProcessAmplitudes(source: List<Int>): IntArray {
        if (source.isEmpty()) return IntArray(0)
        val processed = source.toIntArray()

        for (index in processed.indices) {
            val current = processed[index]
            val previous = processed.getOrElse(index - 1) { 0 }
            val next = processed.getOrElse(index + 1) { 0 }
            if (current in 1..74 && previous < 90 && next < 90) {
                processed[index] = 0
            }
        }

        for (index in 1 until processed.lastIndex) {
            if (processed[index] == 0 && processed[index - 1] >= 145 && processed[index + 1] >= 120) {
                processed[index] = ((minOf(processed[index - 1], processed[index + 1]) * 0.62)).roundToInt()
            }
        }

        for (index in processed.indices) {
            val current = processed[index]
            if (current >= 195 && index + 1 < processed.size) {
                processed[index + 1] = max(processed[index + 1], (current * 0.74).roundToInt())
            } else if (current >= 135 && index + 1 < processed.size) {
                processed[index + 1] = max(processed[index + 1], (current * 0.56).roundToInt())
            }
        }

        for (index in 1 until processed.lastIndex) {
            val previous = processed[index - 1]
            val current = processed[index]
            val next = processed[index + 1]
            if (current in 1..88 && previous < 88 && next < 88) {
                processed[index] = 0
            }
            if (current in 1..105 && previous == 0 && next == 0) {
                processed[index] = 0
            }
        }

        for (index in processed.indices) {
            processed[index] = processed[index].coerceIn(0, 255)
        }

        return processed
    }

    private fun pushHistory(history: ArrayDeque<Double>, value: Double, maxSize: Int = 64) {
        history.addLast(value)
        while (history.size > maxSize) {
            history.removeFirst()
        }
    }

    private fun referenceMean(history: ArrayDeque<Double>, fallback: Double): Double {
        if (history.isEmpty()) return fallback.coerceAtLeast(1e-6)
        var total = 0.0
        history.forEach { total += it }
        return (total / history.size).coerceAtLeast(1e-6)
    }

    private fun normalizedSpike(
        value: Double,
        history: ArrayDeque<Double>,
        scale: Double
    ): Double {
        if (history.size <= 2) return 0.6
        val mean = referenceMean(history, value)
        val std = standardDeviation(history, mean).coerceAtLeast(1e-6)
        return ((value - mean) / (std * scale)).coerceIn(0.0, 2.2)
    }

    private fun standardDeviation(history: ArrayDeque<Double>, mean: Double): Double {
        if (history.size <= 1) return 0.0
        var variance = 0.0
        history.forEach { variance += (it - mean) * (it - mean) }
        return sqrt(variance / history.size)
    }
}
