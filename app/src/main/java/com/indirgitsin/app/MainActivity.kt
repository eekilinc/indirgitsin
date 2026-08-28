package com.indirgitsin.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.indirgitsin.app.data.history.AppDatabase
import com.indirgitsin.app.ui.navigation.AppNavHost
import com.indirgitsin.app.ui.navigation.Screen
import com.indirgitsin.app.ui.theme.IndirGitsinTheme
import com.indirgitsin.app.util.YoutubeLinkHelper
import kotlinx.coroutines.launch

data class BottomItem(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

class MainActivity : ComponentActivity() {

    private val vm: HomeViewModel by viewModels()
    private val bottomItems = listOf(
        BottomItem(Screen.Home.route, "İndir", Icons.Rounded.Download, Icons.Rounded.Download),
        BottomItem(Screen.History.route, "Geçmiş", Icons.Rounded.History, Icons.Rounded.History),
        BottomItem(Screen.Settings.route, "Ayarlar", Icons.Rounded.Settings, Icons.Rounded.Settings)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.get(this)
        vm.historyDao = db.historyDao()

        handleIntent(intent)
        checkClipboard()

        setContent {
            IndirGitsinTheme(darkTheme = true) {
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route

                Scaffold(
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                            bottomItems.forEach { item ->
                                val selected = currentRoute == item.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(item.route) {
                                            popUpTo(Screen.Home.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = item.label) },
                                    label = { Text(item.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    )
                                )
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        AppNavHost(
                            navController = navController,
                            homeViewModel = vm,
                            historyDao = db.historyDao(),
                            onDownload = { video, option ->
                                try {
                                    // Geçmişte son kaliteyi güncelle
                                    lifecycleScope.launch {
                                        try {
                                            db.historyDao().insert(
                                                com.indirgitsin.app.data.history.HistoryEntity(
                                                    videoId = video.id,
                                                    title = video.title,
                                                    author = video.author,
                                                    thumbnailUrl = video.thumbnailUrl,
                                                    durationSeconds = video.durationSeconds,
                                                    viewCount = video.viewCount,
                                                    url = video.url,
                                                    lastQuality = option.label
                                                )
                                            )
                                        } catch (_: Exception) {}
                                    }
                                    com.indirgitsin.app.data.downloader.FileDownloader.enqueue(this@MainActivity, video, option)
                                    Toast.makeText(this@MainActivity, "İndirme başlatıldı: ${option.label}", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(this@MainActivity, "İndirme hatası: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }
                }

                // Intent ile gelen link varsa otomatik getir
                LaunchedEffect(Unit) {
                    vm.pendingUrl.collect { url ->
                        if (url != null) {
                            vm.onInputChange(url)
                            vm.fetch(url)
                            // Home'a git
                            try { navController.navigate(Screen.Home.route) } catch (_: Exception) {}
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
                    url = YoutubeLinkHelper.findYoutubeUrlInText(text)
                }
            }
            Intent.ACTION_VIEW -> url = intent.dataString
        }
        if (url != null && YoutubeLinkHelper.isValidYoutubeUrl(url)) {
            lifecycleScope.launch { vm.setPendingUrl(url) }
        }
    }

    private fun checkClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = cm.primaryClip?.getItemAt(0)?.text?.toString()
        val found = YoutubeLinkHelper.findYoutubeUrlInText(clip)
        if (found != null) vm.setClipboardSuggestion(found)
    }
}
