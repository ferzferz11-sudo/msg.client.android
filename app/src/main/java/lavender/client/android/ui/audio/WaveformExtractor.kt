package lavender.client.android.ui.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object WaveformExtractor {

    private const val BAR_COUNT = 40
    private const val SAMPLE_DURATION_US = 50_000L // 50ms per bar

    private val cache = ConcurrentHashMap<String, List<Float>>()

    suspend fun extract(audioUrl: String): List<Float> = withContext(Dispatchers.IO) {
        cache[audioUrl]?.let { return@withContext it }

        try {
            val tmpFile = java.io.File.createTempFile("waveform", ".tmp", context?.cacheDir)
            try {
                URL(audioUrl).openStream().use { input ->
                    tmpFile.outputStream().use { output -> input.copyTo(output) }
                }
                val result = extractFromFile(tmpFile.absolutePath)
                if (result.isNotEmpty()) cache[audioUrl] = result
                result
            } finally {
                tmpFile.delete()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getCachedOrFallback(audioUrl: String): List<Float> {
        cache[audioUrl]?.let { return it }
        return generateDefaultWaveform()
    }

    fun generateDefaultWaveform(): List<Float> {
        val bars = mutableListOf<Float>()
        for (i in 0 until BAR_COUNT) {
            bars.add(kotlin.random.Random.nextFloat() * 0.7f + 0.3f)
        }
        return bars
    }

    private fun extractFromFile(path: String): List<Float> {
        val extractor = MediaExtractor()
        extractor.setDataSource(path)

        var audioTrackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioTrackIndex = i
                format = f
                break
            }
        }
        if (audioTrackIndex == -1 || format == null) {
            extractor.release()
            return emptyList()
        }

        extractor.selectTrack(audioTrackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return emptyList()
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmData = mutableListOf<Short>()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outputIndex >= 0) {
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
                val outputBuffer = codec.getOutputBuffer(outputIndex) ?: continue
                val shortBuffer = outputBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val samples = ShortArray(shortBuffer.remaining())
                shortBuffer.get(samples)
                pcmData.addAll(samples.toList())
                codec.releaseOutputBuffer(outputIndex, false)
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        if (pcmData.isEmpty()) return emptyList()

        // Compute RMS per bar
        val samplesPerBar = pcmData.size / BAR_COUNT
        if (samplesPerBar <= 0) return generateDefaultWaveform()

        val bars = mutableListOf<Float>()
        var maxRms = 0.0
        val rmsValues = mutableListOf<Double>()

        for (bar in 0 until BAR_COUNT) {
            val start = bar * samplesPerBar
            val end = minOf(start + samplesPerBar, pcmData.size)
            var sum = 0.0
            for (i in start until end) {
                val sample = pcmData[i].toDouble()
                sum += sample * sample
            }
            val rms = kotlin.math.sqrt(sum / (end - start))
            rmsValues.add(rms)
            if (rms > maxRms) maxRms = rms
        }

        // Normalize to 0.1-1.0 range
        for (rms in rmsValues) {
            val normalized = if (maxRms > 0) (rms / maxRms).coerceIn(0.1, 1.0) else 0.5
            bars.add(normalized.toFloat())
        }

        return bars
    }

    private var context: android.content.Context? = null

    fun init(appContext: android.content.Context) {
        context = appContext.applicationContext
    }
}
