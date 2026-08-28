package com.indirgitsin.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.indirgitsin.app.data.extractor.YoutubeExtractor
import com.indirgitsin.app.data.model.UiState
import com.indirgitsin.app.util.YoutubeLinkHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    var historyDao: com.indirgitsin.app.data.history.HistoryDao? = null

    private val _inputUrl = MutableStateFlow("")
    val inputUrl: StateFlow<String> = _inputUrl

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    private val _pendingUrl = MutableStateFlow<String?>(null)
    val pendingUrl: StateFlow<String?> = _pendingUrl

    private val _clipboardSuggestion = MutableStateFlow<String?>(null)
    val clipboardSuggestion: StateFlow<String?> = _clipboardSuggestion

    fun onInputChange(value: String) { _inputUrl.value = value }

    fun setPendingUrl(url: String) { _pendingUrl.value = url }

    fun setClipboardSuggestion(url: String) { _clipboardSuggestion.value = url }

    fun clearClipboardSuggestion() { _clipboardSuggestion.value = null }

    fun fetch(url: String) {
        val normalized = YoutubeLinkHelper.findYoutubeUrlInText(url) ?: url
        if (!YoutubeLinkHelper.isValidYoutubeUrl(normalized)) {
            _uiState.value = UiState.Error("Geçerli bir YouTube / YouTube Music linki gir. Örn: https://youtu.be/...")
            return
        }
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val result = YoutubeExtractor.extract(normalized)
            result.onSuccess { video ->
                if (video.streams.isEmpty()) {
                    _uiState.value = UiState.Error("Video bulundu ama indirilebilir akış bulunamadı. Farklı bir video dene.")
                } else {
                    _uiState.value = UiState.Success(video)
                    // Geçmişe kaydet (premium: otomatik, gizli)
                    try {
                        historyDao?.insert(
                            com.indirgitsin.app.data.history.HistoryEntity(
                                videoId = video.id,
                                title = video.title,
                                author = video.author,
                                thumbnailUrl = video.thumbnailUrl,
                                durationSeconds = video.durationSeconds,
                                viewCount = video.viewCount,
                                url = video.url
                            )
                        )
                    } catch (_: Exception) {}
                }
            }.onFailure { e ->
                _uiState.value = UiState.Error(e.message ?: "Bilinmeyen hata oluştu")
            }
        }
    }

    fun reset() { _uiState.value = UiState.Idle }
}
