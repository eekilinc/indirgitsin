package com.indirgitsin.app.data.downloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class StreamHttpException(val status: Int) : IOException("Medya sunucusu HTTP $status döndürdü.")

object MediaTransfer {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(5, TimeUnit.MINUTES).build()

    suspend fun download(url: String, destination: File, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        var downloaded = 0L
        var total = -1L
        destination.outputStream().use { output ->
            do {
                currentCoroutineContext().ensureActive()
                val call = client.newCall(Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://www.youtube.com/")
                    .header("Accept-Encoding", "identity")
                    .header("Range", "bytes=$downloaded-${downloaded + 4 * 1024 * 1024 - 1}").build())
                // A child job closes the body/socket promptly even while a blocking read is in progress.
                kotlinx.coroutines.coroutineScope {
                    val closer = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                        try { kotlinx.coroutines.awaitCancellation() } finally { call.cancel() }
                    }
                    try {
                        call.awaitResponse().use { response ->
                            if (!response.isSuccessful) throw StreamHttpException(response.code)
                            val body = response.body ?: throw IOException("Sunucu boş yanıt verdi.")
                            val range = response.header("Content-Range")?.let { ContentRange.parse(it) }
                            if (response.code == 206) {
                                checkNotNull(range) { "Sunucu geçersiz Content-Range döndürdü." }
                                if (range.start != downloaded || (total > 0 && range.total != total)) throw IOException("Medya parçaları tutarsız.")
                                total = range.total
                            } else {
                                if (downloaded > 0) throw IOException("Sunucu parçalı indirmeyi devam ettirmedi.")
                                val declaredLength = response.request.url.queryParameter("clen")?.toLongOrNull() ?: -1L
                                total = maxOf(body.contentLength(), declaredLength)
                            }
                            val expected = range?.let { it.end - it.start + 1 } ?: body.contentLength()
                            var received = 0L
                            body.byteStream().use { input ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                    received += count
                                    downloaded += count
                                    onProgress(downloaded, total)
                                }
                            }
                            if (received == 0L || (expected >= 0 && received != expected)) throw IOException("Medya dosyası eksik indirildi.")
                        }
                    } finally { closer.cancel() }
                }
            } while (total > 0 && downloaded < total)
            output.flush()
        }
        if (total > 0 && downloaded != total) throw IOException("Dosya boyutu beklenen değerle uyuşmuyor.")
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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
