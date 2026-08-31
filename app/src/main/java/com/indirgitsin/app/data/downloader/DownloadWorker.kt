package com.indirgitsin.app.data.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.work.Data
import com.indirgitsin.app.MainActivity
import com.indirgitsin.app.data.SettingsStore
import com.indirgitsin.app.data.extractor.YoutubeExtractor
import com.indirgitsin.app.data.model.StreamSelector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

class DownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    companion object {
        private val slots = Semaphore(2)
        private const val CHANNEL = "media_downloads"
    }
    private val title = inputData.getString("title") ?: "Video"
    private val notificationId = id.hashCode()
    private val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        createChannel()
        setProgress(workDataOf("name" to title, "stage" to "Sırada"))
        try {
            slots.withPermit {
                setProgress(workDataOf("name" to title, "stage" to "Bağlantı çözümleniyor…"))
                setForeground(foreground("Bağlantı çözümleniyor…", 0))
                performDownload()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val reason = when (e) {
                is StreamHttpException -> "Medya bağlantısı reddedildi (HTTP ${e.status}). Yeniden deneyin."
                is IOException -> "Ağ veya dosya hatası: ${e.message.orEmpty().take(160)}"
                else -> e.message.orEmpty().take(180).ifBlank { "İndirme tamamlanamadı." }
            }
            if (e is IOException && runAttemptCount < 2) {
                setProgress(workDataOf("name" to title, "stage" to "Yeniden denenecek"))
                Result.retry()
            } else {
                showResult("İndirme başarısız", reason)
                Result.failure(Data.Builder().putAll(inputData).putString("name", title).putString("error", reason).build())
            }
        }
    }

    private suspend fun performDownload(): Result {
        val videoId = requireNotNull(inputData.getString("videoId"))
        val info = YoutubeExtractor.extract("https://www.youtube.com/watch?v=$videoId", applicationContext, forceRefresh = runAttemptCount > 0 || inputData.getBoolean("manualRetry", false)).getOrThrow()
        val candidates = info.streams.filter {
            it.isDownloadable && it.extension == inputData.getString("extension") &&
                it.quality == inputData.getString("quality") && it.isAudioOnly == inputData.getBoolean("audioOnly", false)
        }
        val option = (if (inputData.getBoolean("autoSelect", false)) {
            StreamSelector.preferred(info.streams, inputData.getBoolean("highQuality", true), inputData.getString("audioFormat") ?: "M4A")
        } else candidates.firstOrNull { it.codec == inputData.getString("codec") && it.isVideoOnly == inputData.getBoolean("videoOnly", false) }
            ?: candidates.firstOrNull()) ?: error("Seçilen sesli kalite artık mevcut değil. Videoyu tekrar açıp kalite seçin.")
        val extension = option.extension
        val safeTitle = title.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").trim('_', '.').take(70).ifBlank { "video" }
        val quality = option.quality.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)
        val name = "${safeTitle}_${quality}_${id.toString().take(8)}.$extension"
        // Keep active files away from the user-clearable thumbnail/cache directory.
        val directory = File(applicationContext.noBackupFilesDir, "downloads/$id")
        check(directory.isDirectory || directory.mkdirs()) { "Geçici indirme klasörü oluşturulamadı." }
        val primary = File(directory, "primary.part")
        val audio = File(directory, "audio.part")
        val output = File(directory, "output.$extension")
        val primaryBytes = AtomicLong()
        val audioBytes = AtomicLong()
        val primaryTotal = AtomicLong(-1)
        val audioTotal = AtomicLong(if (option.needsMuxing) -1 else 0)
        val lastUpdate = AtomicLong()
        val estimator = TransferProgress(SystemClock.elapsedRealtime())
        fun progress() {
            val now = SystemClock.elapsedRealtime()
            val previous = lastUpdate.get()
            if (now - previous < 600 || !lastUpdate.compareAndSet(previous, now)) return
            val downloaded = primaryBytes.get() + audioBytes.get()
            val total = if (primaryTotal.get() < 0 || audioTotal.get() < 0) -1 else primaryTotal.get() + audioTotal.get()
            val percent = if (total > 0) (downloaded * 95 / total).toInt().coerceIn(0, 95) else 0
            val estimate = estimator.update(now, downloaded, total)
            setProgressAsync(workDataOf("name" to name, "stage" to "İndiriliyor", "bytes" to downloaded,
                "total" to total, "percent" to percent, "speed" to estimate.bytesPerSecond, "eta" to (estimate.remainingSeconds ?: -1)))
            val speed = android.text.format.Formatter.formatShortFileSize(applicationContext, estimate.bytesPerSecond)
            notifySafely(notificationId, notification("İndiriliyor • $speed/sn", percent))
        }
        try {
            coroutineScope {
                val ticker = launch { while (true) { delay(1_000); progress() } }
                try {
                    val videoJob = async {
                        MediaTransfer.download(option.url, primary) { done, total -> primaryBytes.set(done); primaryTotal.set(total); progress() }
                    }
                    val audioJob = if (option.needsMuxing) async {
                        MediaTransfer.download(requireNotNull(option.audioUrl), audio) { done, total -> audioBytes.set(done); audioTotal.set(total); progress() }
                    } else null
                    videoJob.await()
                    audioJob?.await()
                } finally { ticker.cancel() }
            }
            currentCoroutineContext().ensureActive()
            val resultFile = if (option.needsMuxing) {
                setProgress(workDataOf("name" to name, "stage" to "Video ve ses birleştiriliyor", "percent" to 96))
                setForeground(foreground("Video ve ses birleştiriliyor", 96))
                MediaFileMuxer.mux(primary, audio, output, extension)
                output
            } else primary
            MediaFileMuxer.validate(resultFile, option.isVideo, info.durationSeconds)
            currentCoroutineContext().ensureActive()
            setProgress(workDataOf("name" to name, "stage" to "Dosya kaydediliyor", "percent" to 99))
            val folder = inputData.getString("folder") ?: SettingsStore.getDownloadSubfolderNow(applicationContext)
            val mime = DownloadStorage.mime(extension, option.isAudioOnly)
            val uri = DownloadStorage.publish(applicationContext, resultFile, name, mime, folder)
            showResult("İndirme tamamlandı", name)
            return Result.success(workDataOf("name" to name, "uri" to uri.toString(), "mime" to mime,
                "size" to resultFile.length(), "completedAt" to System.currentTimeMillis()))
        } finally {
            primary.delete()
            audio.delete()
            output.delete()
            directory.delete()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "İndirmeler", NotificationManager.IMPORTANCE_LOW))
    }
    private fun foreground(stage: String, percent: Int): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notificationId, notification(stage, percent), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notificationId, notification(stage, percent))

    private fun notification(stage: String, percent: Int): Notification =
        NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(title).setContentText(stage)
            .setProgress(100, percent, percent == 0).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "İptal", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()

    private fun showResult(heading: String, message: String) {
        val open = PendingIntent.getActivity(applicationContext, notificationId, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        notifySafely(notificationId xor Int.MIN_VALUE, NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle(heading).setContentText(message)
            .setContentIntent(open).setAutoCancel(true).build())
    }
    private fun notifySafely(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        try { manager.notify(id, notification) } catch (_: SecurityException) { /* Progress remains visible in-app. */ }
    }
}
