package com.jeiu.fashionproject.navigation

import androidx.navigation.NavController

/** Keep this editor's owner-keyed saveable draft when leaving and reopening it. */
internal fun NavController.openBodyAvatar() {
    navigate(Screen.BodyAnalysisAvatar.route) {
        launchSingleTop = true
        restoreState = true
    }
}

internal fun NavController.closeBodyAvatar() {
    popBackStack(Screen.BodyAnalysisAvatar.route, inclusive = true, saveState = true)
}
