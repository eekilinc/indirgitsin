package com.indirgitsin.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.indirgitsin.app.ui.screen.HomeScreen
import com.indirgitsin.app.ui.theme.IndirGitsinTheme
import com.indirgitsin.app.util.YoutubeLinkHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        // Clipboard'ta link var mı kontrol et (uygulama açılınca)
        checkClipboard()

        setContent {
            IndirGitsinTheme {
                val uiState by vm.uiState.collectAsState()
                val inputUrl by vm.inputUrl.collectAsState()

                HomeScreen(
                    inputUrl = inputUrl,
                    onInputChange = vm::onInputChange,
                    uiState = uiState,
                    onFetch = { vm.fetch(inputUrl) },
                    onDownload = { video, option ->
                        try {
                            com.indirgitsin.app.data.downloader.FileDownloader.enqueue(this, video, option)
                            Toast.makeText(this, "İndirme başlatıldı: ${option.label}", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this, "İndirme hatası: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    onPaste = { pasteFromClipboard() }
                )

                // Intent ile gelen link varsa otomatik getir
                LaunchedEffect(Unit) {
                    vm.pendingUrl.collect { url ->
                        if (url != null) {
                            vm.onInputChange(url)
                            vm.fetch(url)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        var url: String? = null

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                url = YoutubeLinkHelper.findYoutubeUrlInText(text) ?: text
                if (!YoutubeLinkHelper.isValidYoutubeUrl(url)) {
                    // belki direkt link değil, text içinde ara
                    url = YoutubeLinkHelper.findYoutubeUrlInText(text)
                }
            }
            Intent.ACTION_VIEW -> {
                url = intent.dataString
            }
        }

        if (url != null && YoutubeLinkHelper.isValidYoutubeUrl(url)) {
            lifecycleScope.launch { vm.setPendingUrl(url) }
        } else if (url != null && intent.action == Intent.ACTION_SEND) {
            // kullanıcı bir şey paylaştı ama youtube değilse uyar
            // sessiz geç, home ekranında zaten input var
        }
    }

    private fun checkClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = cm.primaryClip?.getItemAt(0)?.text?.toString()
        val found = YoutubeLinkHelper.findYoutubeUrlInText(clip)
        if (found != null) {
            // otomatik doldur, ama indirme başlatma
            vm.setClipboardSuggestion(found)
        }
    }

    private fun pasteFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.text?.toString()
        val url = YoutubeLinkHelper.findYoutubeUrlInText(text) ?: text
        if (!url.isNullOrBlank()) {
            vm.onInputChange(url)
            if (YoutubeLinkHelper.isValidYoutubeUrl(url)) {
                vm.fetch(url)
            }
        } else {
            Toast.makeText(this, "Panoda link bulunamadı", Toast.LENGTH_SHORT).show()
        }
    }
}
