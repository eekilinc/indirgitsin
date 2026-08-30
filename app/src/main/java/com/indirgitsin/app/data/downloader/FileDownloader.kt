package com.indirgitsin.app.data.downloader

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.indirgitsin.app.data.model.StreamOption
import com.indirgitsin.app.data.model.VideoInfo
import com.indirgitsin.app.data.model.PlaylistVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

object FileDownloader {
    const val TAG = "media_download"

    fun enqueue(context: Context, video: VideoInfo, option: StreamOption): UUID {
        require(option.isDownloadable) { "Bu akış sesli video olarak indirilemiyor. Başka bir kalite seçin." }
        if (Build.VERSION.SDK_INT <= 28) {
            check(ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                "İndirmek için depolama izni gerekiyor."
            }
        }
        // Persist stable selectors, not expiring CDN URLs (or >10KB signed URLs in WorkManager Data).
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf("videoId" to video.id, "title" to video.title.take(180),
                "quality" to option.quality, "extension" to option.extension,
                "audioOnly" to option.isAudioOnly, "codec" to option.codec,
                "videoOnly" to option.isVideoOnly))
            .addTag(TAG).addTag("title:${video.title.take(180)}")
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
        return request.id
    }

    fun cancel(context: Context, id: UUID) {
        WorkManager.getInstance(context.applicationContext).cancelWorkById(id)
    }

    suspend fun enqueuePlaylist(context: Context, videos: List<PlaylistVideo>, highQuality: Boolean, audioFormat: String): Int = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT <= 28) check(ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            "İndirmek için depolama izni gerekiyor."
        }
        val requests = videos.map { video ->
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf("videoId" to video.id, "title" to video.title.take(180),
                    "autoSelect" to true, "highQuality" to highQuality, "audioFormat" to audioFormat))
                .addTag(TAG).addTag("title:${video.title.take(180)}")
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS).build()
        }
        if (requests.isNotEmpty()) WorkManager.getInstance(context.applicationContext).enqueue(requests).result.get()
        requests.size
    }
}
