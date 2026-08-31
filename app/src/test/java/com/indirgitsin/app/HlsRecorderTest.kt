package com.indirgitsin.app

import com.indirgitsin.app.data.downloader.HlsRecorder
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HlsRecorderTest {
    @Test fun commitsCompleteSegmentsAndRecoversWithoutDuplicatingPartialTail() = runBlocking {
        MockWebServer().use { server ->
            val directory = Files.createTempDirectory("hls-test").toFile()
            try {
                server.enqueue(MockResponse().setBody("#EXTM3U\n#EXTINF:2,\na.ts\n#EXTINF:2,\nb.ts\n#EXT-X-ENDLIST"))
                server.enqueue(MockResponse().setBody("AAA")); server.enqueue(MockResponse().setBody("BBB"))
                val result = HlsRecorder.record(server.url("/live.m3u8").toString(), directory, 1, allowHttp = true)
                assertEquals("AAABBB", result.source.readText())
                assertEquals(4L, result.seconds)
                result.source.appendText("uncommitted-tail")
                val recovered = HlsRecorder.recover(directory)!!
                assertEquals("AAABBB", recovered.source.readText())
                assertEquals(3, server.requestCount)
            } finally { directory.deleteRecursively() }
        }
    }

    @Test fun liveStartsAtLatestCompleteSegmentAndStopsWithoutAnotherRequest() = runBlocking {
        MockWebServer().use { server ->
            val directory = Files.createTempDirectory("hls-live-test").toFile()
            try {
                server.enqueue(MockResponse().setBody("#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:90\n#EXTINF:2,\nold.ts\n#EXTINF:2,\nnow.ts"))
                server.enqueue(MockResponse().setBody("NOW"))
                val result = HlsRecorder.record(server.url("/live.m3u8").toString(), directory, 1, allowHttp = true) { _, _ ->
                    File(directory, "stop-recording").writeText("1")
                }
                assertEquals("NOW", result.source.readText())
                assertEquals(2, server.requestCount)
                server.takeRequest()
                assertEquals("/now.ts", server.takeRequest().path)
            } finally { directory.deleteRecursively() }
        }
    }

    @Test fun interruptedCheckpointFallsBackToPreviousCommittedPrefix() = runBlocking {
        val directory = Files.createTempDirectory("hls-recover-test").toFile()
        try {
            File(directory, "live.media").writeText("AAAunchecked")
            File(directory, "live.properties.bak").writeText("bytes=3\nseconds=2\n")
            File(directory, "live.properties").writeText("bytes=12\nseconds=4\n")
            assertEquals("AAA", HlsRecorder.recover(directory)!!.source.readText())
        } finally { directory.deleteRecursively() }
    }

    @Test fun rejectsIncorrectHttpRangeInsteadOfAppendingWrongBytes() = runBlocking {
        MockWebServer().use { server ->
            val directory = Files.createTempDirectory("hls-range-test").toFile()
            try {
                server.enqueue(MockResponse().setBody("#EXTM3U\n#EXTINF:2,\n#EXT-X-BYTERANGE:3@4\nall.ts\n#EXT-X-ENDLIST"))
                server.enqueue(MockResponse().setResponseCode(200).setBody("whole file"))
                assertTrue(runCatching { HlsRecorder.record(server.url("/live.m3u8").toString(), directory, 1, allowHttp = true) }.isFailure)
                assertNull(HlsRecorder.recover(directory))
            } finally { directory.deleteRecursively() }
        }
    }
}
