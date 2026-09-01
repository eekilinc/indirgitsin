package com.indirgitsin.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.indirgitsin.app.HomeViewModel
import com.indirgitsin.app.data.history.HistoryDao
import com.indirgitsin.app.data.model.StreamOption
import com.indirgitsin.app.data.model.VideoInfo
import com.indirgitsin.app.ui.screen.DownloadsScreen
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
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                inputUrl = inputUrl,
                onInputChange = homeViewModel::onInputChange,
                uiState = uiState,
                onFetch = { url -> homeViewModel.fetch(url, context) },
                onDownload = onDownload,
                onPaste = {},
                historyDao = historyDao,
                onHistoryClick = { url ->
                    homeViewModel.onInputChange(url)
                    homeViewModel.fetch(url, context)
                },
                onClear = {
                    homeViewModel.reset()
                    homeViewModel.onInputChange("")
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
            DownloadsScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
        // Preserve saved navigation stacks from versions that hosted playback in MainActivity.
        composable("player/{uri}/{title}", arguments = listOf(
            navArgument("uri") { type = NavType.StringType },
            navArgument("title") { type = NavType.StringType }
        )) { entry ->
            LaunchedEffect(entry) {
                val uri = entry.arguments?.getString("uri")
                val title = entry.arguments?.getString("title").orEmpty()
                navController.popBackStack()
                if (uri != null) context.startActivity(com.indirgitsin.app.PlayerActivity.intent(context, android.net.Uri.parse(uri), title))
            }
        }
    }
}
