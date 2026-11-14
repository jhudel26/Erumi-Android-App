package com.erumi.nativeapp.shared.navigation

import androidx.compose.ui.graphics.vector.ImageVector

abstract class BaseScreen(
    open val route: String,
    open val title: String,
    open val icon: ImageVector? = null
)
