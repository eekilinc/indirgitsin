package com.indirgitsin.app

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.indirgitsin.app.data.SettingsStore
import com.indirgitsin.app.data.history.AppDatabase
import com.indirgitsin.app.data.lang.t
import com.indirgitsin.app.data.lang.tr
import com.indirgitsin.app.ui.navigation.AppNavHost
import com.indirgitsin.app.ui.navigation.Screen
import com.indirgitsin.app.ui.theme.IndirGitsinTheme
import com.indirgitsin.app.util.UpdateChecker
import com.indirgitsin.app.util.YoutubeLinkHelper
import kotlinx.coroutines.launch
import java.util.Locale

data class BottomItem(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

class MainActivity : ComponentActivity() {

    private val vm: HomeViewModel by viewModels()

    private val requestNotifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val requestStoragePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        // Android 13+ bildirim izni - DownloadCompleteReceiver için
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val db = AppDatabase.get(this)
        vm.historyDao = db.historyDao()

        handleIntent(intent)
        checkClipboard()

        setContent {
            val context = LocalContext.current
            val themePref by SettingsStore.themeFlow(context).collectAsState(initial = "dark")
            val appColor by SettingsStore.appColorFlow(context).collectAsState(initial = "red")
            val lang by SettingsStore.languageFlow(context).collectAsState(initial = "tr")
            val isDark = when (themePref) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
            androidx.compose.runtime.CompositionLocalProvider(com.indirgitsin.app.data.lang.LocalAppLanguage provides lang) {
            IndirGitsinTheme(darkTheme = isDark, appColor = appColor) {
                val bottomItems = listOf(
                    BottomItem(Screen.Home.route, t("home"), Icons.Rounded.Home, Icons.Rounded.Home),
                    BottomItem(Screen.History.route, t("library"), Icons.Rounded.VideoLibrary, Icons.Rounded.VideoLibrary),
                    BottomItem(Screen.Downloads.route, t("downloads"), Icons.Rounded.Download, Icons.Rounded.DownloadDone),
                    BottomItem(Screen.Settings.route, t("settings"), Icons.Rounded.Settings, Icons.Rounded.Settings)
                )
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route
                var updateInfo by remember { mutableStateOf<com.indirgitsin.app.util.UpdateChecker.UpdateInfo?>(null) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2000)
                    updateInfo = com.indirgitsin.app.util.UpdateChecker.check(this@MainActivity)
                }
                if (updateInfo != null) {
                    AlertDialog(
                        onDismissRequest = { updateInfo = null },
                        title = { Text(t("update_check_title", updateInfo!!.latestTag)) },
                        text = { Text(updateInfo!!.body.take(300).ifBlank { t("update_check_body") }) },
                        confirmButton = {
                            TextButton(onClick = {
                                com.indirgitsin.app.util.UpdateChecker.openUpdatePage(this@MainActivity, updateInfo!!)
                                updateInfo = null
                            }) { Text(t("update_btn")) }
                        },
                        dismissButton = { TextButton(onClick = { updateInfo = null }) { Text(t("later")) } }
                    )
                }

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
                                    Toast.makeText(this@MainActivity, tr(lang, "download_started", option.label), Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(this@MainActivity, tr(lang, "download_error", e.message ?: ""), Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }
                }

                // Intent ile gelen link varsa otomatik getir
                LaunchedEffect(Unit) {
                    vm.pendingUrl.collect { url ->
                        if (url != null) {
                            vm.consumePendingUrl()
                            vm.onInputChange(url)
                            vm.fetch(url, this@MainActivity)
                            // Home'a git
                            try { navController.navigate(Screen.Home.route) } catch (_: Exception) {}
                        }
                    }
                }
            } // CompositionLocalProvider
            } // IndirGitsinTheme
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
