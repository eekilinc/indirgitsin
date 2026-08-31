package com.indirgitsin.app.data.downloader

import android.content.Context
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

object PartialDownloads {
    /** Deletes only private, inactive transfer folders; never published Downloads files. */
    suspend fun clearInactive(context: Context, expiredOnly: Boolean = false): Long = withContext(Dispatchers.IO) {
        FileDownloader.enqueueLock.withLock {
            val active = WorkManager.getInstance(context.applicationContext).getWorkInfosByTag(FileDownloader.TAG).get()
                .filter { !it.state.isFinished }.flatMap { job ->
                    listOf(job.id.toString()) + job.tags.filter { it.startsWith("resume:") }.map { it.removePrefix("resume:") }
                }.toSet()
            val root = File(context.noBackupFilesDir, "downloads").canonicalFile
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
            var removed = 0L
            root.listFiles().orEmpty().forEach { folder ->
                if (folder.name !in active && folder.isDirectory && folder.canonicalFile.parentFile == root &&
                    runCatching { UUID.fromString(folder.name) }.isSuccess && (!expiredOnly || folder.lastModified() < cutoff)) {
                    val size = folder.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    if (folder.deleteRecursively()) removed += size
                }
            }
            removed
        }
    }
}
