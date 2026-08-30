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
import java.text.Normalizer

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

        // Register Receiver
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

        CoroutineScope(Dispatchers.Main).launch {
            val subfolder = try { withTimeoutOrNull(1500) { SettingsStore.downloadSubfolderFlow(context).first() } ?: "IndirGitsin" } catch (_: Exception) { "IndirGitsin" }
            val destFile = "$subfolder/$fileName"
            try {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val request = DownloadManager.Request(android.net.Uri.parse(option.url)).apply {
                    setTitle(fileName)
                    setDescription("İndir Gitsin • ${option.label}")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, destFile)
                    setMimeType(if (option.isAudioOnly) "audio/*" else "video/*")
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                }
                dm.enqueue(request)
                Toast.makeText(context, "İndiriliyor: $fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sanitizeFileName(title: String): String {
        // Remove/replace Turkish characters and special chars for filesystem compatibility
        var result = Normalizer.normalize(title, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "") // Remove diacritics (ş->s, ğ->g, ı->i, ç->c, ö->o, ü->u)
            .replace(Regex("[\\\\/:*?\"<>|]"), "_") // Replace filesystem forbidden chars
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .trim()
        // Limit length, keep quality part space
        return if (result.length > 80) result.take(80).trim() else result
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
