package com.erumi.nativeapp.shared.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.erumi.nativeapp.shared.ui.components.MediaBottomNavBar
import com.erumi.nativeapp.ui.theme.ErumiBackground

@Composable
fun BaseNavGraph(
    bottomNavItems: List<BaseScreen>,
    navController: NavHostController = rememberNavController(),
    hideBottomBarRoutes: List<String> = emptyList(),
    content: @Composable (PaddingValues, NavHostController, String?) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = hideBottomBarRoutes.none { currentRoute?.startsWith(it) == true }

    Scaffold(
        containerColor = ErumiBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            MediaBottomNavBar(
                items = bottomNavItems,
                currentRoute = currentRoute,
                visible = showBottomBar,
                onNavigate = { screen ->
                    val startDestination = navController.graph.findStartDestination().id
                    // Always navigate fresh to the bottom nav destination:
                    // - popUpTo start destination inclusive to clear back stack
                    // - restoreState = false to avoid restoring scroll position/state
                    // - launchSingleTop = true to avoid duplicate instances
                    navController.navigate(screen.route) {
                        popUpTo(startDestination) {
                            inclusive = true
                            saveState = false
                        }
                        launchSingleTop = true
                        restoreState = false
                    }
                }
            )
        }
    ) { innerPadding ->
        content(innerPadding, navController, currentRoute)
    }
}
