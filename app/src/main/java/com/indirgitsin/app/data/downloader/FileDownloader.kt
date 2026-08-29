package com.indirgitsin.app.data.downloader

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.indirgitsin.app.data.model.StreamOption
import com.indirgitsin.app.data.model.VideoInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
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

        Toast.makeText(context, "İndiriliyor: $fileName", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url(option.url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://www.youtube.com/")
                    .header("Origin", "https://www.youtube.com")
                    .header("Accept", "*/*")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw Exception("Sunucu hatası: ${response.code} - ${response.message}")

                val body = response.body ?: throw Exception("Boş yanıt")
                val input = body.byteStream()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, if (option.isAudioOnly) "audio/mp4" else "video/mp4")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw Exception("Dosya oluşturulamadı")

                    resolver.openOutputStream(uri)?.use { out ->
                        input.copyTo(out)
                    }
                    input.close()

                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "İndirildi: $fileName\nİndirilenler klasöründe", Toast.LENGTH_LONG).show()
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, fileName)
                    file.outputStream().use { out ->
                        input.copyTo(out)
                    }
                    input.close()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "İndirildi: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    }
                }
                body.close()
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "İndirme hatası: ${e.message}", Toast.LENGTH_LONG).show()
                }
                // Fallback: DownloadManager dene
                try {
                    fallbackDownloadManager(context, option.url, fileName, option)
                } catch (_: Exception) {}
            }
        }
    }

    private fun fallbackDownloadManager(context: Context, url: String, fileName: String, option: StreamOption) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription("İndir Gitsin • ${option.label}")
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setMimeType(if (option.isAudioOnly) "audio/*" else "video/*")
                addRequestHeader("User-Agent", "Mozilla/5.0")
                addRequestHeader("Referer", "https://www.youtube.com/")
            }
            dm.enqueue(request)
        } catch (_: Exception) {}
    }
}
