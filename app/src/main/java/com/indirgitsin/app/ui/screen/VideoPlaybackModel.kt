package com.indirgitsin.app.ui.screen

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

/** Activity recreation retains the same player; process recreation restores the last checkpoint. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class VideoPlaybackModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    val player = ExoPlayer.Builder(application).build().apply {
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        setHandleAudioBecomingNoisy(true)
    }
    var uri: Uri? = null
        private set

    fun open(source: Uri) {
        if (source == uri) return
        val restore = saved.get<String>("uri") == source.toString()
        uri = source
        player.setMediaItem(MediaItem.fromUri(source))
        if (restore) {
            player.seekTo(saved.get<Long>("position") ?: 0)
            player.setPlaybackSpeed(saved.get<Float>("speed") ?: 1f)
        }
        player.prepare()
        player.playWhenReady = if (restore) saved.get<Boolean>("playing") ?: true else true
    }

    fun checkpoint() {
        saved["uri"] = uri?.toString()
        saved["position"] = player.currentPosition
        saved["speed"] = player.playbackParameters.speed
        saved["playing"] = player.playWhenReady
    }

    override fun onCleared() { player.release() }
}
