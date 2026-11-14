package com.erumi.nativeapp.features.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness1
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.erumi.nativeapp.domain.model.DownloadStatus
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiBorder
import com.erumi.nativeapp.ui.theme.ErumiElevatedSurface
import com.erumi.nativeapp.ui.theme.ErumiError
import com.erumi.nativeapp.ui.theme.ErumiPrimary
import com.erumi.nativeapp.ui.theme.ErumiPrimaryText
import com.erumi.nativeapp.ui.theme.ErumiSecondary
import com.erumi.nativeapp.ui.theme.ErumiSecondaryText
import kotlinx.coroutines.delay

// Aspect ratio modes: FIT → ZOOM (FILL) → FILL (STRETCH)
private val ASPECT_MODES = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    AspectRatioFrameLayout.RESIZE_MODE_FILL
)
private val ASPECT_LABELS = listOf("Fit", "Crop", "Stretch")

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onBackClick: () -> Unit,
    viewModel: PlayerViewModel
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsState()

    var isControlsVisible by remember { mutableStateOf(true) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showAspectDialog by remember { mutableStateOf(false) }

    // Smooth Slider Scrubbing state
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    // Double-tap seek feedback
    var doubleTapSide by remember { mutableStateOf<String?>(null) }
    
    // Volume / Brightness HUD
    var volumeLevel by remember { mutableFloatStateOf(0f) }
    var brightnessLevel by remember { mutableFloatStateOf(0.5f) }
    var showVolumeHud by remember { mutableStateOf(false) }
    var showBrightnessHud by remember { mutableStateOf(false) }
    
    // Aspect ratio cycling
    var aspectModeIndex by remember { mutableStateOf(0) }

    // Auto-play next countdown
    var autoPlayCountdown by remember { mutableStateOf<Int?>(null) }

    // Touch lock
    var isLocked by remember { mutableStateOf(false) }
    var showLockIndicator by remember { mutableStateOf(false) }

    // Auto-hide lock indicator after 2 seconds
    LaunchedEffect(showLockIndicator) {
        if (showLockIndicator) {
            delay(2000)
            showLockIndicator = false
        }
    }

    // HUD animation states
    val volumeScale by animateFloatAsState(
        targetValue = if (showVolumeHud) 1f else 0f,
        animationSpec = tween(200),
        label = "volumeScale"
    )
    val brightnessScale by animateFloatAsState(
        targetValue = if (showBrightnessHud) 1f else 0f,
        animationSpec = tween(200),
        label = "brightnessScale"
    )

    // Show auto-play countdown when viewModel triggers it
    LaunchedEffect(state.showAutoPlayCountdown) {
        if (state.showAutoPlayCountdown && autoPlayCountdown == null) {
            autoPlayCountdown = 5
        }
    }

    // Initialize volume & brightness level
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    LaunchedEffect(Unit) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        volumeLevel = if (max > 0) cur.toFloat() / max else 0f

        val curBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
        brightnessLevel = if (curBrightness in 0f..1f) curBrightness else 0.5f
    }

    // Auto-hide controls after 4 seconds of inactivity when not dragging
    LaunchedEffect(isControlsVisible, state.isPlaying, isDragging) {
        if (isControlsVisible && state.isPlaying && !isDragging) {
            delay(4000)
            isControlsVisible = false
        }
    }

    // Double-tap ripple auto-hide with proper fade-out animation
    val doubleTapAlpha = remember { Animatable(0f) }
    LaunchedEffect(doubleTapSide) {
        if (doubleTapSide != null) {
            doubleTapAlpha.snapTo(1f)
            doubleTapAlpha.animateTo(0f, animationSpec = tween(700))
            doubleTapSide = null
        }
    }

    // Volume HUD auto-hide
    LaunchedEffect(showVolumeHud) {
        if (showVolumeHud) {
            delay(1500)
            showVolumeHud = false
        }
    }

    // Brightness HUD auto-hide
    LaunchedEffect(showBrightnessHud) {
        if (showBrightnessHud) {
            delay(1500)
            showBrightnessHud = false
        }
    }

    // Auto-play countdown
    LaunchedEffect(autoPlayCountdown) {
        val count = autoPlayCountdown ?: return@LaunchedEffect
        if (count > 0) {
            delay(1000)
            autoPlayCountdown = count - 1
        } else {
            // Navigate to next episode
            val nextEp = state.currentEpisodeNumber + 1
            if (nextEp <= state.totalEpisodes) {
                autoPlayCountdown = null
                viewModel.playNextEpisode()
            } else {
                autoPlayCountdown = null
            }
        }
    }

    // Lifecycle observer: pause player when app goes to background / minimized
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.pause()
        }
    }

    // Set immersive fullscreen landscape
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.let { act ->
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            activity?.let { act ->
                act.requestedOrientation = originalOrientation
                val window = act.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                val lp = act.window.attributes
                lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                act.window.attributes = lp
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. AndroidX Media3 Video Player Surface with gesture detection
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.exoPlayer
                    useController = false // Custom Compose Controls
                    artworkDisplayMode = PlayerView.ARTWORK_DISPLAY_MODE_OFF
                    // Disable ExoPlayer's built-in buffering spinner - we render our own custom one
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setShowSubtitleButton(true)
                    setControllerAutoShow(false)
                    resizeMode = ASPECT_MODES[aspectModeIndex]
                    subtitleView?.apply {
                        setUserDefaultStyle()
                        setUserDefaultTextSize()
                        setStyle(
                            CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                android.graphics.Color.BLACK,
                                android.graphics.Typeface.DEFAULT_BOLD
                            )
                        )
                        setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
                        setVisibility(android.view.View.VISIBLE)
                        // Add bottom margin to move subtitles away from the bottom
                        setPadding(0, 0, 0, 50)
                    }
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                if (playerView.player != viewModel.exoPlayer) {
                    playerView.player = viewModel.exoPlayer
                }
                playerView.resizeMode = ASPECT_MODES[aspectModeIndex]
                // Ensure subtitle view is visible and properly configured when player updates
                playerView.subtitleView?.apply {
                    setVisibility(android.view.View.VISIBLE)
                    setPadding(0, 0, 0, 50)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked) {
                    detectTapGestures(
                        onTap = {
                            if (!isLocked) {
                                isControlsVisible = !isControlsVisible
                            } else {
                                // Show the lock indicator briefly when user taps on a locked screen
                                showLockIndicator = true
                            }
                        },
                        onDoubleTap = { offset ->
                            if (isLocked) return@detectTapGestures
                            val screenWidth = context.resources.displayMetrics.widthPixels
                            if (offset.x < screenWidth / 3) {
                                // Left side: seek backward 10s
                                viewModel.seekBackward()
                                doubleTapSide = "left"
                            } else if (offset.x > screenWidth * 2 / 3) {
                                // Right side: seek forward 10s
                                viewModel.seekForward()
                                doubleTapSide = "right"
                            } else {
                                // Center: toggle play/pause
                                viewModel.togglePlayPause()
                            }
                        }
                    )
                }
                .pointerInput(isLocked) {
                    if (isLocked) return@pointerInput
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            val screenWidth = context.resources.displayMetrics.widthPixels
                            if (change.position.x < screenWidth / 2) {
                                // Left side: brightness
                                val newBrightness = (brightnessLevel - dragAmount / 500f).coerceIn(0.01f, 1f)
                                brightnessLevel = newBrightness
                                showBrightnessHud = true
                                activity?.let { act ->
                                    val lp = act.window.attributes
                                    lp.screenBrightness = newBrightness
                                    act.window.attributes = lp
                                }
                            } else {
                                // Right side: volume
                                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val newLevel = (volumeLevel - dragAmount / 500f).coerceIn(0f, 1f)
                                volumeLevel = newLevel
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    (newLevel * max).toInt(),
                                    0
                                )
                                showVolumeHud = true
                            }
                        }
                    )
                }
        )

        // 2. Buffering Spinner
        if (state.isBuffering && state.error == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ErumiPrimary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Summoning your stream...",
                        style = MaterialTheme.typography.bodyMedium.copy(color = ErumiPrimaryText)
                    )
                }
            }
        }

        // 3. Error Overlay
        state.error?.let { err ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = ErumiError,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodyMedium.copy(color = ErumiPrimaryText),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { viewModel.retryPlayback() },
                            colors = ButtonDefaults.buttonColors(containerColor = ErumiPrimary)
                        ) {
                            Text(text = "Retry", color = ErumiBackground, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = onBackClick) {
                            Text(text = "Go Back", color = ErumiPrimaryText)
                        }
                    }
                }
            }
        }

        // 4. Double-tap Seek Ripple Feedback
        if (doubleTapSide != null) {
            val rippleOffset = when (doubleTapSide) {
                "left" -> -100
                "right" -> 100
                else -> 0
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
                    .graphicsLayer {
                        alpha = doubleTapAlpha.value
                    }
            ) {
                Icon(
                    imageVector = when (doubleTapSide) {
                        "left" -> Icons.Default.Replay10
                        "right" -> Icons.Default.Forward10
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = null,
                    tint = ErumiPrimary,
                    modifier = Modifier
                        .size(80.dp)
                        .align(Alignment.Center)
                        .offset(x = rippleOffset.dp)
                )
            }
        }

        // 5. Volume HUD (right side - volume drag is on right half of screen)
        if (showVolumeHud) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 32.dp)
                    .graphicsLayer {
                        scaleX = volumeScale
                        scaleY = volumeScale
                    }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        when {
                            volumeLevel > 0.66f -> Icons.AutoMirrored.Filled.VolumeUp
                            volumeLevel > 0.33f -> Icons.AutoMirrored.Filled.VolumeDown
                            else -> Icons.AutoMirrored.Filled.VolumeOff
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(volumeLevel)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White)
                        )
                    }
                    Text(
                        text = "${(volumeLevel * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // 6. Brightness HUD (left side - brightness drag is on left half of screen)
        if (showBrightnessHud) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 32.dp)
                    .graphicsLayer {
                        scaleX = brightnessScale
                        scaleY = brightnessScale
                    }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        when {
                            brightnessLevel > 0.66f -> Icons.Default.Brightness7
                            brightnessLevel > 0.33f -> Icons.Default.Brightness5
                            else -> Icons.Default.Brightness1
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(brightnessLevel)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White)
                        )
                    }
                    Text(
                        text = "${(brightnessLevel * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // 7. Auto-play Countdown
        if (autoPlayCountdown != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Next episode in",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${autoPlayCountdown}s",
                        color = ErumiPrimary,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(
                        onClick = { 
                            autoPlayCountdown = null
                            viewModel.cancelAutoPlay()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // 8. Skip Intro Button
        if (state.showSkipIntro && !state.isBuffering && state.error == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 76.dp, end = 24.dp)
            ) {
                Button(
                    onClick = { viewModel.skipIntro() },
                    colors = ButtonDefaults.buttonColors(containerColor = ErumiElevatedSurface.copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(8.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(ErumiPrimary, ErumiSecondary)))
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = ErumiPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Skip Intro",
                        style = MaterialTheme.typography.bodyMedium.copy(color = ErumiPrimaryText, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // 9. Cinema Overlay Controls
        AnimatedVisibility(
            visible = isControlsVisible && !isLocked && state.error == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBackClick) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ErumiPrimaryText)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(
                            modifier = Modifier.width(300.dp)
                        ) {
                            Text(
                                text = if (state.animeTitle.length > 70) "${state.animeTitle.take(70)}..." else state.animeTitle,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Episode ${state.currentEpisodeNumber}",
                                style = MaterialTheme.typography.labelSmall.copy(color = ErumiSecondary)
                            )
                        }
                    }

                    // Top Action Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Touch Lock Button
                        IconButton(onClick = {
                            isLocked = !isLocked
                            showLockIndicator = true
                        }) {
                            Icon(
                                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (isLocked) "Unlock" else "Lock",
                                tint = if (isLocked) ErumiPrimary else ErumiPrimaryText
                            )
                        }

                        // Sub/Dub Toggle
                        TextButton(
                            onClick = { viewModel.toggleAudio() },
                            colors = ButtonDefaults.textButtonColors(containerColor = ErumiElevatedSurface)
                        ) {
                            Text(
                                text = state.selectedAudio.uppercase(),
                                color = ErumiPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        // Subtitle Selector Button
                        IconButton(onClick = { showSubtitleDialog = true }) {
                            Icon(imageVector = Icons.Default.Subtitles, contentDescription = "Subtitles", tint = ErumiPrimaryText)
                        }

                        // Speed Button
                        TextButton(onClick = { showSpeedDialog = true }) {
                            Text(
                                text = "${state.playbackSpeed}x",
                                color = ErumiPrimaryText,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Aspect Ratio Button
                        TextButton(onClick = { showAspectDialog = true }) {
                            Text(
                                text = ASPECT_LABELS[aspectModeIndex],
                                color = ErumiPrimaryText,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Download Episode Button
                        IconButton(onClick = { viewModel.downloadCurrentEpisode() }) {
                            when (state.downloadStatus) {
                                DownloadStatus.COMPLETED -> {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Downloaded",
                                        tint = ErumiPrimary
                                    )
                                }
                                DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> {
                                    CircularProgressIndicator(
                                        color = ErumiPrimary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download Episode",
                                        tint = ErumiPrimaryText
                                    )
                                }
                            }
                        }
                    }
                }

                // Center Play / Pause & Seek Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Episode
                    IconButton(
                        onClick = { viewModel.playPrevEpisode() },
                        enabled = state.currentEpisodeNumber > 1,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Prev", tint = ErumiPrimaryText, modifier = Modifier.size(32.dp))
                    }

                    // Seek Backward 10s
                    IconButton(
                        onClick = { viewModel.seekBackward() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Replay10, contentDescription = "Replay 10s", tint = ErumiPrimaryText, modifier = Modifier.size(36.dp))
                    }

                    // Play / Pause Circle
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(ErumiPrimary)
                            .clickable { viewModel.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = ErumiBackground,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    // Seek Forward 10s
                    IconButton(
                        onClick = { viewModel.seekForward() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Forward10, contentDescription = "Forward 10s", tint = ErumiPrimaryText, modifier = Modifier.size(36.dp))
                    }

                    // Next Episode
                    IconButton(
                        onClick = { viewModel.playNextEpisode() },
                        enabled = state.currentEpisodeNumber < state.totalEpisodes,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next", tint = ErumiPrimaryText, modifier = Modifier.size(32.dp))
                    }
                }

                // Bottom Progress Bar & Timestamps
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    val currentDisplayMs = if (isDragging) dragPosition.toLong() else state.currentPositionMs

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentDisplayMs),
                            style = MaterialTheme.typography.labelSmall.copy(color = ErumiPrimaryText)
                        )
                        Text(
                            text = formatTime(state.durationMs),
                            style = MaterialTheme.typography.labelSmall.copy(color = ErumiSecondaryText)
                        )
                    }

                    val maxDuration = state.durationMs.toFloat().coerceAtLeast(1f)
                    val sliderValue = if (isDragging) {
                        dragPosition.coerceIn(0f, maxDuration)
                    } else {
                        state.currentPositionMs.toFloat().coerceIn(0f, maxDuration)
                    }

                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            isDragging = true
                            dragPosition = it
                        },
                        onValueChangeFinished = {
                            viewModel.seekTo(dragPosition.toLong())
                            isDragging = false
                        },
                        valueRange = 0f..maxDuration,
                        colors = SliderDefaults.colors(
                            thumbColor = ErumiPrimary,
                            activeTrackColor = ErumiPrimary,
                            inactiveTrackColor = ErumiBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Subtitle Selector Dialog
        if (showSubtitleDialog) {
            AlertDialog(
                onDismissRequest = { showSubtitleDialog = false },
                title = { Text(text = "Select Subtitles", color = ErumiPrimaryText) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Off option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectSubtitle("off")
                                    showSubtitleDialog = false
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Off", color = ErumiPrimaryText)
                            if (state.selectedSubtitleId == "off") {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = ErumiPrimary)
                            }
                        }

                        // Available subtitle tracks
                        if (state.availableSubtitles.isEmpty()) {
                            Text(
                                text = "No subtitle tracks found for this video",
                                style = MaterialTheme.typography.bodySmall,
                                color = ErumiSecondaryText,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            state.availableSubtitles.forEach { sub ->
                                val isSelected = state.selectedSubtitleId == sub.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectSubtitle(sub)
                                            showSubtitleDialog = false
                                        }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = sub.label, color = ErumiPrimaryText)
                                    if (isSelected) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = ErumiPrimary)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubtitleDialog = false }) {
                        Text(text = "Done", color = ErumiPrimary)
                    }
                },
                containerColor = ErumiElevatedSurface
            )
        }

        // Speed Selector Dialog
        if (showSpeedDialog) {
            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                title = { Text(text = "Playback Speed", color = ErumiPrimaryText) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        speeds.forEach { speed ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setPlaybackSpeed(speed)
                                        showSpeedDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "${speed}x", color = ErumiPrimaryText)
                                if (state.playbackSpeed == speed) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = ErumiPrimary)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSpeedDialog = false }) {
                        Text(text = "Done", color = ErumiPrimary)
                    }
                },
                containerColor = ErumiElevatedSurface
            )
        }

        // Aspect Ratio Dialog
        if (showAspectDialog) {
            AlertDialog(
                onDismissRequest = { showAspectDialog = false },
                title = { Text(text = "Aspect Ratio", color = ErumiPrimaryText) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ASPECT_LABELS.forEachIndexed { index, label ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        aspectModeIndex = index
                                        showAspectDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = label, color = ErumiPrimaryText)
                                if (aspectModeIndex == index) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = ErumiPrimary)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAspectDialog = false }) {
                        Text(text = "Done", color = ErumiPrimary)
                    }
                },
                containerColor = ErumiElevatedSurface
            )
        }

        // Lock indicator - shown briefly when lock state changes or user taps while locked
        if (isLocked && showLockIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = ErumiPrimary
                    )
                    Text(
                        text = "Screen Locked",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}
