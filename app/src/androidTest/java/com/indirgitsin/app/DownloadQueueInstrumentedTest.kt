package com.indirgitsin.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.indirgitsin.app.data.SettingsStore
import com.indirgitsin.app.data.downloader.DownloadWorker
import com.indirgitsin.app.data.downloader.FileDownloader
import com.indirgitsin.app.data.model.StreamOption
import com.indirgitsin.app.data.model.VideoInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadQueueInstrumentedTest {
    @Test fun duplicateNetworkConstraintFailureRetryAndCancellation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, parameters: WorkerParameters): ListenableWorker? {
                if (workerClassName != DownloadWorker::class.java.name) return null
                // Deterministic transfer failure, exercising the real persistent queue without using YouTube.
                return object : Worker(appContext, parameters) {
                    override fun doWork(): Result = Result.failure(Data.Builder().putAll(inputData)
                        .putString("name", inputData.getString("title"))
                        .putString("error", "Simulated offline failure").build())
                }
            }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder()
            .setExecutor(SynchronousExecutor()).setWorkerFactory(factory).build())
        val manager = WorkManager.getInstance(context)
        val driver = requireNotNull(WorkManagerTestInitHelper.getTestDriver(context))
        val video = VideoInfo("abcdefghijk", "Queue test", "", "", 5, 0, "https://youtu.be/abcdefghijk")
        val option = StreamOption("720p MP4", "mp4", "720p", "https://media.invalid/test", true, false)
        try {
            SettingsStore.setUnmetered(context, true)
            assertTrue(FileDownloader.enqueue(context, video, option))
            assertFalse(FileDownloader.enqueue(context, video, option))
            val pending = manager.getWorkInfosByTag(FileDownloader.TAG).get().single()
            assertEquals(WorkInfo.State.ENQUEUED, pending.state)
            assertEquals(NetworkType.UNMETERED, pending.constraints.requiredNetworkType)
            driver.setAllConstraintsMet(pending.id)
            val failed = manager.getWorkInfoById(pending.id).get()
            assertEquals(WorkInfo.State.FAILED, failed.state)
            assertEquals(video.id, failed.outputData.getString("videoId"))
            SettingsStore.setUnmetered(context, false)
            assertTrue(FileDownloader.retry(context, failed))
            val retry = manager.getWorkInfosByTag(FileDownloader.TAG).get().single { !it.state.isFinished }
            assertNotEquals(pending.id, retry.id)
            assertEquals(NetworkType.CONNECTED, retry.constraints.requiredNetworkType)
            manager.cancelWorkById(retry.id).result.get()
            assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(retry.id).get().state)
            assertTrue(FileDownloader.enqueue(context, video, option))
        } finally {
            manager.cancelAllWorkByTag(FileDownloader.TAG).result.get()
            SettingsStore.setUnmetered(context, false)
        }
    }
}
