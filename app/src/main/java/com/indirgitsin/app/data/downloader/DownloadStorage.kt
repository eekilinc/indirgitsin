package com.indirgitsin.app.data.downloader

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

object DownloadStorage {
    suspend fun publish(context: Context, source: File, name: String, mime: String, folder: String): Uri {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= 29) {
            val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$folder/"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            // Recover our exact job's pending row after process death; never touch another download.
            var pending: Uri? = null
            resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.IS_PENDING, MediaStore.Downloads.SIZE),
                "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
                arrayOf(name, relativePath), null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0).toString())
                    if (cursor.getInt(1) == 0 && cursor.getLong(2) > 0) return uri
                    pending = uri
                }
            }
            val uri = pending ?: checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) { "İndirme dosyası oluşturulamadı." }
            try {
                checkNotNull(resolver.openOutputStream(uri, "wt")).use { output ->
                    source.inputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                check(resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) == 1)
                return uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        }
        @Suppress("DEPRECATION")
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), folder)
        check(directory.isDirectory || directory.mkdirs()) { "İndirme klasörü oluşturulamadı." }
        val target = File(directory, name)
        val partial = File(directory, "$name.part")
        try {
            partial.outputStream().use { output ->
                source.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            check(partial.renameTo(target)) { "Dosya indirme klasörüne taşınamadı." }
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
            return FileProvider.getUriForFile(context, "${context.packageName}.provider", target)
        } finally { partial.delete() }
    }

    fun mime(extension: String, audioOnly: Boolean): String = when (extension) {
        "mp4" -> "video/mp4"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "webm" -> if (audioOnly) "audio/webm" else "video/webm"
        "opus", "ogg" -> "audio/ogg"
        else -> "application/octet-stream"
    }
}
