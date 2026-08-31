package com.indirgitsin.app.data.downloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class RecordedHls(val source: File, val seconds: Long, val reason: String)

/** Commits complete segments on disk; a restarted worker saves that prefix instead of adding a gap. */
object HlsRecorder {
    private const val MAX_BYTES = 2L * 1024 * 1024 * 1024
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).followSslRedirects(false).build()

    fun recover(directory: File): RecordedHls? {
        val backup = File(directory, "live.properties.bak")
        val checkpoint = if (backup.isFile) backup else File(directory, "live.properties")
        val source = File(directory, "live.media")
        if (!checkpoint.isFile) return null
        val saved = Properties().apply { checkpoint.inputStream().use { load(it) } }
        val bytes = saved.getProperty("bytes")?.toLongOrNull() ?: error("Canlı kayıt bilgisi bozuk.")
        val seconds = saved.getProperty("seconds")?.toDoubleOrNull() ?: error("Canlı kayıt süresi bozuk.")
        check(bytes in 1..MAX_BYTES && source.length() >= bytes && seconds.isFinite() && seconds > 0) { "Canlı kayıt dosyası eksik." }
        RandomAccessFile(source, "rw").use { it.setLength(bytes) }
        return RecordedHls(source, seconds.toLong(), "Kesintiden önceki bölüm kurtarıldı")
    }

    suspend fun record(url: String, directory: File, minutes: Int, httpClient: OkHttpClient = client,
                       allowHttp: Boolean = false, onProgress: (Long, Long) -> Unit = { _, _ -> }): RecordedHls = withContext(Dispatchers.IO) {
        require(minutes in 1..60)
        check(directory.isDirectory || directory.mkdirs())
        recover(directory)?.let { return@withContext it }
        var playlistUrl = url
        var playlist = readPlaylist(playlistUrl, directory, httpClient, allowHttp)
        var depth = 0
        while (playlist.variants.isNotEmpty()) {
            check(depth++ < 3) { "HLS listesi döngü içeriyor." }
            playlistUrl = playlist.selectVariant().url
            playlist = readPlaylist(playlistUrl, directory, httpClient, allowHttp)
        }
        val source = File(directory, "live.media")
        val checkpoint = File(directory, "live.properties")
        val temporary = File(directory, "live-segment.tmp")
        var bytes = 0L
        var seconds = 0.0
        var next: Long? = null
        var initialization: HlsResource? = null
        var lastNewSegment = System.nanoTime()
        var reason = "Süre sınırına ulaşıldı"
        RandomAccessFile(source, "rw").use { output ->
            output.setLength(0)
            recording@ while (seconds < minutes * 60) {
                currentCoroutineContext().ensureActive()
                if (LiveRecordingControl.shouldStop(directory)) { reason = "Kayıt durdurulup kaydedildi"; break }
                if (next == null) next = (if (playlist.ended) playlist.segments.firstOrNull() else playlist.segments.lastOrNull())?.sequence
                if (next != null && (playlist.segments.firstOrNull()?.sequence ?: next!!) > next!!) {
                    reason = "Yayın parçası kaçırıldı; kesintisiz bölüm kaydedildi"; break
                }
                for (segment in playlist.segments.filter { it.sequence >= (next ?: Long.MAX_VALUE) }) {
                    currentCoroutineContext().ensureActive()
                    if (seconds + segment.seconds > minutes * 60) break@recording
                    if (LiveRecordingControl.shouldStop(directory)) { reason = "Kayıt durdurulup kaydedildi"; break@recording }
                    if (segment.gap || (bytes > 0 && (segment.discontinuity || segment.initialization != initialization))) {
                        reason = "Yayın biçimi değişti; mevcut bölüm kaydedildi"; break@recording
                    }
                    if (bytes == 0L && segment.initialization != null) {
                        fetch(segment.initialization, temporary, httpClient, 4L * 1024 * 1024, allowHttp)
                        temporary.inputStream().use { it.copyToOutput(output) }
                        initialization = segment.initialization
                    }
                    fetch(segment.resource, temporary, httpClient, 64L * 1024 * 1024, allowHttp)
                    if (output.length() + temporary.length() > MAX_BYTES || directory.usableSpace < temporary.length() + 64L * 1024 * 1024) {
                        reason = "Dosya veya boş alan sınırına ulaşıldı"; break@recording
                    }
                    temporary.inputStream().use { it.copyToOutput(output) }
                    output.fd.sync()
                    bytes = output.length(); seconds += segment.seconds
                    // Atomic rename retains the previous valid checkpoint if a write is interrupted.
                    val pending = File(directory, "live.properties.tmp")
                    pending.outputStream().use { sink -> Properties().apply {
                        setProperty("bytes", bytes.toString()); setProperty("seconds", seconds.toString())
                    }.store(sink, null); sink.fd.sync() }
                    val backup = File(directory, "live.properties.bak")
                    check(!backup.exists() || backup.delete())
                    check(!checkpoint.exists() || checkpoint.renameTo(backup))
                    check(pending.renameTo(checkpoint)) { "Canlı kayıt bilgisi kaydedilemedi." }
                    check(!backup.exists() || backup.delete())
                    next = Math.addExact(segment.sequence, 1)
                    lastNewSegment = System.nanoTime()
                    onProgress(bytes, seconds.toLong())
                    if (seconds >= minutes * 60) break@recording
                }
                if (LiveRecordingControl.shouldStop(directory)) { reason = "Kayıt durdurulup kaydedildi"; break }
                if (playlist.ended) { reason = "Yayın sona erdi"; break }
                if (System.nanoTime() - lastNewSegment > maxOf(30, playlist.targetSeconds * 3).coerceAtMost(120) * 1_000_000_000L) {
                    reason = "Yayın veri göndermeyi durdurdu; mevcut bölüm kaydedildi"; break
                }
                repeat((playlist.targetSeconds * 2).coerceIn(2, 20)) {
                    if (!LiveRecordingControl.shouldStop(directory)) delay(250)
                }
                if (LiveRecordingControl.shouldStop(directory)) { reason = "Kayıt durdurulup kaydedildi"; break }
                playlist = readPlaylist(playlistUrl, directory, httpClient, allowHttp)
                check(playlist.variants.isEmpty()) { "HLS akış türü değişti." }
            }
        }
        temporary.delete()
        check(bytes > 0) { "Henüz tamamlanmış yayın parçası yok; kayıt oluşmadı." }
        RecordedHls(source, seconds.toLong(), reason)
    }

    private suspend fun java.io.InputStream.copyToOutput(output: RandomAccessFile) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
    }

    private suspend fun readPlaylist(url: String, directory: File, client: OkHttpClient, allowHttp: Boolean): HlsPlaylist {
        val file = File(directory, "live-playlist.tmp")
        fetch(HlsResource(url), file, client, 2L * 1024 * 1024, allowHttp)
        return HlsPlaylist.parse(file.readText(Charsets.UTF_8), url, allowHttp)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun fetch(resource: HlsResource, file: File, client: OkHttpClient, limit: Long, allowHttp: Boolean) = coroutineScope {
        val parsed = java.net.URI(resource.url)
        require(parsed.scheme == "https" || (allowHttp && parsed.scheme == "http")) { "Güvenli olmayan yayın bağlantısı." }
        val request = Request.Builder().url(resource.url).header("User-Agent", "Mozilla/5.0")
            .header("Referer", "https://www.youtube.com/").header("Accept-Encoding", "identity")
        resource.range?.let { request.header("Range", "bytes=${it.offset}-${it.offset + it.length - 1}") }
        val call = client.newCall(request.build())
        val closer = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try {
            val response = suspendCancellableCoroutine<Response> { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                    override fun onResponse(call: Call, response: Response) {
                        if (continuation.isActive) continuation.resume(response) { response.close() } else response.close()
                    }
                })
            }
            response.use {
                if (!response.isSuccessful) throw StreamHttpException(response.code)
                check(resource.range != null || response.code == 200) { "Beklenmeyen kısmi HLS yanıtı." }
                val expected = resource.range?.length
                resource.range?.let { range ->
                    val content = response.header("Content-Range")?.let(ContentRange::parse)
                    check(response.code == 206 && content != null && content.start == range.offset && content.end == range.offset + range.length - 1) { "HLS parça aralığı doğrulanamadı." }
                }
                val body = response.body ?: throw IOException("Yayın yanıtı boş.")
                require(body.contentLength() <= limit) { "Yayın parçası çok büyük." }
                var count = 0L
                file.outputStream().use { sink -> body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val size = input.read(buffer)
                        if (size < 0) break
                        count += size
                        require(count <= limit && (expected == null || count <= expected)) { "Yayın parçası sınırı aşıldı." }
                        sink.write(buffer, 0, size)
                    }
                } }
                check(count > 0 && (expected == null || count == expected) && (body.contentLength() < 0 || count == body.contentLength())) { "Yayın parçası eksik." }
            }
        } finally { closer.cancel() }
    }
}
