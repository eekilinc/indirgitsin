package com.indirgitsin.app.data.downloader

import android.content.Context
import java.io.File
import java.util.UUID

object LiveRecordingControl {
    fun requestStop(context: Context, resumeId: UUID) {
        val directory = File(context.noBackupFilesDir, "downloads/$resumeId")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "stop-recording").outputStream().use { it.write(1) }
    }
    fun shouldStop(directory: File): Boolean = File(directory, "stop-recording").isFile
}
