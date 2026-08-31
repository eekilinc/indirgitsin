package com.indirgitsin.app.data.downloader

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.ByteBuffer

/** Copies encoded samples and their original timestamps; never fabricates a silent video. */
object MediaFileMuxer {
    /** Remux a contiguous, unencrypted TS/fMP4 capture into a seekable MP4 without re-encoding. */
    suspend fun remuxCapture(source: File, output: File) {
        val reader = MediaExtractor()
        var writer: MediaMuxer? = null
        var succeeded = false
        try {
            reader.setDataSource(source.absolutePath)
            val video = findTrack(reader, "video/")
            val audio = findTrack(reader, "audio/")
            require(video >= 0 && audio >= 0) { "Canlı yayında hem görüntü hem ses bulunamadı." }
            val videoFormat = reader.getTrackFormat(video)
            val audioFormat = reader.getTrackFormat(audio)
            val adts = audioFormat.containsKey(MediaFormat.KEY_IS_ADTS) && audioFormat.getInteger(MediaFormat.KEY_IS_ADTS) == 1
            if (adts) audioFormat.setInteger(MediaFormat.KEY_IS_ADTS, 0)
            checkCompatibility(videoFormat.getString(MediaFormat.KEY_MIME).orEmpty(), audioFormat.getString(MediaFormat.KEY_MIME).orEmpty(), "mp4")
            val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            writer = muxer
            val tracks = mapOf(video to muxer.addTrack(videoFormat), audio to muxer.addTrack(audioFormat))
            reader.selectTrack(video); reader.selectTrack(audio)
            muxer.start()
            var buffer = ByteBuffer.allocateDirect(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            val counts = mutableMapOf(video to 0L, audio to 0L)
            // Android's TS/MP4 extractor exposes both tracks on one shared timeline.
            val firstTime = reader.sampleTime.coerceAtLeast(0)
            while (reader.sampleTrackIndex >= 0) {
                currentCoroutineContext().ensureActive()
                val required = if (Build.VERSION.SDK_INT >= 28) reader.sampleSize else 16L * 1024 * 1024
                require(required in 1..64L * 1024 * 1024) { "Yayın karesi desteklenen sınırı aşıyor." }
                if (required > buffer.capacity()) buffer = ByteBuffer.allocateDirect(required.toInt())
                buffer.clear()
                val size = reader.readSampleData(buffer, 0)
                check(size > 0 && reader.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) { "Yayın örneği eksik veya şifreli." }
                val track = reader.sampleTrackIndex
                val timestamp = (reader.sampleTime - firstTime).coerceAtLeast(0)
                buffer.position(0); buffer.limit(size)
                val frames = if (track == audio && adts) AdtsFrames.split(buffer, size) else listOf(AdtsFrame(0, size, 0))
                for (frame in frames) {
                    info.set(frame.offset, frame.size, timestamp + frame.timeOffsetUs,
                        if (reader.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    muxer.writeSampleData(requireNotNull(tracks[track]), buffer, info)
                    counts[track] = counts.getValue(track) + 1
                }
                reader.advance()
            }
            check(counts.values.all { it > 0 }) { "Canlı kayıt ses veya görüntü içermiyor." }
            muxer.stop()
            validate(output, true)
            succeeded = true
        } finally {
            try { writer?.release() } finally { reader.release(); if (!succeeded) output.delete() }
        }
    }

    suspend fun mux(video: File, audio: File, output: File, extension: String) {
        val v = MediaExtractor()
        val a = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            v.setDataSource(video.absolutePath)
            a.setDataSource(audio.absolutePath)
            val vi = findTrack(v, "video/")
            val ai = findTrack(a, "audio/")
            require(vi >= 0) { "İndirilen video dosyasında görüntü izi yok." }
            require(ai >= 0) { "İndirilen ses dosyasında ses izi yok." }
            val vf = v.getTrackFormat(vi)
            val af = a.getTrackFormat(ai)
            checkCompatibility(vf.getString(MediaFormat.KEY_MIME).orEmpty(), af.getString(MediaFormat.KEY_MIME).orEmpty(), extension)
            v.selectTrack(vi)
            a.selectTrack(ai)
            val format = if (extension == "webm") MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            val writer = MediaMuxer(output.absolutePath, format)
            muxer = writer
            // Passing the original formats preserves csd-0/csd-1, dimensions, AAC config and color information.
            val vt = writer.addTrack(vf)
            val at = writer.addTrack(af)
            if (vf.containsKey(MediaFormat.KEY_ROTATION)) writer.setOrientationHint(vf.getInteger(MediaFormat.KEY_ROTATION))
            writer.start()
            var buffer = ByteBuffer.allocateDirect(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            var videoSamples = 0L
            var audioSamples = 0L
            while (v.sampleTrackIndex >= 0 || a.sampleTrackIndex >= 0) {
                currentCoroutineContext().ensureActive()
                val useVideo = v.sampleTrackIndex >= 0 && (a.sampleTrackIndex < 0 || v.sampleTime <= a.sampleTime)
                val reader = if (useVideo) v else a
                val trackFormat = if (useVideo) vf else af
                val required = if (Build.VERSION.SDK_INT >= 28) reader.sampleSize else
                    if (trackFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).toLong() else 16L * 1024 * 1024
                require(required <= 64L * 1024 * 1024) { "Video karesi desteklenen bellek sınırını aşıyor." }
                if (required > buffer.capacity()) buffer = ByteBuffer.allocateDirect(required.toInt())
                buffer.clear()
                val size = reader.readSampleData(buffer, 0)
                check(size > 0) { "Medya verisi eksik veya okunamıyor." }
                check(reader.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) { "Şifreli medya örneği desteklenmiyor." }
                // B-frame timestamps need not increase in decode order. Keep the source timeline intact.
                info.set(0, size, reader.sampleTime,
                    if (reader.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                buffer.position(0)
                buffer.limit(size)
                writer.writeSampleData(if (useVideo) vt else at, buffer, info)
                if (useVideo) videoSamples++ else audioSamples++
                reader.advance()
            }
            check(videoSamples > 0 && audioSamples > 0) { "Video ve sesin ikisi de yazılamadı." }
            writer.stop()
        } finally {
            try { muxer?.release() } finally {
                try { v.release() } finally { a.release() }
            }
        }
        validate(output, videoRequired = true)
    }

    fun validate(file: File, videoRequired: Boolean, expectedDurationSeconds: Long = 0) {
        check(file.length() > 0) { "İndirilen dosya boş." }
        val reader = MediaExtractor()
        try {
            reader.setDataSource(file.absolutePath)
            val audio = findTrack(reader, "audio/")
            val video = findTrack(reader, "video/")
            check(audio >= 0) { "Dosyada ses izi yok; sessiz video kaydedilmedi." }
            check(!videoRequired || video >= 0) { "Dosyada görüntü izi yok." }
            val tracks = if (videoRequired) listOf(video, audio) else listOf(audio)
            for (index in tracks) {
                reader.selectTrack(index)
                reader.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                check(reader.sampleTrackIndex >= 0) { "Medya izi boş." }
                val format = reader.getTrackFormat(index)
                if (expectedDurationSeconds > 10 && format.containsKey(MediaFormat.KEY_DURATION)) {
                    val duration = format.getLong(MediaFormat.KEY_DURATION) / 1_000_000
                    check(duration >= expectedDurationSeconds - maxOf(5, expectedDurationSeconds / 50)) {
                        "İndirme eksik: beklenen $expectedDurationSeconds saniye, bulunan $duration saniye."
                    }
                }
                reader.unselectTrack(index)
            }
        } finally { reader.release() }
    }

    private fun findTrack(reader: MediaExtractor, prefix: String): Int =
        (0 until reader.trackCount).firstOrNull { reader.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true } ?: -1

    private fun checkCompatibility(videoMime: String, audioMime: String, extension: String) {
        val supported = when (extension) {
            "mp4" -> audioMime == "audio/mp4a-latm" && (videoMime in setOf("video/avc", "video/hevc", "video/mp4v-es") ||
                (videoMime == "video/av01" && Build.VERSION.SDK_INT >= 34))
            "webm" -> videoMime in setOf("video/x-vnd.on2.vp8", "video/x-vnd.on2.vp9") &&
                (audioMime == "audio/vorbis" || (audioMime == "audio/opus" && Build.VERSION.SDK_INT >= 29))
            else -> false
        }
        require(supported) { "Bu cihazda $videoMime + $audioMime, $extension olarak birleştirilemiyor. MP4/AAC seçeneğini deneyin." }
    }
}
