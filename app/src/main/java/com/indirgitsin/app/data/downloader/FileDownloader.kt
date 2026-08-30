package com.indirgitsin.app.data.downloader

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.app.NotificationCompat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.indirgitsin.app.data.SettingsStore
import com.indirgitsin.app.data.model.StreamOption
import com.indirgitsin.app.data.model.VideoInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.ByteBuffer
import java.text.Normalizer
import java.util.concurrent.TimeUnit

object FileDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun enqueue(context: Context, video: VideoInfo, option: StreamOption) {
        // Better Turkish-compatible file name
        val safeTitle = sanitizeFileName(video.title)
        val ext = option.extension.ifBlank { if (option.isAudioOnly) "m4a" else "mp4" }
        val qualityPart = option.quality.ifBlank { option.label.replace(" ", "_").take(20) }
        val fileName = "${safeTitle}_${qualityPart}.$ext"

        // Mux gerektiren yüksek kalite video+ses birleştirme
        if (option.audioUrl != null) {
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "İndiriliyor (birleştiriliyor): $fileName", Toast.LENGTH_SHORT).show()
                val subfolder = try { withTimeoutOrNull(1500) { SettingsStore.downloadSubfolderFlow(context).first() } ?: "IndirGitsin" } catch (_: Exception) { "IndirGitsin" }
                val result = withContext(Dispatchers.IO) { downloadAndMuxWithReason(context, option.url, option.audioUrl!!, subfolder, fileName) }
                if (result.first) {
                    showNotification(context, fileName)
                    Toast.makeText(context, "İndirme tamamlandı: $fileName", Toast.LENGTH_SHORT).show()
                } else {
                    val reason = result.second ?: "bilinmeyen hata"
                    Toast.makeText(context, "Birleştirme başarısız ($reason), ayrı indiriliyor", Toast.LENGTH_LONG).show()
                    // Fallback: video ve sesi ayrı ayrı DownloadManager ile indir (en azından ikisi de olsun)
                    val audioExt = "m4a"
                    val audioName = fileName.substringBeforeLast(".") + "_ses.$audioExt"
                    enqueueSingle(context, option.url, subfolder, fileName, option.label, false)
                    enqueueSingle(context, option.audioUrl!!, subfolder, audioName, "Ses • $audioExt", true)
                }
            }
            return
        }

        // Normal tek dosya indirme (mux gerekmeyen / mux'lu 360p / ses)
        CoroutineScope(Dispatchers.Main).launch {
            val subfolder = try { withTimeoutOrNull(1500) { SettingsStore.downloadSubfolderFlow(context).first() } ?: "IndirGitsin" } catch (_: Exception) { "IndirGitsin" }
            enqueueSingleWithNotification(context, option.url, subfolder, fileName, option.label, option.isAudioOnly)
        }
    }

    private fun enqueueSingleWithNotification(context: Context, url: String, subfolder: String, fileName: String, label: String, isAudioOnly: Boolean) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                showNotification(ctx, fileName)
                ctx.unregisterReceiver(this)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
        enqueueSingle(context, url, subfolder, fileName, label, isAudioOnly)
    }

    private fun enqueueSingle(context: Context, url: String, subfolder: String, fileName: String, label: String, isAudioOnly: Boolean) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(android.net.Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription("İndir Gitsin \u2022 $label")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$subfolder/$fileName")
                setMimeType(if (isAudioOnly) "audio/*" else "video/*")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
                addRequestHeader("Referer", "https://www.youtube.com/")
                addRequestHeader("Accept", "*/*")
            }
            dm.enqueue(request)
            Toast.makeText(context, "İndiriliyor: $fileName", Toast.LENGTH_SHORT).show()
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

    private fun downloadAndMux(context: Context, videoUrl: String, audioUrl: String, subfolder: String, fileName: String): Boolean = downloadAndMuxWithReason(context, videoUrl, audioUrl, subfolder, fileName).first

    private fun downloadAndMuxWithReason(context: Context, videoUrl: String, audioUrl: String, subfolder: String, fileName: String): Pair<Boolean, String?> {
        var videoTmp: File? = null
        var audioTmp: File? = null
        return try {
            val cache = context.cacheDir
            videoTmp = File.createTempFile("vid_", ".tmp", cache)
            audioTmp = File.createTempFile("aud_", ".tmp", cache)
            if (!downloadToFile(videoUrl, videoTmp)) return false to "video indirilemedi (403/throttle)"
            if (!downloadToFile(audioUrl, audioTmp)) return false to "ses indirilemedi"
            if (videoTmp.length() < 1024 || audioTmp.length() < 1024) return false to "dosya çok küçük"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val outDir = File(downloadsDir, subfolder)
            if (!outDir.exists()) outDir.mkdirs()
            val outFile = File(outDir, fileName)
            val muxTmp = File.createTempFile("mux_", ".tmp", cache)
            val muxRes = muxFilesWithReason(videoTmp, audioTmp, muxTmp)
            if (!muxRes.first) {
                muxTmp.delete()
                return false to (muxRes.second ?: "mux başarısız")
            }
            if (outFile.exists()) outFile.delete()
            muxTmp.copyTo(outFile, overwrite = true)
            muxTmp.delete()
            try {
                val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                scanIntent.data = android.net.Uri.fromFile(outFile)
                context.sendBroadcast(scanIntent)
            } catch (_: Exception) {}
            true to null
        } catch (e: Exception) {
            e.printStackTrace()
            false to (e.message ?: "exception")
        } finally {
            try { videoTmp?.delete() } catch (_: Exception) {}
            try { audioTmp?.delete() } catch (_: Exception) {}
        }
    }

    private fun downloadToFile(url: String, dest: File): Boolean {
        // YouTube n throttling için Range header ve referer ekle, 1 retry
        repeat(2) { attempt ->
            try {
                val req = Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
                    .header("Accept", "*/*")
                    .header("Accept-Language", "en-US,en;q=0.9,tr;q=0.8")
                    .header("Referer", "https://www.youtube.com/")
                    .header("Range", "bytes=0-")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        // 403 throttled -> fail fast, fallback'a bırak
                        if (resp.code == 403 && attempt == 0) return@repeat
                        return false
                    }
                    val body = resp.body ?: return false
                    dest.outputStream().use { out -> body.byteStream().copyTo(out) }
                    if (dest.length() > 1024) return true else dest.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (attempt == 1) return false
            }
        }
        return false
    }

    private fun muxFiles(videoFile: File, audioFile: File, outFile: File): Boolean = muxFilesWithReason(videoFile, audioFile, outFile).first

    private fun muxFilesWithReason(videoFile: File, audioFile: File, outFile: File): Pair<Boolean, String?> {
        var muxer: MediaMuxer? = null
        var vExtractor: MediaExtractor? = null
        var aExtractor: MediaExtractor? = null
        return try {
            vExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            aExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }
            if (vExtractor.trackCount == 0) return false to "video track yok"
            if (aExtractor.trackCount == 0) return false to "audio track yok"
            val vTrackIdx = (0 until vExtractor.trackCount).firstOrNull { i -> vExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true } ?: 0
            val aTrackIdx = (0 until aExtractor.trackCount).firstOrNull { i -> aExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: 0
            val vFormat = vExtractor.getTrackFormat(vTrackIdx)
            val aFormat = aExtractor.getTrackFormat(aTrackIdx)
            val vMime = vFormat.getString(MediaFormat.KEY_MIME) ?: ""
            val aMime = aFormat.getString(MediaFormat.KEY_MIME) ?: ""
            // MP4 muxer sadece avc/aac destekler, vp9/opus ise fail -> nedeni bildir
            if (vMime.contains("vp9", true) || vMime.contains("vp8", true)) return false to "vp9 mp4'e muxlanamaz ($vMime)"
            if (aMime.contains("opus", true) && vMime.contains("mp4", true)) {
                // opus mp4 içinde sorun olabilir ama dene
            }
            vExtractor.selectTrack(vTrackIdx)
            aExtractor.selectTrack(aTrackIdx)
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val vMuxIdx = muxer.addTrack(vFormat)
            val aMuxIdx = muxer.addTrack(aFormat)
            muxer.start()
            copyTrack(vExtractor, muxer, vMuxIdx)
            copyTrack(aExtractor, muxer, aMuxIdx)
            muxer.stop()
            true to null
        } catch (e: Exception) {
            e.printStackTrace()
            false to (e.message ?: "mux exception")
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            try { vExtractor?.release() } catch (_: Exception) {}
            try { aExtractor?.release() } catch (_: Exception) {}
        }
    }

    private fun copyTrack(extractor: MediaExtractor, muxer: MediaMuxer, muxIdx: Int) {
        val buf = ByteBuffer.allocate(1024 * 512)
        val info = android.media.MediaCodec.BufferInfo()
        while (true) {
            info.offset = 0
            info.size = extractor.readSampleData(buf, 0)
            if (info.size < 0) break
            info.presentationTimeUs = extractor.sampleTime
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(muxIdx, buf, info)
            extractor.advance()
        }
    }

    private fun showNotification(context: Context, fileName: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "indirgitsin_dl"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "İndirmeler", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("İndirme Tamamlandı")
            .setContentText(fileName)
            .setAutoCancel(true)
        manager.notify(fileName.hashCode(), builder.build())
    }
}
