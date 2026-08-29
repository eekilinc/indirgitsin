package com.indirgitsin.app.ui.screen

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DerivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ContentScale
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoUri: Uri,
    title: String = "Video",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }
    var playbackException by remember { mutableStateOf<PlaybackException?>(null) }

    val scope = rememberCoroutineScope()

    // Prepare player
    LaunchedEffect(Unit) {
        val mediaItem = MediaItem.fromUri(videoUri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // Cleanup
    androidx.compose.runtime.DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    // UI State
    var showControls by remember { mutableStateOf(true) }
    var controlsTimeoutJob by remember { mutableStateOf<Job?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }

    // Position tracking
    val currentPosition by remember {
        derivedStateOf { exoPlayer.currentPosition }
    }
    val duration by remember {
        derivedStateOf { exoPlayer.duration }
    }
    val isPlaying by remember {
        derivedStateOf { exoPlayer.playWhenReady && exoPlayer.playbackState == Player.STATE_READY }
    }
    val bufferedPosition by remember {
        derivedStateOf { exoPlayer.bufferedPosition }
    }

    // Auto-hide controls
    fun resetControlsTimeout() {
        controlsTimeoutJob?.cancel()
        showControls = true
        controlsTimeoutJob = scope.launch {
            delay(3000)
            showControls = false
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            resetControlsTimeout()
        } else {
            controlsTimeoutJob?.cancel()
            showControls = true
        }
    }

    val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
    val bufferedProgress = if (duration > 0) bufferedPosition.toFloat() / duration else 0f

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black)
            .onClick { resetControlsTimeout() }
    ) {
        // PlayerView (AndroidView wrapper)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top bar
        AnimatedVisibility(visible = showControls) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Geri", tint = Color.White)
                    }
                    Text(
                        title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        style = androidx.compose.ui.text.TextStyle(fontSize = 18.sp)
                    )
                    IconButton(onClick = { isFullscreen = !isFullscreen }) {
                        Icon(
                            if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            contentDescription = if (isFullscreen) "Tam ekrandan çık" else "Tam ekran",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Bottom controls
        AnimatedVisibility(visible = showControls) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                    .height(120.dp),
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Progress bar
                    Column {
                        Slider(
                            value = progress.coerceIn(0f, 1f),
                            onValueChange = { newProgress ->
                                val seekPosition = (newProgress * duration).toLong()
                                exoPlayer.seekTo(seekPosition)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFF0000),
                                activeTrackColor = Color(0xFFFF0000),
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                activeTickColor = Color.White.copy(alpha = 0.5f),
                                inactiveTickColor = Color.White.copy(alpha = 0.3f)
                            ),
                            thumb = { SliderDefaults.Thumb(
                                modifier = androidx.compose.ui.Modifier.size(12.dp),
                                color = Color(0xFFFF0000)
                            ) }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatTime(currentPosition),
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                            Text(
                                formatTime(duration),
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Playback controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Speed & Skip
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { exoPlayer.seekTo((currentPosition - 10_000).coerceAtLeast(0)) }) {
                                Icon(Icons.Rounded.Replay10, contentDescription = "10 sn geri", tint = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                }
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            IconButton(onClick = { exoPlayer.seekTo((currentPosition + 10_000).coerceAtMost(duration)) }) {
                                Icon(Icons.Rounded.Forward10, contentDescription = "10 sn ileri", tint = Color.White)
                            }
                        }

                        // Settings: Speed selector
                        MenuButton(
                            title = "Hız: ${playbackSpeed}x",
                            options = listOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x"),
                            onSelected = { selected ->
                                playbackSpeed = selected.replace("x", "").toFloat()
                                exoPlayer.playbackParameters = exoPlayer.playbackParameters.withSpeed(playbackSpeed)
                            }
                        )
                    }
                }
            }
        }

        // Error overlay
        if (playbackException != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Rounded.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                    Text("Oynatma hatası", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(playbackException?.message ?: "Bilinmeyen hata", color = Color.White.copy(alpha = 0.7f))
                    FilledTonalButton(
                        onClick = {
                            playbackException = null
                            exoPlayer.prepare()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFFF0000), contentColor = Color.White)
                    ) {
                        Text("Tekrar Dene", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuButton(
    title: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { expanded = !expanded },
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                borderColor = Color.White.copy(alpha = 0.5f)
            ),
            border = androidx.compose.ui.graphics.drawscope.Stroke(1.dp, Color.White.copy(alpha = 0.5f))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = Color.White, fontSize = 12.sp)
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        if (expanded) {
            Card(
                modifier = Modifier
                    .width(100.dp)
                    .padding(bottom = 56.dp),
                elevation = CardDefaults.cardElevation(8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    options.forEach { opt ->
                        TextButton(
                            onClick = {
                                onSelected(opt)
                                expanded = false
                            },
                            modifier = Modifier.fillMaxWidth().padding(12.dp, 8.dp),
                            contentPadding = androidx.compose.ui.unit.PaddingValues(0.dp)
                        ) {
                            Text(opt, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.Start))
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%02d:%02d", minutes, seconds)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AndroidView(
    factory: (android.content.Context) -> android.view.View,
    modifier: Modifier = Modifier
) = androidx.compose.ui.viewinterop.AndroidView(
    factory = factory,
    modifier = modifier
)