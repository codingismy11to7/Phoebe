package com.phoebe.app.player

import com.phoebe.app.domain.AudioAnalysisFrame
import com.phoebe.app.domain.AudioAnalysisSource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class AudioAnalysisAccumulator(
    private val bandCount: Int = DefaultBandCount,
    private val minPublishIntervalMs: Long = DefaultPublishIntervalMs,
) {
    private var lastPublishedAtMs = Long.MIN_VALUE

    fun reset() {
        lastPublishedAtMs = Long.MIN_VALUE
    }

    fun observePcm(
        samples: FloatArray,
        sampleRateHz: Float,
        timestampMs: Long,
        source: AudioAnalysisSource = AudioAnalysisSource.Pcm,
    ): AudioAnalysisFrame? {
        if (!canPublish(timestampMs)) return null
        if (samples.isEmpty() || sampleRateHz <= 0f) {
            return publish(AudioAnalysisFrame(timestampMs = timestampMs, source = source))
        }
        val trimmed = downsample(samples, AnalysisSampleLimit)
        var sumSquares = 0.0
        trimmed.forEach { sample ->
            val coerced = sample.coerceIn(-1f, 1f)
            sumSquares += coerced * coerced
        }
        val amplitude = sqrt(sumSquares / trimmed.size.toDouble()).toFloat().coerceIn(0f, 1f)
        val bands = frequencyBands(trimmed, sampleRateHz)
        return publish(
            AudioAnalysisFrame(
                amplitude = amplitude,
                bands = bands,
                timestampMs = timestampMs,
                source = source,
            ),
        )
    }

    fun observeMagnitudesDb(
        magnitudesDb: FloatArray,
        timestampMs: Long,
        source: AudioAnalysisSource = AudioAnalysisSource.Spectrum,
    ): AudioAnalysisFrame? {
        if (!canPublish(timestampMs)) return null
        if (magnitudesDb.isEmpty()) {
            return publish(AudioAnalysisFrame(timestampMs = timestampMs, source = source))
        }
        val bands = FloatArray(bandCount.coerceAtLeast(1)) { band ->
            val start = (band * magnitudesDb.size) / bandsSize()
            val end = (((band + 1) * magnitudesDb.size) / bandsSize()).coerceAtLeast(start + 1)
            var peak = 0f
            for (index in start until end.coerceAtMost(magnitudesDb.size)) {
                peak = max(peak, magnitudeDbToUnit(magnitudesDb[index]))
            }
            peak
        }.toList()
        val amplitude = sqrt(bands.fold(0.0) { acc, band -> acc + band * band } / bands.size).toFloat()
        return publish(
            AudioAnalysisFrame(
                amplitude = amplitude.coerceIn(0f, 1f),
                bands = bands,
                timestampMs = timestampMs,
                source = source,
            ),
        )
    }

    private fun canPublish(timestampMs: Long): Boolean =
        lastPublishedAtMs == Long.MIN_VALUE ||
            timestampMs - lastPublishedAtMs >= minPublishIntervalMs ||
            timestampMs < lastPublishedAtMs

    private fun publish(frame: AudioAnalysisFrame): AudioAnalysisFrame {
        lastPublishedAtMs = frame.timestampMs
        return frame.normalized(bandCount)
    }

    private fun bandsSize(): Int = bandCount.coerceAtLeast(1)

    private fun frequencyBands(samples: FloatArray, sampleRateHz: Float): List<Float> {
        val count = bandCount.coerceAtLeast(1)
        return FloatArray(count) { index ->
            val frequency = logFrequency(index, count)
            goertzelMagnitude(samples, sampleRateHz, frequency)
        }
            .let { raw ->
                val peak = raw.maxOrNull()?.takeIf { it > 0f } ?: 1f
                raw.map { (it / peak).coerceIn(0f, 1f) }
            }
    }

    private fun logFrequency(index: Int, count: Int): Float {
        val min = MinFrequencyHz
        val max = MaxFrequencyHz
        val t = if (count <= 1) 0f else index.toFloat() / (count - 1).toFloat()
        return (min * (max / min).pow(t)).coerceIn(min, max)
    }

    private fun goertzelMagnitude(samples: FloatArray, sampleRateHz: Float, targetFrequencyHz: Float): Float {
        val normalizedFrequency = targetFrequencyHz.coerceAtMost(sampleRateHz * 0.48f)
        if (normalizedFrequency <= 0f) return 0f
        val omega = 2.0 * PI * normalizedFrequency.toDouble() / sampleRateHz.toDouble()
        val coeff = 2.0 * cos(omega)
        var s0: Double
        var s1 = 0.0
        var s2 = 0.0
        samples.forEach { sample ->
            s0 = sample.coerceIn(-1f, 1f).toDouble() + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        val power = s1 * s1 + s2 * s2 - coeff * s1 * s2
        return sqrt(power.coerceAtLeast(0.0)).toFloat() / samples.size.coerceAtLeast(1)
    }

    companion object {
        const val DefaultBandCount = 32
        const val DefaultPublishIntervalMs = 45L
        private const val AnalysisSampleLimit = 2048
        private const val MinFrequencyHz = 60f
        private const val MaxFrequencyHz = 12_000f

        fun fallbackFrame(
            seed: String,
            positionMs: Long,
            isPlaying: Boolean,
            timestampMs: Long,
            bandCount: Int = DefaultBandCount,
        ): AudioAnalysisFrame {
            val phase = positionMs.coerceAtLeast(0L).toFloat() / 680f
            val seedHash = seed.fold(0) { acc, c -> acc * 31 + c.code }
            val seedPhase = (((seedHash % 997) + 997) % 997) * 0.013f
            val pulse = if (isPlaying) 0.42f + 0.28f * sin(phase) else 0.12f
            val bands = List(bandCount.coerceAtLeast(1)) { index ->
                val local = sin(phase * (0.65f + index * 0.018f) + index * 0.71f + seedPhase)
                val ripple = cos(phase * 0.43f + index * 0.37f)
                (0.18f + pulse * 0.55f + local * 0.17f + ripple * 0.10f).coerceIn(0.04f, 1f)
            }
            return AudioAnalysisFrame(
                amplitude = pulse.coerceIn(0f, 1f),
                bands = bands,
                timestampMs = timestampMs.coerceAtLeast(0L),
                source = AudioAnalysisSource.None,
            )
        }
    }
}

private fun magnitudeDbToUnit(db: Float): Float {
    if (!db.isFinite()) return 0f
    return ((db.coerceIn(-80f, 0f) + 80f) / 80f).coerceIn(0f, 1f)
}

private fun downsample(samples: FloatArray, maxSamples: Int): FloatArray {
    if (samples.size <= maxSamples) return samples
    val step = samples.size.toFloat() / maxSamples.toFloat()
    return FloatArray(maxSamples) { index ->
        samples[(index * step).toInt().coerceIn(samples.indices)]
    }
}
