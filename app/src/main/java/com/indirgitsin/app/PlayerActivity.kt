package com.indirgitsin.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.indirgitsin.app.data.SettingsStore
import com.indirgitsin.app.data.lang.LocalAppLanguage
import com.indirgitsin.app.ui.screen.VideoPlaybackModel
import com.indirgitsin.app.ui.screen.VideoPlayerScreen
import com.indirgitsin.app.ui.theme.IndirGitsinTheme

/** A dedicated, private playback window keeps app navigation out of the video surface. */
class PlayerActivity : ComponentActivity() {
    private val playback: VideoPlaybackModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        if (uri == null || uri.scheme !in setOf("content", "file")) { finish(); return }
        enableEdgeToEdge()
        playback.open(uri)
        setContent {
            val language by SettingsStore.languageFlow(this).collectAsState(initial = "tr")
            CompositionLocalProvider(LocalAppLanguage provides language) {
                IndirGitsinTheme(darkTheme = true) {
                    VideoPlayerScreen(playback, intent.getStringExtra("title").orEmpty(), onBack = { finish() })
                }
            }
        }
    }

    override fun onStop() {
        playback.checkpoint()
        if (!isChangingConfigurations) playback.player.pause()
        super.onStop()
    }

    companion object {
        fun intent(context: Context, uri: Uri, title: String) = Intent(context, PlayerActivity::class.java)
            .setData(uri).putExtra("title", title)
    }
}
