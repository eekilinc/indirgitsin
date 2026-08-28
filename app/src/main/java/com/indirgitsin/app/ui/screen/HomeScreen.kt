package com.indirgitsin.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.indirgitsin.app.data.model.StreamOption
import com.indirgitsin.app.data.model.UiState
import com.indirgitsin.app.data.model.VideoInfo
import com.indirgitsin.app.util.YoutubeLinkHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    inputUrl: String,
    onInputChange: (String) -> Unit,
    uiState: UiState,
    onFetch: () -> Unit,
    onDownload: (VideoInfo, StreamOption) -> Unit,
    onPaste: () -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    var selectedVideo by remember { mutableStateOf<VideoInfo?>(null) }

    LaunchedEffect(uiState) {
        if (uiState is UiState.Success) {
            selectedVideo = uiState.video
            showSheet = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("İndir Gitsin", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Hero Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("YouTube & Music", color = MaterialTheme.colorScheme.onPrimary.copy(alpha=0.9f), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        Text("Linki yapıştır, kaliteyi seç, indir gitsin.", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("YouTube uygulamasından → Paylaş → İndir Gitsin diyerek de indirebilirsin.", color = MaterialTheme.colorScheme.onPrimary.copy(alpha=0.85f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = inputUrl,
                            onValueChange = onInputChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("https://youtu.be/... veya https://music.youtube.com/...") },
                            leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                            trailingIcon = {
                                if (inputUrl.isNotBlank()) {
                                    IconButton(onClick = { onInputChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "Temizle") }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = onPaste, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Filled.ContentPaste, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Yapıştır")
                            }
                            Button(
                                onClick = onFetch,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                enabled = inputUrl.isNotBlank()
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Getir")
                            }
                        }
                        Text("İpucu: Linki kopyalayınca otomatik algılanır. YouTube Music linkleri de desteklenir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Durum kartları
            when (uiState) {
                is UiState.Loading -> item {
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Video bilgileri alınıyor…", fontWeight = FontWeight.SemiBold)
                                Text("En iyi kaliteler hazırlanıyor", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                is UiState.Error -> item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Text(uiState.message, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                is UiState.Success -> item {
                    VideoPreviewCard(video = uiState.video, onClick = { showSheet = true })
                }
                else -> {}
            }

            item {
                // Nasıl çalışır
                Text("Nasıl çalışır?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    HowItWorksStep("1", "Kopyala", "YouTube'da linki kopyala")
                    HowItWorksStep("2", "Paylaş", "Paylaş → İndir Gitsin")
                    HowItWorksStep("3", "İndir", "Kalite seç ve indir")
                }
            }

            item {
                Spacer(Modifier.height(40.dp))
                Text("Not: Bu uygulama sadece kendi içeriklerinizi veya indirme izni olan videolar için kullanılmalıdır. YouTube Hizmet Şartlarına uyunuz.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showSheet && selectedVideo != null) {
        DownloadOptionsSheet(
            video = selectedVideo!!,
            onDismiss = { showSheet = false },
            onDownload = { opt -> onDownload(selectedVideo!!, opt); showSheet = false }
        )
    }
}

@Composable
private fun RowScope.HowItWorksStep(number: String, title: String, desc: String) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.weight(1f)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Text(number, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VideoPreviewCard(video: VideoInfo, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp)) {
        Column {
            Box {
                AsyncImage(model = video.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().height(190.dp), contentScale = ContentScale.Crop)
                Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.scrim.copy(alpha=0.7f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(YoutubeLinkHelper.formatDuration(video.durationSeconds), color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(video.author, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("${video.viewCount} görüntüleme", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(onClick = onClick, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Seçenekler")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadOptionsSheet(video: VideoInfo, onDismiss: () -> Unit, onDownload: (StreamOption) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("İndirme Seçenekleri", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            val videoOptions = video.streams.filter { it.isVideo }
            val audioOptions = video.streams.filter { it.isAudioOnly }

            if (videoOptions.isNotEmpty()) {
                Text("Video", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                videoOptions.forEach { opt ->
                    DownloadOptionRow(label = opt.label, sublabel = opt.extension.uppercase(), isVideo = true, onClick = { onDownload(opt) })
                }
            }
            if (audioOptions.isNotEmpty()) {
                Text("Ses (MP3/M4A)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                audioOptions.forEach { opt ->
                    DownloadOptionRow(label = opt.label, sublabel = "Ses dosyası • ${opt.extension}", isVideo = false, onClick = { onDownload(opt) })
                }
            }
            if (video.streams.isEmpty()) {
                Text("İndirilebilir akış bulunamadı.", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            Text("İndirilenler klasörüne kaydedilir. Bildirimden takip edebilirsin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DownloadOptionRow(label: String, sublabel: String, isVideo: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isVideo) Icons.Filled.VideoFile else Icons.Filled.AudioFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Medium)
                Text(sublabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.Download, contentDescription = null)
        }
    }
}

@Preview(showBackground = true, name = "Home Preview")
@Composable
private fun HomePreview() {
    com.indirgitsin.app.ui.theme.IndirGitsinTheme {
        HomeScreen(
            inputUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            onInputChange = {},
            uiState = com.indirgitsin.app.data.model.UiState.Success(
                com.indirgitsin.app.data.model.VideoInfo(
                    id = "dQw4w9WgXcQ", title = "Örnek Video Başlığı - İndir Gitsin Demo", author = "Demo Kanal",
                    thumbnailUrl = "", durationSeconds = 212, viewCount = 1234567, url = "",
                    streams = listOf(
                        com.indirgitsin.app.data.model.StreamOption("1080p • MP4", "mp4", "1080p", "https://example.com", true, false),
                        com.indirgitsin.app.data.model.StreamOption("720p • MP4", "mp4", "720p", "https://example.com", true, false),
                        com.indirgitsin.app.data.model.StreamOption("Ses • M4A 128kbps", "m4a", "128kbps", "https://example.com", false, true, bitrate = 128)
                    )
                )
            ),
            onFetch = {},
            onDownload = { _, _ -> },
            onPaste = {}
        )
    }
}
