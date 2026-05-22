package com.phoebe.app.player

import com.phoebe.app.domain.EqualizerProfile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal class GraphicEqualizerProcessor(
    sampleRateHz: Float,
    channelCount: Int,
    profile: EqualizerProfile,
) {
    private val normalized = profile.normalized()
    private val enabled = normalized.enabled && !normalized.isFlat && sampleRateHz > 0f && channelCount > 0
    private val inputGain = if (enabled) {
        val maxBoost = normalized.gainsDb.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        10.0.pow((-maxBoost.coerceAtMost(12f)).toDouble() / 20.0).toFloat()
    } else {
        1f
    }
    private val filtersByChannel: Array<Array<BiquadFilter>> = if (enabled) {
        val filters = normalized.bands
            .zip(normalized.gainsDb)
            .filter { (band, gainDb) ->
                gainDb != 0f && band.frequencyHz > 0f && band.frequencyHz < sampleRateHz * 0.48f
            }
            .map { (band, gainDb) ->
                BiquadFilter.peaking(
                    sampleRateHz = sampleRateHz,
                    centerFrequencyHz = band.frequencyHz,
                    gainDb = gainDb,
                    q = qForBandCount(normalized.bandCount),
                )
            }
        Array(channelCount) { filters.map { it.copy() }.toTypedArray() }
    } else {
        emptyArray()
    }

    fun process(channel: Int, sample: Float): Float {
        if (!enabled || filtersByChannel.isEmpty()) return sample
        val filters = filtersByChannel[channel.coerceIn(filtersByChannel.indices)]
        var output = sample * inputGain
        filters.forEach { filter ->
            output = filter.process(output)
        }
        return output.coerceIn(-1f, 1f)
    }

    companion object {
        fun isActive(profile: EqualizerProfile): Boolean =
            profile.normalized().let { it.enabled && !it.isFlat }
    }
}

private fun qForBandCount(bandCount: Int): Double =
    when (bandCount) {
        31 -> 4.2
        15 -> 2.1
        5 -> 0.9
        else -> 1.35
    }

private class BiquadFilter(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun process(input: Float): Float {
        val x0 = input.toDouble()
        val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x0
        y2 = y1
        y1 = y0
        return y0.toFloat()
    }

    fun copy(): BiquadFilter = BiquadFilter(b0, b1, b2, a1, a2)

    companion object {
        fun peaking(
            sampleRateHz: Float,
            centerFrequencyHz: Float,
            gainDb: Float,
            q: Double,
        ): BiquadFilter {
            val a = sqrt(10.0.pow(gainDb.toDouble() / 20.0))
            val omega = 2.0 * PI * centerFrequencyHz.toDouble() / sampleRateHz.toDouble()
            val alpha = sin(omega) / (2.0 * q)
            val cosOmega = cos(omega)

            val rawB0 = 1.0 + alpha * a
            val rawB1 = -2.0 * cosOmega
            val rawB2 = 1.0 - alpha * a
            val rawA0 = 1.0 + alpha / a
            val rawA1 = -2.0 * cosOmega
            val rawA2 = 1.0 - alpha / a

            return BiquadFilter(
                b0 = rawB0 / rawA0,
                b1 = rawB1 / rawA0,
                b2 = rawB2 / rawA0,
                a1 = rawA1 / rawA0,
                a2 = rawA2 / rawA0,
            )
        }
    }
}
