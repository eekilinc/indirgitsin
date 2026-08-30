package com.indirgitsin.app.data.downloader

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id == -1L) return

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val q = DownloadManager.Query().setFilterById(id)
        var title = "İndirme tamamlandı"
        var status = -1
        try {
            dm.query(q)?.use { c ->
                if (c.moveToFirst()) {
                    title = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: title
                    status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                }
            }
        } catch (_: Exception) {}
        if (status != DownloadManager.STATUS_SUCCESSFUL && status != DownloadManager.STATUS_FAILED) return

        val channelId = "indirgitsin_complete"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "İndirmeler", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "İndirme tamamlandı bildirimleri"
            }
            manager.createNotificationChannel(channel)
        }

        val openDownloadsIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pending = PendingIntent.getActivity(
            context, id.toInt(), openDownloadsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isSuccess = status == DownloadManager.STATUS_SUCCESSFUL
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(if (isSuccess) "İndirme tamamlandı" else "İndirme başarısız")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setAutoCancel(true)
            .setContentIntent(pending)

        try {
            // POST_NOTIFICATIONS izni yoksa SecurityException atar - yakala, sessiz geç
            NotificationManagerCompat.from(context).notify(id.toInt(), builder.build())
        } catch (_: SecurityException) {}
    }
}
