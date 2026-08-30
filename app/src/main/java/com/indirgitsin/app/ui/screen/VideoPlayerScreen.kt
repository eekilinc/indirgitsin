package com.indirgitsin.app.ui.screen

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.indirgitsin.app.data.lang.t
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(videoUri: Uri, title: String = "", navController: NavController) {
    val context = LocalContext.current
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    val exoPlayer = remember(videoUri) {
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
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var resizeModeIndex by remember { mutableIntStateOf(0) } // 0: FIT, 1: ZOOM, 2: FILL

    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to t("fit_screen"),
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to t("zoom_screen"),
        AspectRatioFrameLayout.RESIZE_MODE_FILL to t("fill_screen")
    )

    // Properly release player on back press or when leaving screen
    BackHandler(enabled = true) {
        navController.popBackStack()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exoPlayer.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    // Auto-hide controls after 4 seconds if playing
    LaunchedEffect(showControls, isPlaying, isDragging) {
        if (showControls && isPlaying && !isDragging && !showSpeedMenu) {
            delay(4000)
            showControls = false
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
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        val screenWidth = size.width
                        if (offset.x < screenWidth * 0.35f) {
                            // Left side: Rewind 10s
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0))
                            showControls = true
                        } else if (offset.x > screenWidth * 0.65f) {
                            // Right side: Fast forward 10s
                            exoPlayer.seekTo((exoPlayer.currentPosition + 10_000).coerceAtMost(durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE))
                            showControls = true
                        } else {
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = resizeModes[resizeModeIndex].first
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    playerViewRef = this
                }
            },
            update = { view ->
                view.resizeMode = resizeModes[resizeModeIndex].first
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay Kontroller
        AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.40f))) {

                // ÜST BAR: Geri butonu, Başlık, Boyut/Ölçek, Hız Seçici
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Geri", tint = Color.White)
                    }

                    Text(
                        text = title.ifBlank { "Video Oynatıcı" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                    )

                    // En boy oranı butonu (Fit / Zoom / Fill)
                    IconButton(onClick = {
                        resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
                        playerViewRef?.resizeMode = resizeModes[resizeModeIndex].first
                    }) {
                        Icon(Icons.Rounded.AspectRatio, contentDescription = resizeModes[resizeModeIndex].second, tint = Color.White)
                    }

                    // Hız Seçici Menüsü
                    Box {
                        IconButton(onClick = { showSpeedMenu = true }) {
                            Icon(Icons.Rounded.Speed, contentDescription = t("playback_speed"), tint = Color.White)
                        }

                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false }
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${speed}x",
                                            fontWeight = if (playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        playbackSpeed = speed
                                        exoPlayer.setPlaybackSpeed(speed)
                                        showSpeedMenu = false
                                    },
                                    trailingIcon = {
                                        if (playbackSpeed == speed) {
                                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // ORTA KONTROLLER: 10sn geri, Play/Pause, 10sn ileri
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    Surface(
                        onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0)) },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.20f),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Replay10, contentDescription = "-10s", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }

                    Surface(
                        onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                                tint = Color.Black,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10_000).coerceAtMost(durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)) },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.20f),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Forward10, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                }

                // ALT KONTROL BARI: Zaman göstergesi + Slider + Hızlı butonlar
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.60f))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            fmt(if (isDragging) (dragPos * durationMs).toLong() else positionMs),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            fmt(durationMs),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Slider(
                        value = progress,
                        onValueChange = { isDragging = true; dragPos = it },
                        onValueChangeFinished = {
                            exoPlayer.seekTo((dragPos * durationMs).toLong())
                            isDragging = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.30f)
                        ),
                        modifier = Modifier.fillMaxWidth().height(28.dp)
                    )
                }
            }
        }
    }
}
