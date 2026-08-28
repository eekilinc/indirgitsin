package com.indirgitsin.app.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.indirgitsin.app.data.history.HistoryDao
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
    onPaste: () -> Unit,
    historyDao: HistoryDao? = null,
    onHistoryClick: (String) -> Unit = {}
) {
    var showSheet by remember { mutableStateOf(false) }
    var selectedVideo by remember { mutableStateOf<VideoInfo?>(null) }
    var selectedTab by remember { mutableStateOf(0) } // 0 video, 1 ses

    LaunchedEffect(uiState) {
        if (uiState is UiState.Success) {
            selectedVideo = uiState.video
            showSheet = true
            selectedTab = 0
        }
    }

    val context = LocalContext.current
    val doPaste = {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = cm?.primaryClip?.getItemAt(0)?.text?.toString()
        val url = YoutubeLinkHelper.findYoutubeUrlInText(text) ?: text
        if (!url.isNullOrBlank()) {
            onInputChange(url)
            if (YoutubeLinkHelper.isValidYoutubeUrl(url)) onFetch()
        }
        onPaste()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFFFF1B1B), Color(0xFFFF6B6B))))
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("İndir Gitsin", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { HeroCard() }

            item { InputCard(inputUrl, onInputChange, onFetch, doPaste) }

            item {
                AnimatedContent(targetState = uiState, transitionSpec = {
                    fadeIn() + scaleIn(initialScale = 0.97f) togetherWith fadeOut()
                }, label = "state") { state ->
                    when (state) {
                        is UiState.Loading -> ShimmerLoadingCard()
                        is UiState.Error -> ErrorCard(state.message)
                        is UiState.Success -> VideoPreviewCard(video = state.video, onClick = { showSheet = true })
                        else -> EmptyHint()
                    }
                }
            }

            item { HowItWorksSection() }

            item { RecentSection(historyDao, onHistoryClick) }

            item {
                Text(
                    "Sadece kendi içeriklerin veya izinli videolar için kullan. YouTube Hizmet Şartlarına uy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    if (showSheet && selectedVideo != null) {
        DownloadOptionsSheet(
            video = selectedVideo!!,
            selectedTab = selectedTab,
            onTabChange = { selectedTab = it },
            onDismiss = { showSheet = false },
            onDownload = { opt -> onDownload(selectedVideo!!, opt); showSheet = false }
        )
    }
}

@Composable
private fun HeroCard() {
    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFF1B1B), Color(0xFFFF3B3B), Color(0xFFFF6B6B)),
        start = Offset(0f, 0f), end = Offset(1000f, 400f)
    )
    Card(
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(28.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier.fillMaxWidth().background(gradient).padding(22.dp)
        ) {
            // Dekoratif daire
            Box(
                Modifier.size(120.dp).align(Alignment.TopEnd).offset(x = 30.dp, y = (-30).dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = 0.12f))
            )
            Box(
                Modifier.size(80.dp).align(Alignment.BottomEnd).offset(x = 10.dp, y = 20.dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.2f)) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("YouTube • YouTube Music", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(shape = CircleShape, color = Color.White) {
                        Text(" 2 sn'de hazır ", color = Color(0xFFFF1B1B), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                Text(
                    "Linki yapıştır,\nkaliteyi seç,\nindir gitsin.",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge.copy(lineHeight = MaterialTheme.typography.headlineLarge.fontSize * 1.1),
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "YouTube uygulamasından → Paylaş → İndir Gitsin ile tek dokunuşta. Müzik ve video desteklenir.",
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun InputCard(
    inputUrl: String,
    onInputChange: (String) -> Unit,
    onFetch: () -> Unit,
    onPaste: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Video Linki", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = inputUrl,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://youtu.be/...  •  music.youtube.com/...") },
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (inputUrl.isNotBlank()) {
                        IconButton(onClick = { onInputChange("") }) { Icon(Icons.Rounded.Clear, contentDescription = "Temizle") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onPaste,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Yapıştır", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onFetch,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    enabled = inputUrl.isNotBlank()
                ) {
                    Icon(Icons.Rounded.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Çözümle", fontWeight = FontWeight.Bold)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Lightbulb, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Kopyaladığın link otomatik algılanır. Shorts ve Music dahil.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ShimmerLoadingCard() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.4f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.5f)))
                    Box(Modifier.fillMaxWidth(0.4f).height(10.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.3f)))
                }
            }
            Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.2f)))
            Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.2f)))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) {
                    Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.15f)))
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Text("Bir sorun oldu", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text("Bir YouTube linki yapıştır ve \"Çözümle\"ye dokun. En iyi kaliteler 2 saniyede hazır.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HowItWorksSection() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Nasıl çalışır?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            HowItWorksStep("1", "Kopyala", "YouTube'da linki kopyala", Icons.Rounded.ContentCopy)
            HowItWorksStep("2", "Paylaş", "Paylaş → İndir Gitsin", Icons.Rounded.Share)
            HowItWorksStep("3", "İndir", "Kalite seç ve indir", Icons.Rounded.Download)
        }
    }
}

@Composable
private fun RowScope.HowItWorksStep(number: String, title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.weight(1f)
    ) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFFF1B1B), Color(0xFFFF6B6B)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Text(number, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall)
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RecentSection(historyDao: HistoryDao?, onHistoryClick: (String) -> Unit) {
    var recent by remember { mutableStateOf<List<com.indirgitsin.app.data.history.HistoryEntity>>(emptyList()) }
    LaunchedEffect(historyDao) {
        historyDao?.observeHistory()?.collect { recent = it.take(5) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Son denediklerin", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (recent.isNotEmpty() && historyDao != null) {
                val scope = rememberCoroutineScope()
                TextButton(onClick = { scope.launch(kotlinx.coroutines.Dispatchers.IO) { historyDao.clearAll() } }) { Text("Temizle", style = MaterialTheme.typography.labelSmall) }
            }
        }
        if (recent.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("youtu.be/dQw4w...") }, leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp)) })
                AssistChip(onClick = {}, label = { Text("music.youtu.be/...") }, leadingIcon = { Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp)) })
            }
            Text("Geçmiş cihazında saklanır, gizlidir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recent.forEach { item ->
                    SuggestionChip(
                        onClick = { onHistoryClick(item.url) },
                        label = { Text(item.title.take(28) + "…", maxLines = 1) },
                        icon = { Icon(Icons.Rounded.History, null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoPreviewCard(video: VideoInfo, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(20.dp)),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            Box {
                AsyncImage(
                    model = video.thumbnailUrl.ifBlank { "https://i.ytimg.com/vi/${video.id}/hqdefault.jpg" },
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(196.dp),
                    contentScale = ContentScale.Crop
                )
                // Play overlay
                Box(
                    Modifier.align(Alignment.Center).size(56.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                // Duration badge
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.75f)
                ) {
                    Text(
                        YoutubeLinkHelper.formatDuration(video.durationSeconds),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                // Quality count badge
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        "${video.streams.size} seçenek",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Text(video.author.take(1).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(video.author, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.Verified, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text("${formatViews(video.viewCount)} görüntüleme", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(YoutubeLinkHelper.formatDuration(video.durationSeconds), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Kalite Seç ve İndir", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatViews(v: Long): String = when {
    v >= 1_000_000 -> String.format("%.1fM", v / 1_000_000.0)
    v >= 1_000 -> String.format("%.1fB", v / 1_000.0)
    v == 0L -> "—"
    else -> v.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadOptionsSheet(
    video: VideoInfo,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onDownload: (StreamOption) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = video.thumbnailUrl.ifBlank { "https://i.ytimg.com/vi/${video.id}/hqdefault.jpg" },
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(video.author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("İndirme Seçenekleri", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(selected = selectedTab == 0, onClick = { onTabChange(0) }, shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp), icon = { Icon(Icons.Rounded.Videocam, null, modifier = Modifier.size(16.dp)) }) { Text("Video") }
                SegmentedButton(selected = selectedTab == 1, onClick = { onTabChange(1) }, shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp), icon = { Icon(Icons.Rounded.AudioFile, null, modifier = Modifier.size(16.dp)) }) { Text("Ses") }
            }
            val filtered = if (selectedTab == 0) video.streams.filter { it.isVideo } else video.streams.filter { it.isAudioOnly }
            if (filtered.isEmpty()) {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("Bu kategoride seçenek yok", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    filtered.forEach { opt ->
                        DownloadOptionRow(label = opt.label, sublabel = "${opt.extension.uppercase()} • ${if (opt.isVideo) "Görüntü + Ses" else "Yalnız ses"}", isVideo = opt.isVideo, onClick = { onDownload(opt) })
                    }
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("İndirilenler klasörüne kaydedilir • Bildirimden takip et", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DownloadOptionRow(label: String, sublabel: String, isVideo: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(if (isVideo) Icons.Rounded.VideoFile else Icons.Rounded.AudioFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(sublabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onClick, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("İndir", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(showBackground = true, name = "Home Modern Preview")
@Composable
private fun HomePreviewModern() {
    com.indirgitsin.app.ui.theme.IndirGitsinTheme {
        HomeScreen(
            inputUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            onInputChange = {},
            uiState = com.indirgitsin.app.data.model.UiState.Success(
                com.indirgitsin.app.data.model.VideoInfo(
                    id = "dQw4w9WgXcQ", title = "Örnek Video Başlığı - İndir Gitsin Modern Demo Çok Uzun Başlık Testi", author = "Demo Kanal",
                    thumbnailUrl = "", durationSeconds = 212, viewCount = 1234567, url = "",
                    streams = listOf(
                        com.indirgitsin.app.data.model.StreamOption("1080p • MP4", "mp4", "1080p", "https://example.com", true, false),
                        com.indirgitsin.app.data.model.StreamOption("720p • MP4", "mp4", "720p", "https://example.com", true, false),
                        com.indirgitsin.app.data.model.StreamOption("Ses • M4A 128kbps", "m4a", "128kbps", "https://example.com", false, true, bitrate = 128),
                        com.indirgitsin.app.data.model.StreamOption("Ses • M4A 256kbps", "m4a", "256kbps", "https://example.com", false, true, bitrate = 256)
                    )
                )
            ),
            onFetch = {},
            onDownload = { _, _ -> },
            onPaste = {}
        )
    }
}
