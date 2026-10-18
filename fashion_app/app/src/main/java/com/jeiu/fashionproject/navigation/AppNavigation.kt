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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.jeiu.fashionproject.ui.screens.*
import com.jeiu.fashionproject.ui.theme.Camel
import com.jeiu.fashionproject.ui.viewmodel.BodyAnalysisUiState
import com.jeiu.fashionproject.ui.viewmodel.BodyAnalysisViewModel
import com.jeiu.fashionproject.ui.viewmodel.ShopViewModel
import com.jeiu.fashionproject.ui.viewmodel.UserPreferencesViewModel
import com.jeiu.fashionproject.ui.viewmodel.WeatherViewModel
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
    val context = LocalContext.current
    val weatherViewModel: WeatherViewModel = viewModel()
    val bodyAnalysisViewModel: BodyAnalysisViewModel = viewModel()
    val bodyAnalysisState by bodyAnalysisViewModel.state.collectAsState()
    val analyzedBodyTypeKo = (bodyAnalysisState as? BodyAnalysisUiState.Success)?.result?.bodyTypeKo
    val shopViewModel: ShopViewModel = viewModel()
    val userPreferencesViewModel: UserPreferencesViewModel = viewModel()
    val gender by userPreferencesViewModel.gender.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { userPreferencesViewModel.loadIfNeeded(context) }
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
                    onOutfitRecommendClick = { navController.navigate(Screen.OutfitRecommend.route()) },
                    onForecastClick = { navController.navigate(Screen.OutfitRecommend.route(tab = 1)) },
                    onProductClick = { navController.navigate(Screen.ProductDetail.route) },
                    onAddClothing = { navController.navigate(Screen.AddClothing.route()) },
                    onShopClick = { preset ->
                        // 홈에서 보던 목록을 쇼핑 탭에서 이어서 전체로 보여준다 (쇼핑 탭이 성별을 앞에 붙임)
                        preset?.let { shopViewModel.setPresetQuery(it) }
                        navController.navigate(Screen.Shop.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    weatherViewModel = weatherViewModel,
                    gender = gender
                )
            }
            composable(Screen.Wardrobe.route) {
                WardrobeScreen(
                    onOutfitRecommend = { scope -> navController.navigate(Screen.OutfitRecommend.route(scope = scope.name)) },
                    onProductDetail = { navController.navigate(Screen.ProductDetail.route) },
                    onShop = { navController.navigate(Screen.Shop.route) },
                    onAddClothing = { navController.navigate(Screen.AddClothing.route()) },
                    onEditClothing = { id -> navController.navigate(Screen.AddClothing.route(id)) },
                    onVirtualFitting = { navController.navigate(Screen.VirtualFitting.route) },
                    onWearLog = { navController.navigate(Screen.WearLog.route()) },
                    onWearCalendar = { navController.navigate(Screen.WearCalendar.route) },
                    onMissingItem = { navController.navigate(Screen.MissingItem.route) },
                    onMusinsaCloset = { navController.navigate(Screen.MusinsaCloset.route) }
                )
            }
            composable(Screen.MusinsaCloset.route) {
                MusinsaClosetScreen(
                    onBack = { navController.popBackStack() },
                    gender = gender,
                    weatherViewModel = weatherViewModel,
                    // AI 체형 분석에서 찍은 전신 사진이 있으면 가상피팅 기본 사진으로 쓴다
                    bodyPhotoUri = (bodyAnalysisState as? BodyAnalysisUiState.Success)?.imageUri
                )
            }
            composable(
                Screen.WearLog.route,
                arguments = listOf(navArgument("date") { type = NavType.StringType; nullable = true })
            ) { entry ->
                WearLogScreen(
                    initialDate = entry.arguments?.getString("date"),
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(Screen.WearCalendar.route) {
                WearCalendarScreen(
                    onBack = { navController.popBackStack() },
                    onRecord = { date -> navController.navigate(Screen.WearLog.route(date)) }
                )
            }
            composable(
                Screen.AddClothing.route,
                arguments = listOf(navArgument("edit") { type = NavType.StringType; nullable = true })
            ) { entry ->
                AddClothingScreen(
                    editItemId = entry.arguments?.getString("edit"),
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() }
                )
            }
            composable(Screen.MissingItem.route) {
                MissingItemScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.StylePreference.route) {
                StylePreferenceScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.WeatherOutfit.route) {
                WeatherOutfitScreen(onBack = { navController.popBackStack() },
                    onProductClick = { navController.navigate(Screen.ProductDetail.route) },
                    weatherViewModel = weatherViewModel,
                    gender = gender)
            }
            composable(Screen.VirtualFitting.route) {
                VirtualFittingScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Screen.OutfitRecommend.route,
                arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("scope") { type = NavType.StringType; defaultValue = "OWNED" })
            ) { entry ->
                OutfitRecommendScreen(
                    initialTab = entry.arguments?.getInt("tab") ?: 0,
                    initialScope = com.jeiu.fashionproject.recommend.ClothingScope.entries.firstOrNull {
                        it.name == entry.arguments?.getString("scope")
                    } ?: com.jeiu.fashionproject.recommend.ClothingScope.OWNED,
                    onShop = { navController.navigate(Screen.Shop.route) },
                    onBack = { navController.popBackStack() },
                    onAddClothing = { navController.navigate(Screen.AddClothing.route()) },
                    weatherViewModel = weatherViewModel
                )
            }
            composable(Screen.BodyAnalysis.route) {
                BodyAnalysisScreen(
                    onBack = { navController.popBackStack() },
                    onStyleSuggestion = { navController.navigate(Screen.StyleSuggestion.route) },
                    onCameraClick = { navController.navigate(Screen.Camera.route) },
                    viewModel = bodyAnalysisViewModel,
                    onHistory = { navController.navigate(Screen.BodyHistory.route) }
                )
            }
            composable(Screen.Camera.route) {
                val context = LocalContext.current
                CameraPreviewScreen(
                    onPhotoCaptured = { uri ->
                        bodyAnalysisViewModel.analyzeImage(context, uri)
                        navController.popBackStack()
                    },
                    onDismiss = { navController.popBackStack() }
                )
            }
            composable(Screen.Shop.route) {
                ShopScreen(
                    onProductClick = { navController.navigate(Screen.ProductDetail.route) },
                    viewModel = shopViewModel,
                    gender = gender
                )
            }
            composable(Screen.ProductDetail.route) {
                ProductDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.MyPage.route) {
                MyPageScreen(
                    bodyTypeKo = analyzedBodyTypeKo,
                    gender = gender,
                    onGenderSelected = { userPreferencesViewModel.setGender(context, it) },
                    onStyleAnalysis = { navController.navigate(Screen.StyleAnalysis.route) },
                    onStyleSuggestion = { navController.navigate(Screen.StyleSuggestion.route) },
                    onBodyAnalysis = { navController.navigate(Screen.BodyAnalysis.route) },
                    onStylePreference = { navController.navigate(Screen.StylePreference.route) },
                    onVirtualFitting = { navController.navigate(Screen.VirtualFitting.route) },
                    onBodyHistory = { navController.navigate(Screen.BodyHistory.route) },
                    onLogout = {
                        navController.navigate(Screen.Onboarding.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.BodyHistory.route) {
                BodyHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onMeasure = {
                        navController.navigate(Screen.BodyAnalysis.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
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
