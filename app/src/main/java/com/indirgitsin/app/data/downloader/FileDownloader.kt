package com.indirgitsin.app.data.downloader

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.indirgitsin.app.data.model.StreamOption
import com.indirgitsin.app.data.model.VideoInfo
import java.net.URLEncoder

object FileDownloader {

    fun enqueue(context: Context, video: VideoInfo, option: StreamOption): Long {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val safeTitle = video.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
        val ext = option.extension.ifBlank { if (option.isAudioOnly) "m4a" else "mp4" }
        val fileName = "${safeTitle}_${option.quality.ifBlank{option.label.replace(" ","_") }}.$ext"

        val request = DownloadManager.Request(Uri.parse(option.url)).apply {
            setTitle(safeTitle)
            setDescription("İndir Gitsin • ${option.label}")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setMimeType(if (option.isAudioOnly) "audio/*" else "video/*")
            addRequestHeader("User-Agent", "Mozilla/5.0")
        }
        return dm.enqueue(request)
    }
}
