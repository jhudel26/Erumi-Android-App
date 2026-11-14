package com.erumi.nativeapp.shared.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erumi.nativeapp.shared.navigation.BaseScreen
import com.erumi.nativeapp.ui.theme.BgCard
import com.erumi.nativeapp.ui.theme.ErumiPrimary
import com.erumi.nativeapp.ui.theme.ErumiSecondaryText
import com.erumi.nativeapp.ui.theme.ErumiSurface

@Composable
fun MediaBottomNavBar(
    items: List<BaseScreen>,
    currentRoute: String?,
    onNavigate: (BaseScreen) -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        NavigationBar(
            modifier = modifier,
            containerColor = ErumiSurface,
            tonalElevation = 8.dp
        ) {
            items.forEach { screen ->
                val selected = currentRoute == screen.route
                NavigationBarItem(
                    icon = {
                        screen.icon?.let {
                            Icon(
                                imageVector = it,
                                contentDescription = screen.title
                            )
                        }
                    },
                    label = { Text(text = screen.title) },
                    selected = selected,
                    onClick = { onNavigate(screen) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ErumiPrimary,
                        selectedTextColor = ErumiPrimary,
                        unselectedIconColor = ErumiSecondaryText,
                        unselectedTextColor = ErumiSecondaryText,
                        indicatorColor = BgCard
                    )
                )
            }
        }
    }
}
