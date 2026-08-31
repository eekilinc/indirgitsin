package com.indirgitsin.app

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.indirgitsin.app.data.downloader.AudioMp3Converter
import com.indirgitsin.app.data.downloader.MediaFileMuxer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class MediaFileMuxerInstrumentedTest {
    @get:org.junit.Rule val timeout = org.junit.rules.Timeout.seconds(60)

    @Test fun separateVideoAndAudioKeepBothTracksAndTimestamps() = runBlocking {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "mux-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val video = File(directory, "video.mp4")
            val audio = File(directory, "audio.m4a")
            val output = File(directory, "combined.mp4")
            encode(video, video = true)
            encode(audio, video = false)
            MediaFileMuxer.mux(video, audio, output, "mp4")
            MediaFileMuxer.validate(output, videoRequired = true)
            assertSameTimeline(timestamps(video, "video/"), timestamps(output, "video/"))
            assertSameTimeline(timestamps(audio, "audio/"), timestamps(output, "audio/"))
            assertDecodable(output, "video/")
            assertDecodable(output, "audio/")
        } finally { directory.deleteRecursively() }
    }

    @Test fun videoOnlyFileIsRejectedInsteadOfReportedAsSuccessful() {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "mux-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val video = File(directory, "silent.mp4")
            encode(video, video = true)
            assertTrue(runCatching { MediaFileMuxer.validate(video, videoRequired = true) }.isFailure)
        } finally { directory.deleteRecursively() }
    }

    @Test fun mp3ConversionProducesDecodableMonoAndStereoAtAllBitrates() = runBlocking {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "mp3-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            for (channels in 1..2) {
                val source = File(directory, "source-$channels.m4a")
                encode(source, video = false, channels = channels)
                for (bitrate in listOf(128, 192, 320)) {
                    val output = File(directory, "result-$channels-$bitrate.mp3")
                    AudioMp3Converter.convert(source, output, bitrate)
                    AudioMp3Converter.validate(output)
                    assertDecodable(output, "audio/")
                    val timeline = timestamps(output, "audio/")
                    assertTrue("Truncated MP3", timeline.last() - timeline.first() >= 2_000_000)
                    val extractor = MediaExtractor()
                    try {
                        extractor.setDataSource(output.absolutePath)
                        val format = extractor.getTrackFormat(0)
                        assertEquals("audio/mpeg", format.getString(MediaFormat.KEY_MIME))
                        assertEquals(channels, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                        assertEquals(bitrate * 1000, format.getInteger(MediaFormat.KEY_BIT_RATE))
                    } finally { extractor.release() }
                }
            }
        } finally { directory.deleteRecursively() }
    }

    @Test fun failedMp3ConversionRemovesUnusableOutput() = runBlocking {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "mp3-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val source = File(directory, "silent.mp4")
            val output = File(directory, "invalid.mp3").apply { writeText("old partial data") }
            encode(source, video = true)
            assertTrue(runCatching { AudioMp3Converter.convert(source, output) }.isFailure)
            assertFalse(output.exists())
        } finally { directory.deleteRecursively() }
    }

    @Test fun capturedTransportStreamKeepsDecodableAudioAndVideo() = verifyCapture("capture.ts")
    @Test fun capturedFragmentedMp4KeepsDecodableAudioAndVideo() = verifyCapture("capture-fmp4.mp4")

    private fun verifyCapture(asset: String) = runBlocking {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "live-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val source = File(directory, asset)
            InstrumentationRegistry.getInstrumentation().context.assets.open("live/$asset").use { input ->
                source.outputStream().use { input.copyTo(it) }
            }
            val output = File(directory, "recording.mp4")
            MediaFileMuxer.remuxCapture(source, output)
            MediaFileMuxer.validate(output, videoRequired = true)
            for (prefix in listOf("audio/", "video/")) {
                assertDecodable(output, prefix)
                val before = timestamps(source, prefix)
                val after = timestamps(output, prefix)
                assertEquals("Capture lost $prefix samples", before.size, after.size)
                assertTrue("Capture was truncated", after.last() - after.first() > 7_500_000)
            }
            val audioStart = timestamps(output, "audio/").first()
            val videoStart = timestamps(output, "video/").first()
            assertTrue("A/V synchronization lost", kotlin.math.abs(audioStart - videoStart) < 100_000)
        } finally { directory.deleteRecursively() }
    }

    private fun encode(file: File, video: Boolean, channels: Int = 1) {
        val mime = if (video) "video/avc" else "audio/mp4a-latm"
        val codec = MediaCodec.createEncoderByType(mime)
        val writer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val format = if (video) MediaFormat.createVideoFormat(mime, 64, 64).apply {
                val colors = codec.codecInfo.getCapabilitiesForType(mime).colorFormats
                val color = listOf(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible).first { it in colors }
                setInteger(MediaFormat.KEY_COLOR_FORMAT, color)
                setInteger(MediaFormat.KEY_FRAME_RATE, 24)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            } else MediaFormat.createAudioFormat(mime, 44_100, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val maxFrames = if (video) 48 else 96
            var inputFrame = 0
            var inputEnded = false
            var track = -1
            var firstOutputTime: Long? = null
            var done = false
            val info = MediaCodec.BufferInfo()
            val deadline = System.nanoTime() + 20_000_000_000
            while (!done && System.nanoTime() < deadline) {
                if (!inputEnded) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = requireNotNull(codec.getInputBuffer(index))
                        buffer.clear()
                        val pts = if (video) inputFrame * 1_000_000L / 24 else inputFrame * 1024_000_000L / 44_100
                        if (inputFrame == maxFrames) {
                            codec.queueInputBuffer(index, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            if (video) {
                                buffer.put(ByteArray(64 * 64) { (32 + inputFrame * 2).toByte() })
                                buffer.put(ByteArray(64 * 64 / 2) { 128.toByte() })
                            } else {
                                buffer.order(ByteOrder.LITTLE_ENDIAN)
                                repeat(1024) { sample ->
                                    repeat(channels) { channel ->
                                        buffer.putShort((sin(2 * Math.PI * (440 + channel * 220) * (inputFrame * 1024 + sample) / 44_100) * 12000).toInt().toShort())
                                    }
                                }
                            }
                            codec.queueInputBuffer(index, 0, buffer.position(), pts, 0)
                            inputFrame++
                        }
                    }
                }
                when (val index = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = writer.addTrack(codec.outputFormat); writer.start() }
                    in 0..Int.MAX_VALUE -> {
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            if (firstOutputTime == null) firstOutputTime = info.presentationTimeUs
                            info.presentationTimeUs -= firstOutputTime!!
                            val buffer = requireNotNull(codec.getOutputBuffer(index))
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            writer.writeSampleData(track, buffer, info)
                        }
                        done = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(index, false)
                    }
                }
            }
            assertTrue("Encoder timed out", done)
            writer.stop()
            codec.stop()
        } finally { writer.release(); codec.release() }
    }

    private fun assertSameTimeline(expected: List<Long>, actual: List<Long>) {
        assertEquals("Sample count changed", expected.size, actual.size)
        expected.zip(actual).forEach { (before, after) ->
            // MP4 timescale conversion can round a few microseconds during remuxing.
            assertTrue("Timestamp changed: $before -> $after", kotlin.math.abs(before - after) <= 100)
        }
    }

    private fun timestamps(file: File, prefix: String): List<Long> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith(prefix) }
            extractor.selectTrack(track)
            val result = mutableListOf<Long>()
            while (extractor.sampleTrackIndex >= 0) { result += extractor.sampleTime; extractor.advance() }
            assertTrue(result.isNotEmpty())
            return result
        } finally { extractor.release() }
    }

    private fun assertDecodable(file: File, prefix: String) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith(prefix) }
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            decoder = codec
            codec.configure(format, null, null, 0)
            codec.start()
            var inputEnded = false
            var outputCount = 0
            var done = false
            val info = MediaCodec.BufferInfo()
            val deadline = System.nanoTime() + 20_000_000_000
            while (!done && System.nanoTime() < deadline) {
                if (!inputEnded) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = requireNotNull(codec.getInputBuffer(index))
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = codec.dequeueOutputBuffer(info, 10_000)
                if (index >= 0) {
                    if (info.size > 0) outputCount++
                    done = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(index, false)
                }
            }
            assertTrue("No decoded $prefix samples", outputCount > 0)
            assertTrue("Decoder timed out", done)
            codec.stop()
        } finally { decoder?.release(); extractor.release() }
    }
}
