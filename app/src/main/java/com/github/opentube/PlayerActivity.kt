package com.github.opentube

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.github.opentube.player.PlayerHelper
import com.github.opentube.player.controller.PlayerController
import com.github.opentube.player.gesture.GestureHandler
import com.github.opentube.player.manager.ControlsManager
import com.github.opentube.player.queue.PlayQueue
import com.github.opentube.player.util.TimeFormatter

class PlayerActivity : ComponentActivity() {

    companion object {
        private const val TAG = "PlayerActivity"
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Activity onCreate called")

        intent.getParcelableExtra("play_queue", PlayQueue::class.java)?.let { queue ->
            PlayerHelper.playVideo(applicationContext, queue)
        }

        setContent {
            OpenTubePlayerTheme {
                PlayerScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "Activity onStart called")
    }
}

@Composable
fun PlayerScreen() {
    val context = LocalContext.current

    val playerController: PlayerController = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PlayerController(context) as T
            }
        }
    )

    val controlsManager: ControlsManager = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ControlsManager(context) as T
            }
        }
    )

    val playerState by playerController.playerState
    val videoMetadata by playerController.videoMetadata
    val controlsState by controlsManager.controlsState

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when {
            playerState.isConnecting -> {
                LoadingScreen()
            }
            playerState.isConnected -> {
                VideoPlayerContent(
                    playerController = playerController,
                    controlsManager = controlsManager
                )
            }
            playerState.hasError -> {
                ErrorScreen(onRetry = { playerController.initializeController() })
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Color.Red)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Connecting to player...",
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

@Composable
fun ErrorScreen(onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = Color.Red,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Failed to connect to player",
            color = Color.White,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
fun VideoPlayerContent(
    playerController: PlayerController,
    controlsManager: ControlsManager
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val playerState by playerController.playerState
    val videoMetadata by playerController.videoMetadata
    val controlsState by controlsManager.controlsState

    val gestureHandler = remember {
        GestureHandler(
            onSeekBackward = {
                val newPos = (playerState.currentPosition - 10000).coerceAtLeast(0)
                playerController.seekTo(newPos)
                controlsManager.showSeekOverlay(-1)
            },
            onSeekForward = {
                val newPos = (playerState.currentPosition + 10000).coerceAtMost(playerState.duration)
                playerController.seekTo(newPos)
                controlsManager.showSeekOverlay(1)
            },
            onBrightnessChange = { delta ->
                controlsManager.adjustBrightness(delta)
            },
            onVolumeChange = { delta ->
                val newVolume = playerState.volume + delta
                playerController.setVolume(newVolume)
            }
        )
    }

    LaunchedEffect(controlsState.showControls, playerState.isPlaying) {
        if (controlsState.showControls && playerState.isPlaying) {
            kotlinx.coroutines.delay(3000)
            controlsManager.hideControls()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isLandscape) Modifier.fillMaxSize()
                    else Modifier.aspectRatio(16 / 9f)
                )
                .background(Color.Black)
        ) {
            PlayerView(playerController)

            GestureLayer(
                gestureHandler = gestureHandler,
                controlsManager = controlsManager,
                playerController = playerController,
                playerState = playerState
            )

            OverlaysLayer(
                controlsState = controlsState,
                playerState = playerState
            )

            ControlsLayer(
                visible = controlsState.showControls,
                playerController = playerController,
                controlsManager = controlsManager,
                playerState = playerState,
                videoMetadata = videoMetadata,
                controlsState = controlsState,
                isLandscape = isLandscape,
                activity = activity
            )

            if (controlsState.showQualityDialog) {
                QualityDialogOverlay(
                    currentQuality = controlsState.quality,
                    onQualitySelect = { controlsManager.setQuality(it) },
                    onDismiss = { controlsManager.hideQualityDialog() }
                )
            }
        }

        if (!isLandscape) {
            VideoMetadataSection(
                videoMetadata = videoMetadata,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun PlayerView(playerController: PlayerController) {
    val mediaController = playerController.mediaController

    key(mediaController) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    player = mediaController
                }
            },
            update = { view ->
                if (view.player != mediaController) {
                    view.player = mediaController
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun GestureLayer(
    gestureHandler: GestureHandler,
    controlsManager: ControlsManager,
    playerController: PlayerController,
    playerState: com.github.opentube.player.state.PlayerState
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controlsManager.toggleControls()
                    },
                    onDoubleTap = { offset ->
                        gestureHandler.handleDoubleTap(offset, size.width.toFloat())
                    }
                )
            }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { controlsManager.showBrightnessOverlay() },
                            onDragEnd = { controlsManager.hideBrightnessOverlay() }
                        ) { change, dragAmount ->
                            change.consume()
                            gestureHandler.handleBrightnessDrag(
                                dragAmount = dragAmount,
                                screenHeight = size.height.toFloat()
                            )
                        }
                    }
            )

            Spacer(modifier = Modifier.weight(0.5f))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { controlsManager.showVolumeOverlay() },
                            onDragEnd = { controlsManager.hideVolumeOverlay() }
                        ) { change, dragAmount ->
                            change.consume()
                            gestureHandler.handleVolumeDrag(
                                dragAmount = dragAmount,
                                screenHeight = size.height.toFloat()
                            )
                        }
                    }
            )
        }
    }
}

@Composable
fun OverlaysLayer(
    controlsState: com.github.opentube.player.state.ControlsState,
    playerState: com.github.opentube.player.state.PlayerState
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (controlsState.showSeekOverlay) {
            SeekOverlay(
                seekDirection = controlsState.seekDirection,
                modifier = Modifier.align(
                    if (controlsState.seekDirection < 0) Alignment.CenterStart
                    else Alignment.CenterEnd
                )
            )
        }

        if (controlsState.showVolumeOverlay) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                GestureOverlay(
                    icon = if (playerState.volume == 0f) Icons.Default.VolumeOff
                    else Icons.Default.VolumeUp,
                    percentage = (playerState.volume * 100).toInt()
                )
            }
        }

        if (controlsState.showBrightnessOverlay) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                GestureOverlay(
                    icon = Icons.Default.Brightness6,
                    percentage = (controlsState.brightness * 100).toInt()
                )
            }
        }
    }
}

@Composable
fun SeekOverlay(seekDirection: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(horizontal = 48.dp)
            .size(60.dp)
            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (seekDirection < 0) Icons.Default.FastRewind
                else Icons.Default.FastForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Text("10s", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ControlsLayer(
    visible: Boolean,
    playerController: PlayerController,
    controlsManager: ControlsManager,
    playerState: com.github.opentube.player.state.PlayerState,
    videoMetadata: com.github.opentube.player.state.VideoMetadata,
    controlsState: com.github.opentube.player.state.ControlsState,
    isLandscape: Boolean,
    activity: Activity?
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { controlsManager.hideControls() }
        ) {
            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                TopControls(
                    videoTitle = videoMetadata.title,
                    quality = controlsState.quality,
                    isLandscape = isLandscape,
                    onBack = { activity?.finish() },
                    onQualityClick = { controlsManager.showQualityDialog() },
                    onSettingsClick = { }
                )
            }

            Box(modifier = Modifier.align(Alignment.Center)) {
                CenterControls(
                    isPlaying = playerState.isPlaying,
                    onPlayPause = { playerController.togglePlayPause() },
                    onPrevious = { playerController.seekToPrevious() },
                    onNext = { playerController.seekToNext() }
                )
            }

            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                BottomControls(
                    currentPosition = playerState.currentPosition,
                    duration = playerState.duration,
                    bufferedPercentage = playerState.bufferedPercentage,
                    isLandscape = isLandscape,
                    onSeek = { playerController.seekTo(it) },
                    onFullscreenToggle = {
                        if (isLandscape) {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun TopControls(
    videoTitle: String,
    quality: String,
    isLandscape: Boolean,
    onBack: () -> Unit,
    onQualityClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.KeyboardArrowDown, "Back", tint = Color.White)
        }

        if (isLandscape) {
            Text(
                text = videoTitle,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onQualityClick,
                shape = RoundedCornerShape(4.dp),
                color = Color.White.copy(alpha = 0.2f)
            ) {
                Text(
                    text = quality,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, "Settings", tint = Color.White)
            }
        }
    }
}

@Composable
fun CenterControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.Default.SkipPrevious,
                "Previous",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { onPlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        IconButton(onClick = onNext) {
            Icon(
                Icons.Default.SkipNext,
                "Next",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

@Composable
fun BottomControls(
    currentPosition: Long,
    duration: Long,
    bufferedPercentage: Int,
    isLandscape: Boolean,
    onSeek: (Long) -> Unit,
    onFullscreenToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = TimeFormatter.formatTime(currentPosition),
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 8.dp)
            )

            Box(modifier = Modifier.weight(1f)) {
                YouTubeProgressBar(
                    currentPosition = currentPosition,
                    duration = duration,
                    bufferedPercentage = bufferedPercentage,
                    onSeek = onSeek
                )
            }

            Text(
                text = TimeFormatter.formatTime(duration),
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp)
            )

            IconButton(onClick = onFullscreenToggle) {
                Icon(
                    imageVector = if (isLandscape) Icons.Default.FullscreenExit
                    else Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeProgressBar(
    currentPosition: Long,
    duration: Long,
    bufferedPercentage: Int,
    onSeek: (Long) -> Unit
) {
    val safeDuration = duration.coerceAtLeast(1L)
    val externalValue = currentPosition.toFloat() / safeDuration.toFloat()

    var sliderValue by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    if (!isDragging) sliderValue = externalValue

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0x33FFFFFF), RoundedCornerShape(2.dp))
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferedPercentage / 100f)
                    .height(4.dp)
                    .background(Color(0x66FFFFFF), RoundedCornerShape(2.dp))
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(sliderValue)
                    .height(4.dp)
                    .background(Color(0xFFFF0000), RoundedCornerShape(2.dp))
            )
        }

        Slider(
            value = sliderValue,
            onValueChange = {
                isDragging = true
                sliderValue = it
            },
            onValueChangeFinished = {
                val newPos = (sliderValue * safeDuration).toLong()
                onSeek(newPos)
                isDragging = false
            },
            valueRange = 0f..1f,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            ),
            thumb = {
                if (isDragging) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color.White, CircleShape)
                            .border(
                                width = 2.dp,
                                color = Color(0xFFFF0000),
                                shape = CircleShape
                            )
                    )
                }
            }
        )
    }
}

@Composable
fun GestureOverlay(icon: ImageVector, percentage: Int) {
    Column(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text("$percentage%", color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun QualityDialogOverlay(
    currentQuality: String,
    onQualitySelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        QualityDialog(
            currentQuality = currentQuality,
            onQualitySelect = onQualitySelect,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun QualityDialog(
    currentQuality: String,
    onQualitySelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF222222),
        modifier = Modifier.width(250.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Select Quality",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Divider(
                color = Color.Gray.copy(alpha = 0.3f),
                modifier = Modifier.padding(vertical = 12.dp)
            )
            listOf("Auto", "1080p", "720p", "480p", "360p").forEach { q ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onQualitySelect(q) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(q, color = Color.White)
                    if (q == currentQuality) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint = Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoMetadataSection(
    videoMetadata: com.github.opentube.player.state.VideoMetadata,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F0F))
            .padding(12.dp)
    ) {
        Text(
            text = videoMetadata.title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(videoMetadata.channelAvatar.ifEmpty { null })
                    .crossfade(true)
                    .build(),
                contentDescription = "Channel Avatar",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.DarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.DarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Default Avatar",
                            tint = Color.Gray,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = videoMetadata.channelName,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                if (videoMetadata.subscriberCount.isNotEmpty()) {
                    Text(
                        text = videoMetadata.subscriberCount,
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionButton(Icons.Default.ThumbUp, "Like")
            ActionButton(Icons.Default.ThumbDown, "Dislike")
            ActionButton(Icons.Default.Share, "Share")
            ActionButton(Icons.Default.Download, "Download")
            ActionButton(Icons.Default.PlaylistAdd, "Save")
        }
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { }
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.05f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 10.sp)
    }
}

@Composable
fun OpenTubePlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFFF0000),
            surface = Color(0xFF0F0F0F),
            background = Color.Black
        ),
        content = content
    )
}
