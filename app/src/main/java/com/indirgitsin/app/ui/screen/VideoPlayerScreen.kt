package com.indirgitsin.app.ui.screen

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.activity.compose.BackHandler
import androidx.navigation.NavController
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(videoUri: Uri, navController: NavController) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableFloatStateOf(0f) }

    // Properly release player on back press or when leaving screen
    BackHandler(enabled = true) {
        exoPlayer.release()
        navController.popBackStack()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Pozisyonu periyodik güncelle
    LaunchedEffect(exoPlayer, isPlaying, isDragging) {
        while (true) {
            if (!isDragging) {
                positionMs = exoPlayer.currentPosition
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
            }
            delay(300)
        }
    }

    fun fmt(ms: Long): String {
        if (ms <= 0) return "00:00"
        val s = ms / 1000
        val m = s / 60
        val sec = s % 60
        return if (m >= 60) String.format("%d:%02d:%02d", m / 60, m % 60, sec) else String.format("%02d:%02d", m, sec)
    }

    val progress = when {
        isDragging -> dragPos
        durationMs > 0 -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        else -> 0f
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable { showControls = !showControls },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Alt kontrol barı + orta oynat/durdur
        AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f))) {
                // Orta play/pause + 10sn geri/ileri
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Surface(onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0)) }, shape = CircleShape, color = Color.White.copy(alpha = 0.18f), modifier = Modifier.size(48.dp)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Replay10, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                    }
                    Surface(onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }, shape = CircleShape, color = Color.White, modifier = Modifier.size(64.dp)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(36.dp))
                        }
                    }
                    Surface(onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10_000).coerceAtMost(durationMs.takeIf { it>0 } ?: Long.MAX_VALUE)) }, shape = CircleShape, color = Color.White.copy(alpha = 0.18f), modifier = Modifier.size(48.dp)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Forward10, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                    }
                }
                // Alt bar: zaman + slider
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt(if (isDragging) (dragPos * durationMs).toLong() else positionMs), color = Color.White, fontSize = 12.sp, maxLines = 1)
                        Text(fmt(durationMs), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
                    }
                    Slider(
                        value = progress,
                        onValueChange = { isDragging = true; dragPos = it },
                        onValueChangeFinished = { exoPlayer.seekTo((dragPos * durationMs).toLong()); isDragging = false },
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth().height(28.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                            Text(if (isPlaying) "Durdur" else "Devam", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
