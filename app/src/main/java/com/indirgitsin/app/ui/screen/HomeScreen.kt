package com.indirgitsin.app.ui.screen

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.indirgitsin.app.data.history.HistoryDao
import com.indirgitsin.app.data.model.StreamOption
import com.indirgitsin.app.data.model.UiState
import com.indirgitsin.app.data.model.VideoInfo
import com.indirgitsin.app.ui.theme.YtRed
import com.indirgitsin.app.util.YoutubeLinkHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    inputUrl: String,
    onInputChange: (String) -> Unit,
    uiState: UiState,
    onFetch: (String) -> Unit,
    onDownload: (VideoInfo, StreamOption) -> Unit,
    onPaste: () -> Unit,
    historyDao: HistoryDao? = null,
    onHistoryClick: (String) -> Unit = {}
) {
    var showSheet by remember { mutableStateOf(false) }
    var selectedVideo by remember { mutableStateOf<VideoInfo?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var selectedChip by remember { mutableStateOf(0) }
    val context = LocalContext.current

    val doPaste = {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = cm?.primaryClip?.getItemAt(0)?.text?.toString()
        val url = YoutubeLinkHelper.findYoutubeUrlInText(text) ?: text
        if (!url.isNullOrBlank()) {
            onInputChange(url)
            if (YoutubeLinkHelper.isValidYoutubeUrl(url)) onFetch(url)
        }
        onPaste()
    }

    LaunchedEffect(uiState) {
        if (uiState is UiState.Success) {
            selectedVideo = uiState.video
            showSheet = true
            selectedTab = 0
        }
    }

    Scaffold(
        topBar = { YtTopBar() },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Search bar - YouTube search style
            item {
                YtSearchBar(
                    inputUrl = inputUrl,
                    onInputChange = onInputChange,
                    onFetch = onFetch,
                    onPaste = doPaste
                )
            }

            // Chips - YouTube filter chips
            item {
                YtFilterChips(selectedChip = selectedChip, onChipSelected = { selectedChip = it })
            }

            // Content area
            item {
                Box(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
                    AnimatedContent(
                        targetState = uiState,
                        transitionSpec = { fadeIn() + scaleIn(initialScale = 0.98f) togetherWith fadeOut() },
                        label = "state"
                    ) { state ->
                        when (state) {
                            is UiState.Loading -> YtShimmerCard()
                            is UiState.Error -> YtErrorCard(state.message)
                            is UiState.Success -> YtVideoCard(video = state.video, onClick = { showSheet = true })
                            else -> YtEmptyState()
                        }
                    }
                }
            }

            // Geçmiş chips
            item {
                Box(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    YtRecentSection(historyDao, onHistoryClick)
                }
            }

            // Nasıl çalışır - YouTube style 3 steps
            item {
                Box(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    YtHowItWorks()
                }
            }

            item {
                Box(Modifier.padding(16.dp)) {
                    Text(
                        "Sadece kendi içeriklerin veya izinli videolar için kullanın. YouTube Hizmet Şartlarına uyun.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showSheet && selectedVideo != null) {
        YtDownloadSheet(
            video = selectedVideo!!,
            selectedTab = selectedTab,
            onTabChange = { selectedTab = it },
            onDismiss = { showSheet = false },
            onDownload = { opt -> onDownload(selectedVideo!!, opt); showSheet = false }
        )
    }
}

@Composable
private fun YtTopBar() {
    val context = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // YouTube logo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(width = 32.dp, height = 22.dp).clip(RoundedCornerShape(4.dp))
                        .background(YtRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text("İndir Gitsin", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(6.dp))
                Surface(shape = RoundedCornerShape(4.dp), color = YtRed) {
                    Text("  PREMIUM  ", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF8E44AD)).clickable { android.widget.Toast.makeText(context, "Sen sekmesine geç", android.widget.Toast.LENGTH_SHORT).show() }, contentAlignment = Alignment.Center) {
                Text("E", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun YtPremiumBanner() {
    val context = LocalContext.current
    val gradient = Brush.horizontalGradient(listOf(Color(0xFFFF0000), Color(0xFFCC0000)))
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp)).background(gradient).clickable { android.widget.Toast.makeText(context, "İndirdiğin videoları reklamsız, offline ve arka planda oynat", android.widget.Toast.LENGTH_SHORT).show() }.padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Bolt, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Arka planda oynat • Reklamsız • İndir", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text("YouTube Music dahil • Tek dokunuşla indir", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Rounded.ArrowForward, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun YtSearchBar(
    inputUrl: String,
    onInputChange: (String) -> Unit,
    onFetch: (String) -> Unit,
    onPaste: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // YouTube search bar style - rounded, dark
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("YouTube linkini yapıştır", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
                if (inputUrl.isNotBlank()) {
                    IconButton(onClick = { onInputChange("") }) { Icon(Icons.Rounded.Clear, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(36.dp)) {
                    IconButton(onClick = { onFetch(inputUrl) }, enabled = inputUrl.isNotBlank(), modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.background, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(
                onClick = onPaste,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.ContentPaste, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Yapıştır", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { onFetch(inputUrl) },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = YtRed),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                enabled = inputUrl.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Bolt, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Çözümle", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun YtFilterChips(selectedChip: Int, onChipSelected: (Int) -> Unit) {
    val chips = listOf("Tümü", "Video", "Music", "Shorts", "4K")
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEachIndexed { idx, label ->
            val selected = idx == selectedChip
            FilterChip(
                selected = selected,
                onClick = { onChipSelected(idx) },
                label = { Text(label, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.onBackground,
                    selectedLabelColor = MaterialTheme.colorScheme.background,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp),
                border = null
            )
        }
    }
}

@Composable
private fun YtShimmerCard() {
    val transition = rememberInfiniteTransition(label = "ytShimmer")
    val alpha by transition.animateFloat(initialValue = 0.3f, targetValue = 0.8f, animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().height(180.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.3f)))
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.85f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.4f)))
                Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.2f)))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.2f)))
                    Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(8.dp)).background(YtRed.copy(alpha = alpha * 0.2f)))
                }
            }
        }
    }
}

@Composable
private fun YtErrorCard(message: String) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Error, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(10.dp))
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun YtEmptyState() {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.OndemandVideo, null, tint = YtRed, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text("YouTube linkini yukarı yapıştır, kaliteler anında gelsin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun YtRecentSection(historyDao: HistoryDao?, onHistoryClick: (String) -> Unit) {
    var recent by remember { mutableStateOf<List<com.indirgitsin.app.data.history.HistoryEntity>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(historyDao) { historyDao?.observeHistory()?.collect { recent = it.take(4) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Tekrar izle", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (recent.isNotEmpty() && historyDao != null) {
                TextButton(onClick = { scope.launch { historyDao.clearAll() } }) { Text("Temizle", style = MaterialTheme.typography.labelSmall) }
            }
        }
        if (recent.isEmpty()) {
            Text("Çözümlediğin videolar burada görünecek.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recent.forEach { item ->
                    YtHistoryRow(item = item, onClick = { onHistoryClick(item.url) })
                }
            }
        }
    }
}

@Composable
private fun YtHistoryRow(item: com.indirgitsin.app.data.history.HistoryEntity, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.thumbnailUrl.ifBlank { "https://i.ytimg.com/vi/${item.videoId}/hqdefault.jpg" },
                contentDescription = null,
                modifier = Modifier.size(width = 100.dp, height = 56.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text(item.author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun YtHowItWorks() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Nasıl indirilir?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            YtStep("1", "Kopyala", Icons.Rounded.Link)
            YtStep("2", "Paylaş", Icons.Rounded.Share)
            YtStep("3", "İndir", Icons.Rounded.Download)
        }
    }
}

@Composable
private fun RowScope.YtStep(num: String, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.weight(1f)) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(YtRed), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Text(num, color = YtRed, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall)
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun YtVideoCard(video: VideoInfo, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column {
            Box {
                AsyncImage(
                    model = video.thumbnailUrl.ifBlank { "https://i.ytimg.com/vi/${video.id}/hqdefault.jpg" },
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.8f)
                ) {
                    Text(
                        YoutubeLinkHelper.formatDuration(video.durationSeconds),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                // Premium badge
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = YtRed
                ) {
                    Text(" ${video.streams.size} kalite ", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text(video.author.take(1).uppercase(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.2)
                    Text("${video.author} • ${formatViews(video.viewCount)} • ${YoutubeLinkHelper.formatDuration(video.durationSeconds)}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // YouTube chips
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(" 4K ", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                        }
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AudioFile, null, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("MP3", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                IconButton(onClick = onClick, modifier = Modifier.size(24.dp)) { Icon(Icons.Rounded.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
            }
            // YouTube style action row
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onClick, shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 10.dp)) {
                    Icon(Icons.Rounded.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("İndir", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onClick, shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 10.dp)) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Önizle")
                }
            }
        }
    }
}

private fun formatViews(v: Long): String = when {
    v >= 1_000_000 -> String.format("%.1f Mn", v / 1_000_000.0).replace(".0", "")
    v >= 1_000 -> String.format("%.1f B", v / 1_000.0).replace(".0", "")
    v == 0L -> "—"
    else -> v.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YtDownloadSheet(
    video: VideoInfo,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onDownload: (StreamOption) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AsyncImage(
                    model = video.thumbnailUrl.ifBlank { "https://i.ytimg.com/vi/${video.id}/hqdefault.jpg" },
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(video.author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // YouTube Premium style segmented
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(selected = selectedTab == 0, onClick = { onTabChange(0) }, shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp), icon = { Icon(Icons.Rounded.Videocam, null, modifier = Modifier.size(16.dp)) }) { Text("Video") }
                SegmentedButton(selected = selectedTab == 1, onClick = { onTabChange(1) }, shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp), icon = { Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(16.dp)) }) { Text("Ses") }
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
                        YtOptionRow(label = opt.label, sublabel = "${opt.extension.uppercase()} • ${if (opt.isVideo) "Görüntü + Ses" else "Yalnız ses"}", isVideo = opt.isVideo, onClick = { onDownload(opt) })
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Folder, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("İndirilenler • Bildirimden takip et", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun YtOptionRow(label: String, sublabel: String, isVideo: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = YtRed.copy(alpha = 0.12f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(if (isVideo) Icons.Rounded.VideoFile else Icons.Rounded.Headphones, null, tint = YtRed, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(sublabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onClick, shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = YtRed, contentColor = Color.White), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("İndir", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(showBackground = true, name = "YouTube Premium Preview")
@Composable
private fun YtPreview() {
    com.indirgitsin.app.ui.theme.IndirGitsinTheme {
        HomeScreen(
            inputUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            onInputChange = {},
            uiState = com.indirgitsin.app.data.model.UiState.Success(
                com.indirgitsin.app.data.model.VideoInfo(
                    id = "dQw4w9WgXcQ", title = "YouTube Premium - İndir Gitsin Demo: 4K Video Nasıl İndirilir? Uzun Başlık Testi", author = "Demo Kanal",
                    thumbnailUrl = "", durationSeconds = 212, viewCount = 1234567, url = "",
                    streams = listOf(
                        com.indirgitsin.app.data.model.StreamOption("1080p • MP4", "mp4", "1080p", "https://example.com", true, false),
                        com.indirgitsin.app.data.model.StreamOption("720p • MP4", "mp4", "720p", "https://example.com", true, false),
                        com.indirgitsin.app.data.model.StreamOption("Ses • M4A 128kbps", "m4a", "128kbps", "https://example.com", false, true, bitrate = 128)
                    )
                )
            ),
            onFetch = { _ -> },
            onDownload = { _, _ -> },
            onPaste = {}
        )
    }
}
