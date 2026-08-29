package com.indirgitsin.app.data.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import com.indirgitsin.app.MainActivity
import com.indirgitsin.app.R
import com.indirgitsin.app.data.SettingsStore
import com.indirgitsin.app.data.model.StreamOption
import com.indirgitsin.app.data.model.VideoInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object FileDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun enqueue(context: Context, video: VideoInfo, option: StreamOption) {
        val safeTitle = video.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60).trim()
        val ext = option.extension.ifBlank { if (option.isAudioOnly) "m4a" else "mp4" }
        val qualityPart = option.quality.ifBlank { option.label.replace(" ", "_").take(20) }
        val fileName = "${safeTitle}_${qualityPart}.$ext".replace(Regex("[^a-zA-Z0-9._-]"), "_")

        CoroutineScope(Dispatchers.Main).launch {
            val subfolder = try { withTimeoutOrNull(1500) { SettingsStore.downloadSubfolderFlow(context).first() } ?: "IndirGitsin" } catch (_: Exception) { "IndirGitsin" }
            val relative = "${Environment.DIRECTORY_DOWNLOADS}/$subfolder"
            val destFile = "$subfolder/$fileName"
            try {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                val request = android.app.DownloadManager.Request(android.net.Uri.parse(option.url)).apply {
                    setTitle(fileName)
                    setDescription("İndir Gitsin • ${option.label}")
                    setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, destFile)
                    setMimeType(if (option.isAudioOnly) "audio/*" else "video/*")
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                    addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    addRequestHeader("Referer", "https://www.youtube.com/")
                    addRequestHeader("Origin", "https://www.youtube.com")
                }
                dm.enqueue(request)
                android.widget.Toast.makeText(context, "İndiriliyor: $fileName", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "İndirme hatası: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
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
