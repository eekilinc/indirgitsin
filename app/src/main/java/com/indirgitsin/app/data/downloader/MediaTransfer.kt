package com.indirgitsin.app.data.downloader

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class StreamHttpException(val status: Int) : IOException("Medya sunucusu HTTP $status döndürdü.")

object MediaTransfer {
    internal const val CHUNK_SIZE = 4L * 1024 * 1024
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(5, TimeUnit.MINUTES).build()

    /** Only fully checked HTTP ranges are committed. An interrupted range is downloaded again. */
    suspend fun download(
        url: String, destination: File, representation: String = "",
        httpClient: OkHttpClient = client, onProgress: (Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val parsedUrl = url.toHttpUrl()
        val declaredLength = parsedUrl.queryParameter("clen")?.toLongOrNull()?.takeIf { it > 0 } ?: -1L
        // Signed CDN URLs expire. itag + modification time + size identify the same rendition.
        // Else keep the complete URL identity; never join unrelated resources with the same ETag.
        val cdnRevision = if (parsedUrl.host.endsWith(".googlevideo.com") && representation.isNotBlank()) {
            listOf("itag", "lmt", "clen").map { parsedUrl.queryParameter(it) }
                .takeIf { parts -> parts.all { !it.isNullOrBlank() } && (parts[2]?.toLongOrNull() ?: 0) > 0 }
                ?.joinToString("|")
        } else null
        val identity = MessageDigest.getInstance("SHA-256")
            .digest("$representation|${cdnRevision ?: url}".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val metadata = File(destination.path + ".resume")
        val saved = Properties().apply {
            try { metadata.inputStream().use { load(it) } } catch (_: IOException) { clear() }
            catch (_: IllegalArgumentException) { clear() }
        }
        var validator = saved.getProperty("etag").orEmpty()
        var total = saved.getProperty("total")?.toLongOrNull() ?: -1L
        var downloaded = saved.getProperty("offset")?.toLongOrNull() ?: 0L
        val canResume = saved.getProperty("identity") == identity &&
            (validator.isNotBlank() || cdnRevision != null) && total > 0 && downloaded in 1..total &&
            destination.length() >= downloaded
        if (!canResume) { downloaded = 0; total = -1; validator = "" }

        fun checkpoint() {
            val properties = Properties().apply {
                setProperty("identity", identity); setProperty("etag", validator)
                setProperty("total", total.toString()); setProperty("offset", downloaded.toString())
            }
            val temporary = File(metadata.path + ".tmp")
            temporary.outputStream().use { properties.store(it, null) }
            // A missing or incomplete checkpoint causes a safe restart on the next attempt.
            if (metadata.exists() && !metadata.delete()) throw IOException("İndirme kaydı güncellenemedi.")
            if (!temporary.renameTo(metadata)) throw IOException("İndirme kaydı kaydedilemedi.")
        }

        RandomAccessFile(destination, "rw").use { output ->
            output.setLength(downloaded)
            output.seek(downloaded)
            onProgress(downloaded, total)
            var restarts = 0
            while (total < 0 || downloaded < total) {
                currentCoroutineContext().ensureActive()
                val requestedEnd = if (total > 0) minOf(total - 1, downloaded + CHUNK_SIZE - 1) else downloaded + CHUNK_SIZE - 1
                val request = Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://www.youtube.com/")
                    .header("Accept-Encoding", "identity")
                    .header("Range", "bytes=$downloaded-$requestedEnd")
                if (downloaded > 0 && validator.isNotBlank()) request.header("If-Range", validator)
                val call = httpClient.newCall(request.build())
                coroutineScope {
                    val closer = launch(start = CoroutineStart.UNDISPATCHED) {
                        try { awaitCancellation() } finally { call.cancel() }
                    }
                    try {
                        call.awaitResponse().use responseUse@{ response ->
                            if (!response.isSuccessful) throw StreamHttpException(response.code)
                            val body = response.body ?: throw IOException("Sunucu boş yanıt verdi.")
                            val etag = response.header("ETag").orEmpty().takeIf {
                                it.startsWith('"') && it.endsWith('"') && it.length >= 2
                            }.orEmpty()
                            val range = if (response.code == 206) response.header("Content-Range")?.let(ContentRange::parse)
                                ?: throw IOException("Sunucu geçersiz Content-Range döndürdü.") else null
                            if (declaredLength > 0 && ((range != null && range.total != declaredLength) ||
                                    (range == null && body.contentLength() >= 0 && body.contentLength() != declaredLength))) {
                                throw IOException("Medya boyutu kaynak bağlantısındaki değerle uyuşmuyor.")
                            }
                            if (range != null && (range.start != downloaded || range.end > requestedEnd)) {
                                throw IOException("Sunucu yanlış medya parçası döndürdü.")
                            }
                            val changed = downloaded > 0 && (response.code == 200 ||
                                (validator.isNotBlank() && etag != validator) || (range != null && total > 0 && range.total != total))
                            if (changed) {
                                if (++restarts > 1) throw IOException("Medya dosyası aktarım sırasında değişti.")
                                downloaded = 0; total = -1; validator = ""
                                output.setLength(0); output.seek(0); checkpoint(); onProgress(0, -1)
                                if (response.code != 200) return@responseUse
                            }
                            validator = etag
                            total = range?.total ?: maxOf(body.contentLength(), declaredLength)
                            val expected = range?.let { it.end - it.start + 1 } ?: total
                            var received = 0L
                            body.byteStream().use { input ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (expected >= 0 && received + count > expected) throw IOException("Medya parçası beklenenden uzun.")
                                    output.write(buffer, 0, count)
                                    received += count
                                    onProgress(downloaded + received, total)
                                }
                            }
                            if (received == 0L || (expected >= 0 && received != expected)) throw IOException("Medya dosyası eksik indirildi.")
                            downloaded += received
                            if (total < 0) total = downloaded
                            checkpoint()
                            destination.parentFile?.setLastModified(System.currentTimeMillis())
                        }
                    } finally { closer.cancel() }
                }
            }
        }
        if (total <= 0 || downloaded != total) throw IOException("Dosya boyutu beklenen değerle uyuşmuyor.")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { response.close() }
            }
        })
    }
}
