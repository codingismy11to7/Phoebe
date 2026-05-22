package com.phoebe.app.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.phoebe.app.domain.EqualizerProfile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

internal object AndroidPlaybackDiagnostics {
    var diagnostics: PlaybackDiagnostics = PlaybackDiagnostics.None

    fun newPlayerBuilder(
        context: Context,
        engine: PlaybackEnginePath,
    ): ExoPlayer.Builder {
        diagnostics.engineSelected(engine)
        return ExoPlayer.Builder(
            context,
            PhoebeRenderersFactory(
                context = context,
                diagnostics = diagnostics,
                engine = engine,
            ),
        )
    }

    fun reset() {
        diagnostics = PlaybackDiagnostics.None
    }
}

internal object AndroidEqualizerState {
    @Volatile
    var profile: EqualizerProfile = EqualizerProfile.Default.normalized()
}

private class PhoebeRenderersFactory(
    context: Context,
    private val diagnostics: PlaybackDiagnostics,
    private val engine: PlaybackEnginePath,
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink? =
        DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(
                buildList<AudioProcessor> {
                    add(AndroidEqualizerAudioProcessor(AndroidEqualizerState))
                    if (diagnostics !== PlaybackDiagnostics.None) {
                        add(DiagnosticAudioProcessor(diagnostics, engine))
                    }
                }.toTypedArray(),
            )
            .build()
}

private class AndroidEqualizerAudioProcessor(
    private val equalizerState: AndroidEqualizerState,
) : BaseAudioProcessor() {
    private var processor: GraphicEqualizerProcessor? = null
    private var processorProfile: EqualizerProfile? = null
    private var processorSampleRate = 0
    private var processorChannelCount = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (Util.isEncodingLinearPcm(inputAudioFormat.encoding)) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return
        val profile = equalizerState.profile.normalized()
        val sampleBytes = media3SampleBytes(inputAudioFormat.encoding)
        if (!GraphicEqualizerProcessor.isActive(profile) ||
            sampleBytes == null ||
            inputAudioFormat.channelCount <= 0 ||
            inputAudioFormat.sampleRate <= 0 ||
            inputAudioFormat.encoding !in supportedEqualizerEncodings
        ) {
            val output = replaceOutputBuffer(remaining)
            output.put(inputBuffer)
            output.flip()
            return
        }

        val eq = equalizerProcessor(profile)
        val input = inputBuffer.order(byteOrderForEncoding(inputAudioFormat.encoding))
        val output = replaceOutputBuffer(remaining).order(byteOrderForEncoding(inputAudioFormat.encoding))
        val channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
        val frameSize = sampleBytes * channelCount
        while (input.remaining() >= frameSize) {
            repeat(channelCount) { channel ->
                val sample = when (inputAudioFormat.encoding) {
                    C.ENCODING_PCM_FLOAT -> input.float.coerceIn(-1f, 1f)
                    else -> input.short.toFloat() / 32768f
                }
                val processed = eq.process(channel, sample).coerceIn(-1f, 1f)
                when (inputAudioFormat.encoding) {
                    C.ENCODING_PCM_FLOAT -> output.putFloat(processed)
                    else -> output.putShort((processed * 32767f).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
                }
            }
        }
        while (input.hasRemaining()) {
            output.put(input.get())
        }
        output.flip()
    }

    override fun onFlush() {
        resetProcessor()
    }

    override fun onReset() {
        resetProcessor()
    }

    private fun equalizerProcessor(profile: EqualizerProfile): GraphicEqualizerProcessor {
        val needsNew = processor == null ||
            processorProfile != profile ||
            processorSampleRate != inputAudioFormat.sampleRate ||
            processorChannelCount != inputAudioFormat.channelCount
        if (needsNew) {
            processor = GraphicEqualizerProcessor(
                sampleRateHz = inputAudioFormat.sampleRate.toFloat(),
                channelCount = inputAudioFormat.channelCount.coerceAtLeast(1),
                profile = profile,
            )
            processorProfile = profile
            processorSampleRate = inputAudioFormat.sampleRate
            processorChannelCount = inputAudioFormat.channelCount
        }
        return processor ?: GraphicEqualizerProcessor(
            sampleRateHz = inputAudioFormat.sampleRate.toFloat(),
            channelCount = inputAudioFormat.channelCount.coerceAtLeast(1),
            profile = profile,
        )
    }

    private fun resetProcessor() {
        processor = null
        processorProfile = null
        processorSampleRate = 0
        processorChannelCount = 0
    }
}

private class DiagnosticAudioProcessor(
    private val diagnostics: PlaybackDiagnostics,
    private val engine: PlaybackEnginePath,
) : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (Util.isEncodingLinearPcm(inputAudioFormat.encoding)) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return

        val probeBuffer = inputBuffer.asReadOnlyBuffer().order(ByteOrder.nativeOrder())
        val rms = media3PcmRms(
            buffer = probeBuffer,
            encoding = inputAudioFormat.encoding,
            channelCount = inputAudioFormat.channelCount,
        )
        if (rms > 0.0 && rms.isFinite()) {
            diagnostics.decodedAudioEnergy(engine, rms)
        }

        val output = replaceOutputBuffer(remaining)
        output.put(inputBuffer)
        output.flip()
    }
}

private fun media3PcmRms(
    buffer: ByteBuffer,
    encoding: Int,
    channelCount: Int,
): Double {
    val sampleBytes = media3SampleBytes(encoding) ?: return 0.0
    val frameSize = sampleBytes * channelCount.coerceAtLeast(1)
    if (frameSize <= 0) return 0.0
    val start = buffer.position()
    val end = buffer.limit()
    var frameOffset = start
    var sumSquares = 0.0
    var sampleCount = 0L
    while (frameOffset + frameSize <= end) {
        var sampleOffset = frameOffset
        repeat(channelCount.coerceAtLeast(1)) {
            val normalized = media3NormalizedSample(buffer, sampleOffset, encoding)
            sumSquares += normalized * normalized
            sampleCount++
            sampleOffset += sampleBytes
        }
        frameOffset += frameSize
    }
    return if (sampleCount == 0L) 0.0 else kotlin.math.sqrt(sumSquares / sampleCount.toDouble())
}

private fun media3SampleBytes(encoding: Int): Int? =
    when (encoding) {
        C.ENCODING_PCM_8BIT -> 1
        C.ENCODING_PCM_16BIT,
        C.ENCODING_PCM_16BIT_BIG_ENDIAN,
        -> 2
        C.ENCODING_PCM_24BIT,
        C.ENCODING_PCM_24BIT_BIG_ENDIAN,
        -> 3
        C.ENCODING_PCM_32BIT,
        C.ENCODING_PCM_32BIT_BIG_ENDIAN,
        C.ENCODING_PCM_FLOAT,
        -> 4
        else -> null
    }

private val supportedEqualizerEncodings = setOf(
    C.ENCODING_PCM_16BIT,
    C.ENCODING_PCM_16BIT_BIG_ENDIAN,
    C.ENCODING_PCM_FLOAT,
)

private fun byteOrderForEncoding(encoding: Int): ByteOrder =
    when (encoding) {
        C.ENCODING_PCM_16BIT_BIG_ENDIAN -> ByteOrder.BIG_ENDIAN
        else -> ByteOrder.LITTLE_ENDIAN
    }

private fun media3NormalizedSample(
    buffer: ByteBuffer,
    offset: Int,
    encoding: Int,
): Double {
    return when (encoding) {
        C.ENCODING_PCM_8BIT -> ((buffer.get(offset).toInt() and 0xFF) - 128).toDouble() / 128.0
        C.ENCODING_PCM_16BIT -> signedPcm(buffer, offset, 2, littleEndian = true).toDouble() / 32768.0
        C.ENCODING_PCM_16BIT_BIG_ENDIAN -> signedPcm(buffer, offset, 2, littleEndian = false).toDouble() / 32768.0
        C.ENCODING_PCM_24BIT -> signedPcm(buffer, offset, 3, littleEndian = true).toDouble() / 8388608.0
        C.ENCODING_PCM_24BIT_BIG_ENDIAN -> signedPcm(buffer, offset, 3, littleEndian = false).toDouble() / 8388608.0
        C.ENCODING_PCM_32BIT -> signedPcm(buffer, offset, 4, littleEndian = true).toDouble() / 2147483648.0
        C.ENCODING_PCM_32BIT_BIG_ENDIAN -> signedPcm(buffer, offset, 4, littleEndian = false).toDouble() / 2147483648.0
        C.ENCODING_PCM_FLOAT -> buffer.order(ByteOrder.nativeOrder()).getFloat(offset).toDouble().coerceIn(-1.0, 1.0)
        else -> 0.0
    }
}

private fun signedPcm(
    buffer: ByteBuffer,
    offset: Int,
    sampleBytes: Int,
    littleEndian: Boolean,
): Long {
    var value = 0L
    repeat(sampleBytes) { index ->
        val sourceIndex = if (littleEndian) offset + index else offset + sampleBytes - 1 - index
        value = value or ((buffer.get(sourceIndex).toLong() and 0xFFL) shl (8 * index))
    }
    val shift = 64 - sampleBytes * 8
    return (value shl shift) shr shift
}
