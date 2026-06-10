package com.jeiu.fashionproject.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jeiu.fashionproject.ui.screens.*
import com.jeiu.fashionproject.ui.theme.Camel
import com.jeiu.fashionproject.ui.theme.CharcoalBlack
import com.jeiu.fashionproject.ui.theme.LightGray
import com.jeiu.fashionproject.ui.theme.MediumGray
import com.jeiu.fashionproject.ui.theme.White

private data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "홈", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(Screen.Wardrobe, "옷장", Icons.Filled.Checkroom, Icons.Outlined.Checkroom),
    BottomNavItem(Screen.BodyAnalysis, "AI분석", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    BottomNavItem(Screen.Shop, "쇼핑", Icons.Filled.ShoppingBag, Icons.Outlined.ShoppingBag),
    BottomNavItem(Screen.MyPage, "마이", Icons.Filled.Person, Icons.Outlined.Person),
)

private val bottomNavRoutes = setOf(
    Screen.Home.route,
    Screen.Wardrobe.route,
    Screen.BodyAnalysis.route,
    Screen.Shop.route,
    Screen.MyPage.route,
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = White) {
                    val currentDestination = navBackStackEntry?.destination
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Camel,
                                selectedTextColor = Camel,
                                unselectedIconColor = MediumGray,
                                unselectedTextColor = MediumGray,
                                indicatorColor = White
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Onboarding.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(onStart = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                })
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    onWeatherOutfitClick = { navController.navigate(Screen.WeatherOutfit.route) },
                    onProductClick = { navController.navigate(Screen.ProductDetail.route) }
                )
            }
            composable(Screen.Wardrobe.route) {
                WardrobeScreen(onProductClick = { navController.navigate(Screen.ProductDetail.route) })
            }
            composable(Screen.WeatherOutfit.route) {
                WeatherOutfitScreen(
                    onBack = { navController.popBackStack() },
                    onProductClick = { navController.navigate(Screen.ProductDetail.route) }
                )
            }
            composable(Screen.BodyAnalysis.route) {
                BodyAnalysisScreen(
                    onBack = { navController.popBackStack() },
                    onStyleSuggestion = { navController.navigate(Screen.StyleSuggestion.route) }
                )
            }
            composable(Screen.Shop.route) {
                ShopScreen(onProductClick = { navController.navigate(Screen.ProductDetail.route) })
            }
            composable(Screen.ProductDetail.route) {
                ProductDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.MyPage.route) {
                MyPageScreen(
                    onStyleAnalysis = { navController.navigate(Screen.StyleAnalysis.route) },
                    onStyleSuggestion = { navController.navigate(Screen.StyleSuggestion.route) },
                    onBodyAnalysis = { navController.navigate(Screen.BodyAnalysis.route) }
                )
            }
            composable(Screen.StyleAnalysis.route) {
                StyleAnalysisScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.StyleSuggestion.route) {
                StyleSuggestionScreen(
                    onBack = { navController.popBackStack() },
                    onProductClick = { navController.navigate(Screen.ProductDetail.route) }
                )
            }
        }
    }
}
