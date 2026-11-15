package com.jeiu.fashionproject.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.jeiu.fashionproject.data.AuthStore

/** Navigation never consumes the code; the login screen owns exactly one exchange. */
@Composable
internal fun LoginRedirectNavigation(navController: NavHostController) {
    val callback by AuthStore.redirect.collectAsState()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    LaunchedEffect(callback, route) {
        if (callback != null && route != null && route != Screen.Login.route) {
            navController.navigate(Screen.Login.route) { launchSingleTop = true }
        }
    }
}
