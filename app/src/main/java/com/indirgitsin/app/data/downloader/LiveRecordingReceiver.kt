package com.indirgitsin.app.data.downloader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class LiveRecordingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = runCatching { UUID.fromString(intent.getStringExtra("resumeId")) }.getOrNull() ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { LiveRecordingControl.requestStop(context.applicationContext, id) }
            catch (_: Exception) { android.util.Log.w("LiveRecording", "Durdurma isteği kaydedilemedi") }
            finally { pending.finish() }
        }
    }
}
