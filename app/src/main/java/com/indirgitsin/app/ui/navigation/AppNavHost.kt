package com.indirgitsin.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.indirgitsin.app.HomeViewModel
import com.indirgitsin.app.data.history.HistoryDao
import com.indirgitsin.app.data.model.StreamOption
import com.indirgitsin.app.data.model.VideoInfo
import com.indirgitsin.app.ui.screen.HistoryScreen
import com.indirgitsin.app.ui.screen.HomeScreen
import com.indirgitsin.app.ui.screen.SettingsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object History : Screen("history")
    object Downloads : Screen("downloads")
    object Settings : Screen("settings")
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    historyDao: HistoryDao,
    onDownload: (VideoInfo, StreamOption) -> Unit
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val inputUrl by homeViewModel.inputUrl.collectAsState()
    // Context'i composable scope'ta BİR KEZ alıp lambda'lara capture et
    // (LocalContext.current çağrısı non-@Composable lambda içinde yapılamaz)
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                inputUrl = inputUrl,
                onInputChange = homeViewModel::onInputChange,
                uiState = uiState,
                onFetch = { url -> homeViewModel.fetch(url, context) },
                onDownload = onDownload,
                onPaste = { /* handled in MainActivity */ },
                historyDao = historyDao,
                onHistoryClick = { url ->
                    homeViewModel.onInputChange(url)
                    homeViewModel.fetch(url, context)
                }
            )
        }
        composable(Screen.History.route) {
            HistoryScreen(
                historyDao = historyDao,
                onVideoClick = { entity ->
                    val url = entity.url
                    navController.navigate(Screen.Home.route)
                    homeViewModel.onInputChange(url)
                    homeViewModel.fetch(url, context)
                }
            )
        }
        composable(Screen.Downloads.route) {
            com.indirgitsin.app.ui.screen.DownloadsScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}