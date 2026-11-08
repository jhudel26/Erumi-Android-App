package com.erumi.nativeapp.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Dark Cinema Theme Colors (Matching Erumi Streaming Android)
val BgMain = Color(0xFF050A0E)
val BgSurface = Color(0xFF0A1218)
val BgSurfaceElevated = Color(0xFF101D27)
val BgCard = Color(0xFF162430)

val TealPrimary = Color(0xFF14B8A6)
val TealHover = Color(0xFF2DD4BF)
val TealLight = Color(0xFF5EEAD4)
val TealDark = Color(0xFF0F766E)

val TextPrimary = Color(0xFFF3F9FC)
val TextSecondary = Color(0xFF94A9B8)
val TextDim = Color(0xFF5A7082)

val BorderSubtle = Color(0xFF1F2D3A)
val SuccessGreen = Color(0xFF22C55E)
val WarningAmber = Color(0xFFF59E0B)
val DangerRed = Color(0xFFEF4444)

// Backwards-compatible aliases (ensures zero broken references)
val ErumiBackground = BgMain
val ErumiSurface = BgSurface
val ErumiElevatedSurface = BgSurfaceElevated
val ErumiCardBg = BgCard
val ErumiPrimary = TealPrimary
val ErumiSecondary = TealLight
val ErumiPrimaryText = TextPrimary
val ErumiSecondaryText = TextSecondary
val ErumiMuted = TextDim
val ErumiBorder = BorderSubtle
val ErumiError = DangerRed
val ErumiGold = WarningAmber
