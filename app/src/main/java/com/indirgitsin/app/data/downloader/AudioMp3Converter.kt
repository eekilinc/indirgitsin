package com.indirgitsin.app.data.downloader

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder

/** Android decodes to bounded PCM chunks; LAME produces actual MPEG Layer III frames. */
object AudioMp3Converter {
    // Bound CPU use and serialize LAME's initial shared psychoacoustic tables.
    private val encoderSlot = Semaphore(1)

    suspend fun convert(source: File, output: File, bitrate: Int = 192, id3Tag: ByteArray? = null, progress: (Int) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            encoderSlot.withPermit {
                require(bitrate in setOf(128, 192, 320))
                require(id3Tag == null || id3Tag.size <= Mp3Tags.MAX_COVER_BYTES + 8192)
                val extractor = MediaExtractor()
                var decoder: MediaCodec? = null
                var encoder: LameEncoder? = null
                var succeeded = false
                try {
                    extractor.setDataSource(source.absolutePath)
                    val track = (0 until extractor.trackCount).firstOrNull {
                        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                    } ?: error("MP3 dönüştürmek için ses izi bulunamadı.")
                    val format = extractor.getTrackFormat(track)
                    require(format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) in 1..2) { "MP3 için mono veya stereo ses seçin." }
                    val duration = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
                    format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                    extractor.selectTrack(track)
                    val codec = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
                    decoder = codec
                    codec.configure(format, null, null, 0)
                    codec.start()
                    val info = MediaCodec.BufferInfo()
                    val pcm = ShortArray(32768)
                    var inputEnded = false
                    var outputEnded = false
                    var channels = 0
                    var rate = 0
                    var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
                    var sampleCount = 0L
                    var lastActivity = SystemClock.elapsedRealtime()
                    output.outputStream().buffered(64 * 1024).use { sink ->
                        id3Tag?.let { sink.write(it) }
                        while (!outputEnded) {
                            currentCoroutineContext().ensureActive()
                            check(SystemClock.elapsedRealtime() - lastActivity < 30_000) { "Ses çözücü yanıt vermedi." }
                            if (!inputEnded) {
                                val index = codec.dequeueInputBuffer(1_000)
                                if (index >= 0) {
                                    val input = requireNotNull(codec.getInputBuffer(index))
                                    input.clear()
                                    val count = extractor.readSampleData(input, 0)
                                    if (count < 0) {
                                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                        inputEnded = true
                                    } else {
                                        check(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) { "Şifreli ses dönüştürülemiyor." }
                                        codec.queueInputBuffer(index, 0, count, extractor.sampleTime, 0)
                                        extractor.advance()
                                    }
                                    lastActivity = SystemClock.elapsedRealtime()
                                }
                            }
                            when (val index = codec.dequeueOutputBuffer(info, 1_000)) {
                                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                    val decoded = codec.outputFormat
                                    val newRate = decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                    val newChannels = decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                    require(newChannels in 1..2) { "MP3 için mono veya stereo PCM gerekli." }
                                    check(encoder == null || (rate == newRate && channels == newChannels)) { "Ses biçimi kayıt sırasında değişti." }
                                    rate = newRate; channels = newChannels
                                    pcmEncoding = if (decoded.containsKey(MediaFormat.KEY_PCM_ENCODING)) decoded.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                                    require(pcmEncoding in setOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_FLOAT)) { "Desteklenmeyen PCM biçimi." }
                                    if (encoder == null) encoder = LameEncoder(rate, channels, bitrate)
                                    lastActivity = SystemClock.elapsedRealtime()
                                }
                                else -> if (index >= 0) {
                                    try {
                                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                            val buffer = requireNotNull(codec.getOutputBuffer(index)).order(ByteOrder.LITTLE_ENDIAN)
                                            buffer.position(info.offset); buffer.limit(info.offset + info.size)
                                            val bytesPerSample = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                                            check(channels > 0 && info.size % (bytesPerSample * channels) == 0) { "Eksik PCM ses karesi." }
                                            while (buffer.hasRemaining()) {
                                                currentCoroutineContext().ensureActive()
                                                val count = minOf(pcm.size, buffer.remaining() / bytesPerSample)
                                                for (i in 0 until count) pcm[i] = if (bytesPerSample == 2) buffer.short else {
                                                    val value = buffer.float
                                                    if (value.isFinite()) (value.coerceIn(-1f, 1f) * 32767).toInt().toShort() else 0.toShort()
                                                }
                                                sink.write(requireNotNull(encoder).encode(pcm, count))
                                                sampleCount += count / channels
                                            }
                                            if (duration > 0) progress((info.presentationTimeUs.coerceAtLeast(0) * 100 / duration).toInt().coerceIn(0, 99))
                                        }
                                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                        lastActivity = SystemClock.elapsedRealtime()
                                    } finally { codec.releaseOutputBuffer(index, false) }
                                }
                            }
                        }
                        check(sampleCount > 0) { "Ses akışı boş; MP3 oluşturulmadı." }
                        sink.write(requireNotNull(encoder).finish())
                    }
                    currentCoroutineContext().ensureActive()
                    validate(output)
                    succeeded = true
                    progress(100)
                } finally {
                    try { encoder?.close() } finally {
                        try { decoder?.release() } finally { extractor.release(); if (!succeeded) output.delete() }
                    }
                }
            }
        }

    fun validate(file: File) {
        MediaFileMuxer.validate(file, false)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            check((0 until extractor.trackCount).any { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == "audio/mpeg" }) {
                "Çıktı gerçek MP3 değil."
            }
        } finally { extractor.release() }
    }
}
