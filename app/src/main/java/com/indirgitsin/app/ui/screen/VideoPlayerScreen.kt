package com.indirgitsin.app.ui.screen

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.indirgitsin.app.ui.theme.IndirGitsinTheme

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(videoUri: Uri) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
        }
    }

    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                isPlaying = state == Player.STATE_READY && exoPlayer.playWhenReady
            }

            override fun onIsPlayingChanged(newIsPlaying: Boolean) {
                isPlaying = newIsPlaying
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = error.message ?: "Oynatma Hatası"
            }
        }

        exoPlayer.addListener(listener)

        val updater = object : Runnable {
            override fun run() {
                currentPosition = exoPlayer.currentPosition
                bufferedPosition = exoPlayer.bufferedPosition
                duration = exoPlayer.duration.coerceAtLeast(0L)
                if (isPlaying || playbackState == Player.STATE_BUFFERING) {
                    exoPlayer.duration
                    // Trigger recomposition more frequently during playback
                    // postDelayed(this, 100)
                }
                exoPlayer.applicationLooper.post(this)
            }
        }
        exoPlayer.applicationLooper.post(updater)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            exoPlayer.applicationLooper.removeCallbacks(updater)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showControls = !showControls },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false // Custom controls
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Custom controls overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top controls (title, etc.)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { /* Handle back */ }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Geri", tint = Color.White)
                    }
                    // Video Title (if needed)
                    Text(
                        videoUri.lastPathSegment ?: "Video Oynatıcı",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = { /* Fullscreen toggle */ }) {
                        Icon(Icons.Rounded.Fullscreen, contentDescription = "Tam Ekran", tint = Color.White)
                    }
                }

                // Progress bar, current time, duration
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Slider(
                        value = if (duration == 0L) 0f else currentPosition.toFloat() / duration.toFloat(),
                        onValueChange = { newPositionFraction ->
                            exoPlayer.seekTo((duration * newPositionFraction).toLong())
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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
                        IconButton(onClick = { exoPlayer.seekTo((currentPosition - 10_000).coerceAtLeast(0L)) }) {
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
                            exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                        }
                    )
                }
            }
        }

        // Error overlay
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .clickable { errorMessage = null }, // Dismiss error on click
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Rounded.Error, contentDescription = "Hata", tint = Color.Red, modifier = Modifier.size(64.dp))
                    Text(
                        errorMessage ?: "Bilinmeyen bir hata oluştu.",
                        color = Color.White,
                        fontSize = 18.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    OutlinedButton(
                        onClick = { exoPlayer.prepare(); errorMessage = null },
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Yeniden Dene", modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Yeniden Dene")
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuButton(title: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .wrapContentSize(Alignment.TopEnd)
    ) {
        TextButton(onClick = { expanded = true }) {
            Text(title, color = Color.White)
            Icon(Icons.Rounded.ArrowDropDown, null, tint = Color.White)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            options.forEach { option ->
                DropdownMenuItem(onClick = {
                    onSelected(option)
                    expanded = false
                }, text = { Text(option, color = MaterialTheme.colorScheme.onSurfaceVariant) })
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Preview(showBackground = true)
@Composable
private fun VideoPlayerScreenPreview() {
    IndirGitsinTheme {
        VideoPlayerScreen(videoUri = Uri.parse("android.resource://com.indirgitsin.app/raw/sample_video"))
    }
}