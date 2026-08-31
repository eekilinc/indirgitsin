package com.indirgitsin.app

import com.indirgitsin.app.data.downloader.MediaTransfer
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

class MediaTransferTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var file: File
    private val chunk = MediaTransfer.CHUNK_SIZE.toInt()
    private val bytes = ByteArray(chunk + 8192) { (it * 31).toByte() }
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private var interrupt = true
    private var etag: String? = "\"original\""
    private var ignoreRange = false
    private var replacement: ByteArray? = null

    @Before fun setup() {
        file = temporary.newFile("media.part")
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val start = request.getHeader("Range")!!.substringAfter("bytes=").substringBefore('-').toInt()
                val data = replacement ?: bytes
                if (interrupt && start >= chunk) return MockResponse().setResponseCode(503)
                if (ignoreRange) return MockResponse().setBody(Buffer().write(data))
                val end = minOf(start + chunk, data.size)
                return MockResponse().setResponseCode(206)
                    .setHeader("Content-Range", "bytes $start-${end - 1}/${data.size}")
                    .setBody(Buffer().write(data, start, end - start)).apply { etag?.let { setHeader("ETag", it) } }
            }
        }
        server.start()
    }

    @After fun close() { server.shutdown() }
    private fun download(path: String = "/media") = runBlocking { MediaTransfer.download(server.url(path).toString(), file) { _, _ -> } }
    private fun interrupted() {
        try { download(); fail("Expected interrupted transfer") } catch (_: IOException) { }
        assertEquals(chunk.toLong(), file.length())
        requests.clear()
        interrupt = false
    }

    @Test(timeout = 20000) fun resumesCheckedRangesAfterNetworkFailure() {
        interrupted()
        download()
        assertEquals("bytes=$chunk-${bytes.size - 1}", requests.first().getHeader("Range"))
        assertEquals("\"original\"", requests.first().getHeader("If-Range"))
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test(timeout = 20000) fun fullResponseReplacesPartialInsteadOfAppending() {
        interrupted()
        ignoreRange = true
        replacement = ByteArray(16384) { 7 }
        download()
        assertArrayEquals(replacement, file.readBytes())
    }

    @Test(timeout = 20000) fun changedValidatorRestartsAtZero() {
        interrupted()
        etag = "\"replacement\""
        replacement = ByteArray(bytes.size) { 12 }
        download()
        assertEquals("bytes=0-${chunk - 1}", requests[1].getHeader("Range"))
        assertArrayEquals(replacement, file.readBytes())
    }

    @Test(timeout = 20000) fun resourceWithoutValidatorRestartsSafely() {
        etag = null
        interrupted()
        download()
        assertEquals("bytes=0-${chunk - 1}", requests.first().getHeader("Range"))
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test(timeout = 20000) fun completedValidatedFileIsReusedForMuxRetry() {
        interrupt = false
        download()
        requests.clear()
        download()
        assertTrue(requests.isEmpty())
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test(timeout = 20000) fun incompleteUncommittedTailIsDiscarded() {
        interrupted()
        file.appendBytes(ByteArray(4096) { 66 })
        download()
        assertEquals("bytes=$chunk-${bytes.size - 1}", requests.first().getHeader("Range"))
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test(timeout = 20000) fun corruptCheckpointRestarts() {
        interrupted()
        File(file.path + ".resume").writeText("offset=not-a-number")
        download()
        assertEquals("bytes=0-${chunk - 1}", requests.first().getHeader("Range"))
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test(timeout = 20000) fun matchingEtagOnUnrelatedUrlCannotJoinFiles() {
        interrupted()
        download("/different")
        assertEquals("bytes=0-${chunk - 1}", requests.first().getHeader("Range"))
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test(timeout = 20000) fun shortFullResponseCannotOverrideDeclaredMediaLength() {
        interrupt = false
        ignoreRange = true
        replacement = ByteArray(1024) { 3 }
        try { download("/media?clen=${bytes.size}"); fail("A truncated 200 must not be reported complete") }
        catch (_: IOException) { }
        assertEquals(0L, file.length())
    }

    @Test(timeout = 20000) fun rangeTotalMustMatchDeclaredMediaLength() {
        interrupt = false
        try { download("/media?clen=${bytes.size + 1}"); fail("Inconsistent source metadata must be rejected") }
        catch (_: IOException) { }
        assertEquals(0L, file.length())
    }
}
