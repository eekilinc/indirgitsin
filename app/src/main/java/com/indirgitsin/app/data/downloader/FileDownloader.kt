package com.indirgitsin.app.data.downloader

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.indirgitsin.app.data.SettingsStore
import com.indirgitsin.app.data.model.StreamOption
import com.indirgitsin.app.data.model.VideoInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.ByteBuffer
import java.text.Normalizer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

object FileDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private const val CHANNEL_ID = "indirgitsin_dl"

    fun enqueue(context: Context, video: VideoInfo, option: StreamOption) {
        val safeTitle = sanitizeFileName(video.title)
        val ext = option.extension.ifBlank { if (option.isAudioOnly) "m4a" else "mp4" }
        val qualityPart = option.quality.ifBlank { option.label.replace(" ", "_").take(20) }
        val fileName = "${safeTitle}_${qualityPart}.$ext"

        // Güvenlik Ağı: Eğer video ise ve ses akışı ayrık ise, AAC ses akışını bağla
        val effectiveAudioUrl = option.audioUrl ?: if (option.isVideo && option.quality != "360p") {
            video.streams.filter { it.isAudioOnly && it.extension == "m4a" }.maxByOrNull { it.bitrate }?.url
                ?: video.streams.filter { it.isAudioOnly }.maxByOrNull { it.bitrate }?.url
        } else null

        // Mux gerektiren (Video + Ses) İndirme
        if (effectiveAudioUrl != null) {
            val notificationId = (fileName + System.currentTimeMillis()).hashCode()
            Toast.makeText(context, "İndirme başlatıldı: $fileName", Toast.LENGTH_SHORT).show()

            CoroutineScope(Dispatchers.Main).launch {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                createNotificationChannel(context, notificationManager)

                val notifBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("İndiriliyor: $fileName")
                    .setContentText("Video ve ses indiriliyor...")
                    .setProgress(100, 0, false)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)

                notificationManager.notify(notificationId, notifBuilder.build())

                val subfolder = try {
                    withTimeoutOrNull(1500) { SettingsStore.downloadSubfolderFlow(context).first() } ?: "IndirGitsin"
                } catch (_: Exception) { "IndirGitsin" }

                val result = withContext(Dispatchers.IO) {
                    downloadAndMuxParallel(
                        context = context,
                        videoUrl = option.url,
                        audioUrl = effectiveAudioUrl,
                        subfolder = subfolder,
                        fileName = fileName,
                        onProgress = { progress, downloadedMB, totalMB ->
                            notifBuilder.setProgress(100, progress, false)
                            notifBuilder.setContentText("$progress% • ${String.format("%.1f", downloadedMB)} MB / ${String.format("%.1f", totalMB)} MB")
                            notificationManager.notify(notificationId, notifBuilder.build())
                        },
                        onMuxing = {
                            notifBuilder.setProgress(100, 99, true)
                            notifBuilder.setContentText("Video ve ses birleştiriliyor...")
                            notificationManager.notify(notificationId, notifBuilder.build())
                        }
                    )
                }

                if (result.first) {
                    val finalNotif = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle("İndirme Tamamlandı")
                        .setContentText(fileName)
                        .setOngoing(false)
                        .setAutoCancel(true)
                    notificationManager.notify(notificationId, finalNotif.build())
                    Toast.makeText(context, "İndirme ve birleştirme tamamlandı: $fileName", Toast.LENGTH_SHORT).show()
                } else {
                    val reason = result.second ?: "bilinmeyen hata"
                    val errorNotif = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_notify_error)
                        .setContentTitle("İndirme Başarısız")
                        .setContentText(reason)
                        .setOngoing(false)
                        .setAutoCancel(true)
                    notificationManager.notify(notificationId, errorNotif.build())
                    Toast.makeText(context, "Hata: $reason", Toast.LENGTH_LONG).show()
                }
            }
            return
        }

        // Doğrudan tek dosya indirme (Ses dosyaları veya inherent 360p)
        Toast.makeText(context, "İndirme başlatıldı: $fileName", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.Main).launch {
            val subfolder = try {
                withTimeoutOrNull(1500) { SettingsStore.downloadSubfolderFlow(context).first() } ?: "IndirGitsin"
            } catch (_: Exception) { "IndirGitsin" }
            enqueueSingleWithNotification(context, option.url, subfolder, fileName, option.label, option.isAudioOnly)
        }
    }

    private fun enqueueSingleWithNotification(context: Context, url: String, subfolder: String, fileName: String, label: String, isAudioOnly: Boolean) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                showNotification(ctx, fileName)
                try { ctx.unregisterReceiver(this) } catch (_: Exception) {}
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
        } catch (_: Exception) {}
        enqueueSingle(context, url, subfolder, fileName, label, isAudioOnly)
    }

    private fun enqueueSingle(context: Context, url: String, subfolder: String, fileName: String, label: String, isAudioOnly: Boolean) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription("İndir Gitsin • $label")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$subfolder/$fileName")
                setMimeType(if (isAudioOnly) "audio/*" else "video/*")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                addRequestHeader("Referer", "https://www.youtube.com/")
                addRequestHeader("Accept", "*/*")
            }
            dm.enqueue(request)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun sanitizeFileName(title: String): String {
        var result = Normalizer.normalize(title, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        if (result.isBlank()) result = "video"
        return if (result.length > 60) result.take(60).trim('_') else result
    }

    private suspend fun downloadAndMuxParallel(
        context: Context,
        videoUrl: String,
        audioUrl: String,
        subfolder: String,
        fileName: String,
        onProgress: (Int, Float, Float) -> Unit,
        onMuxing: () -> Unit
    ): Pair<Boolean, String?> = coroutineScope {
        var videoTmp: File? = null
        var audioTmp: File? = null
        var muxTmp: File? = null

        try {
            val cache = context.cacheDir
            videoTmp = File.createTempFile("vid_", ".mp4", cache)
            audioTmp = File.createTempFile("aud_", ".m4a", cache)

            val vDownloaded = AtomicLong(0L)
            val aDownloaded = AtomicLong(0L)
            val vTotal = AtomicLong(1L)
            val aTotal = AtomicLong(1L)

            var lastUpdate = 0L

            fun updateProgress() {
                val now = System.currentTimeMillis()
                if (now - lastUpdate > 300) {
                    lastUpdate = now
                    val downloaded = vDownloaded.get() + aDownloaded.get()
                    val total = vTotal.get() + aTotal.get()
                    if (total > 0) {
                        val pct = ((downloaded.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 98)
                        val downMB = downloaded / (1024f * 1024f)
                        val totalMB = total / (1024f * 1024f)
                        onProgress(pct, downMB, totalMB)
                    }
                }
            }

            // Video ve Sesi EŞ ZAMANLI (PARALEL) İNDİR
            val videoDeferred = async(Dispatchers.IO) {
                downloadToFileWithProgress(videoUrl, videoTmp) { bytesRead, totalBytes ->
                    vDownloaded.set(bytesRead)
                    if (totalBytes > 0) vTotal.set(totalBytes)
                    updateProgress()
                }
            }

            val audioDeferred = async(Dispatchers.IO) {
                downloadToFileWithProgress(audioUrl, audioTmp) { bytesRead, totalBytes ->
                    aDownloaded.set(bytesRead)
                    if (totalBytes > 0) aTotal.set(totalBytes)
                    updateProgress()
                }
            }

            val videoSuccess = videoDeferred.await()
            val audioSuccess = audioDeferred.await()

            if (!videoSuccess) return@coroutineScope false to "Video akışı indirilemedi"
            if (!audioSuccess) return@coroutineScope false to "Ses akışı indirilemedi"

            if (videoTmp.length() < 1024 || audioTmp.length() < 1024) {
                return@coroutineScope false to "İndirilen dosyalar eksik veya boş"
            }

            onMuxing()

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val outDir = File(downloadsDir, subfolder)
            if (!outDir.exists()) outDir.mkdirs()
            val outFile = File(outDir, fileName)

            muxTmp = File.createTempFile("mux_", ".mp4", cache)
            val muxRes = muxFilesInterleaved(videoTmp, audioTmp, muxTmp)
            if (!muxRes.first) {
                muxTmp.delete()
                return@coroutineScope false to (muxRes.second ?: "Muxing başarısız")
            }

            if (outFile.exists()) outFile.delete()
            muxTmp.copyTo(outFile, overwrite = true)
            muxTmp.delete()

            // Medya tarayıcısına kaydet
            try {
                val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                    data = Uri.fromFile(outFile)
                }
                context.sendBroadcast(scanIntent)
            } catch (_: Exception) {}

            true to null
        } catch (e: Exception) {
            e.printStackTrace()
            false to (e.message ?: "İşlem sırasında hata oluştu")
        } finally {
            try { videoTmp?.delete() } catch (_: Exception) {}
            try { audioTmp?.delete() } catch (_: Exception) {}
            try { muxTmp?.delete() } catch (_: Exception) {}
        }
    }

    private fun downloadToFileWithProgress(url: String, dest: File, onProgress: (Long, Long) -> Unit): Boolean {
        repeat(3) { attempt ->
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "*/*")
                    .header("Accept-Language", "en-US,en;q=0.9,tr;q=0.8")
                    .header("Referer", "https://www.youtube.com/")
                    .header("Connection", "keep-alive")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@repeat
                    }
                    val body = resp.body ?: return@repeat
                    val contentLength = body.contentLength()
                    dest.outputStream().use { outStream ->
                        body.byteStream().use { inStream ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            var totalRead = 0L
                            while (inStream.read(buffer).also { bytesRead = it } != -1) {
                                outStream.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                onProgress(totalRead, contentLength)
                            }
                            outStream.flush()
                        }
                    }
                    if (dest.length() > 1024) return true else dest.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (attempt == 2) return false
            }
        }
        return false
    }

    /**
     * Video ve Ses parçalarını kronolojik zaman damgasıyla (interleaved) sıralı birleştiren kararlı Muxer
     */
    private fun muxFilesInterleaved(videoFile: File, audioFile: File, outFile: File): Pair<Boolean, String?> {
        var muxer: MediaMuxer? = null
        var vExtractor: MediaExtractor? = null
        var aExtractor: MediaExtractor? = null

        return try {
            vExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            aExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            if (vExtractor.trackCount == 0) return false to "Video izi bulunamadı"
            if (aExtractor.trackCount == 0) return false to "Ses izi bulunamadı"

            var vTrackIdx = -1
            var aTrackIdx = -1

            for (i in 0 until vExtractor.trackCount) {
                val format = vExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    vTrackIdx = i
                    break
                }
            }

            for (i in 0 until aExtractor.trackCount) {
                val format = aExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    aTrackIdx = i
                    break
                }
            }

            if (vTrackIdx == -1) return false to "Geçerli video formatı bulunamadı"
            if (aTrackIdx == -1) return false to "Geçerli ses formatı bulunamadı"

            val vFormat = vExtractor.getTrackFormat(vTrackIdx)
            val aFormat = aExtractor.getTrackFormat(aTrackIdx)

            vExtractor.selectTrack(vTrackIdx)
            aExtractor.selectTrack(aTrackIdx)

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val vMuxIdx = muxer.addTrack(vFormat)
            val aMuxIdx = muxer.addTrack(aFormat)
            muxer.start()

            val buffer = ByteBuffer.allocateDirect(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            var lastVTime = -1L
            var lastATime = -1L
            var vStartOffset = -1L
            var aStartOffset = -1L

            var videoSampleCount = 0
            var audioSampleCount = 0

            var videoDone = false
            var audioDone = false

            while (!videoDone || !audioDone) {
                val vRawTime = if (!videoDone) vExtractor.sampleTime else Long.MAX_VALUE
                val aRawTime = if (!audioDone) aExtractor.sampleTime else Long.MAX_VALUE

                if (vRawTime < 0) videoDone = true
                if (aRawTime < 0) audioDone = true

                if (videoDone && audioDone) break

                if (!videoDone && (audioDone || vRawTime <= aRawTime)) {
                    buffer.clear()
                    val sampleSize = vExtractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        videoDone = true
                    } else {
                        if (vStartOffset < 0) vStartOffset = vRawTime
                        var pts = vRawTime - vStartOffset
                        if (pts <= lastVTime) {
                            pts = lastVTime + 1000L
                        }
                        lastVTime = pts

                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = pts
                        bufferInfo.flags = if ((vExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                            MediaCodec.BUFFER_FLAG_KEY_FRAME
                        } else {
                            0
                        }

                        buffer.position(0)
                        buffer.limit(sampleSize)

                        muxer.writeSampleData(vMuxIdx, buffer, bufferInfo)
                        videoSampleCount++
                        vExtractor.advance()
                    }
                } else if (!audioDone) {
                    buffer.clear()
                    val sampleSize = aExtractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        audioDone = true
                    } else {
                        if (aStartOffset < 0) aStartOffset = aRawTime
                        var pts = aRawTime - aStartOffset
                        if (pts <= lastATime) {
                            pts = lastATime + 1000L
                        }
                        lastATime = pts

                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = pts
                        bufferInfo.flags = if ((aExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                            MediaCodec.BUFFER_FLAG_KEY_FRAME
                        } else {
                            0
                        }

                        buffer.position(0)
                        buffer.limit(sampleSize)

                        muxer.writeSampleData(aMuxIdx, buffer, bufferInfo)
                        audioSampleCount++
                        aExtractor.advance()
                    }
                }
            }

            if (videoSampleCount == 0 || audioSampleCount == 0) {
                return false to "Veri okunamadı (Video: $videoSampleCount, Ses: $audioSampleCount)"
            }

            muxer.stop()
            true to null
        } catch (e: Exception) {
            e.printStackTrace()
            false to (e.message ?: "MediaMuxer hatası")
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            try { vExtractor?.release() } catch (_: Exception) {}
            try { aExtractor?.release() } catch (_: Exception) {}
        }
    }

    private fun createNotificationChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "İndirmeler", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Dosya indirme bildirimleri"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(context: Context, fileName: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(context, manager)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("İndirme Tamamlandı")
            .setContentText(fileName)
            .setAutoCancel(true)
        manager.notify(fileName.hashCode(), builder.build())
    }
}
