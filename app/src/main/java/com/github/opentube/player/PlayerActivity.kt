package com.github.opentube.player

import android.app.Activity
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.core.net.toUri
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.github.opentube.R
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val playerState = mutableStateOf<MediaController?>(null)
    private val isConnecting = mutableStateOf(true)

    companion object {
        private const val TAG = "PlayerActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Activity onCreate called")

        setContent {
            OpenTubePlayerTheme {
                val player by playerState
                val connecting by isConnecting

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        connecting -> {
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
                        player != null -> {
                            VideoPlayerScreen(player = player!!)
                        }
                        else -> {
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
                                Button(onClick = { initializeController() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        // Example: Play a video when activity starts
         PlayerHelper.playSingleVideo(applicationContext, "https://youtu.be/YIucrdfR6rI")

        Log.d(TAG, "Activity onStart called")
        initializeController()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "Activity onStop called")
        releaseController()
    }

    private fun initializeController() {
        Log.d(TAG, "Initializing media controller")
        isConnecting.value = true

        try {
            val sessionToken = SessionToken(this, ComponentName(this, PlayerService::class.java))
            controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

            controllerFuture?.addListener({
                try {
                    val controller = controllerFuture?.get()
                    if (controller != null) {
                        Log.d(TAG, "MediaController connected successfully")
                        Log.d(TAG, "Media items in queue: ${controller.mediaItemCount}")
                        playerState.value = controller
                        isConnecting.value = false
                    } else {
                        Log.e(TAG, "MediaController is null")
                        isConnecting.value = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting MediaController", e)
                    isConnecting.value = false
                }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing controller", e)
            isConnecting.value = false
        }
    }

    private fun releaseController() {
        controllerFuture?.let { future ->
            try {
                MediaController.releaseFuture(future)
                Log.d(TAG, "MediaController released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing MediaController", e)
            }
        }
        controllerFuture = null
        playerState.value = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Activity onDestroy called")
        releaseController()
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

@Composable
fun VideoPlayerScreen(player: Player) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentPosition by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }
    var bufferedPercentage by remember { mutableIntStateOf(player.bufferedPercentage) }

    var videoTitle by remember { mutableStateOf(player.currentMediaItem?.mediaMetadata?.title?.toString() ?: "Loading...") }
    var channelName by remember { mutableStateOf(player.currentMediaItem?.mediaMetadata?.artist?.toString() ?: "") }
    var thumbnailUrl by remember { mutableStateOf(player.currentMediaItem?.mediaMetadata?.artworkUri?.toString() ?: "") }
    var viewCount by remember { mutableStateOf("") }
    var uploadTime by remember { mutableStateOf("") }
    var subscriberCount by remember { mutableStateOf("") }

    var volume by remember { mutableFloatStateOf(player.volume) }

    var brightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f }
                ?: try {
                    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
                } catch (e: Exception) { 0.5f }
        )
    }

    var showVolumeOverlay by remember { mutableStateOf(false) }
    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var showSeekOverlay by remember { mutableStateOf(false) }
    var seekDirection by remember { mutableIntStateOf(0) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf("Auto") }

    val scope = rememberCoroutineScope()

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isProviderPlaying: Boolean) {
                isPlaying = isProviderPlaying
                Log.d("VideoPlayer", "Playback state changed: $isProviderPlaying")
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) ||
                    events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    val metadata = player.currentMediaItem?.mediaMetadata
                    videoTitle = metadata?.title?.toString() ?: "Unknown Title"
                    channelName = metadata?.artist?.toString() ?: "Unknown Channel"
                    thumbnailUrl = metadata?.artworkUri?.toString() ?: ""

                    val description = metadata?.description?.toString() ?: ""
                    val parts = description.split("|")
                    viewCount = parts.getOrNull(0)?.trim() ?: ""
                    uploadTime = parts.getOrNull(1)?.trim() ?: ""
                    subscriberCount = parts.getOrNull(2)?.trim() ?: ""

                    duration = player.duration.coerceAtLeast(0L)

                    Log.d("VideoPlayer", "Video changed: $videoTitle by $channelName")
                }
            }
        }
        player.addListener(listener)

        val metadata = player.currentMediaItem?.mediaMetadata
        if (metadata != null) {
            videoTitle = metadata.title?.toString() ?: "Unknown Title"
            channelName = metadata.artist?.toString() ?: "Unknown Channel"
            thumbnailUrl = metadata.artworkUri?.toString() ?: ""
            val description = metadata.description?.toString() ?: ""
            val parts = description.split("|")
            viewCount = parts.getOrNull(0)?.trim() ?: ""
            uploadTime = parts.getOrNull(1)?.trim() ?: ""
            subscriberCount = parts.getOrNull(2)?.trim() ?: ""
        }

        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            bufferedPercentage = player.bufferedPercentage
            delay(50)
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
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        keepScreenOn = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                showControls = !showControls
                            },
                            onDoubleTap = { offset ->
                                val screenWidth = size.width
                                if (offset.x < screenWidth / 2) {
                                    player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
                                    seekDirection = -1
                                } else {
                                    player.seekTo((player.currentPosition + 10000).coerceAtMost(player.duration))
                                    seekDirection = 1
                                }
                                currentPosition = player.currentPosition
                                showSeekOverlay = true
                                scope.launch {
                                    delay(800)
                                    showSeekOverlay = false
                                }
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
                                    onDragStart = { showControls = false },
                                    onDragEnd = {
                                        scope.launch { delay(500); showBrightnessOverlay = false }
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    val newBrightness = (brightness - dragAmount / 3000f).coerceIn(0f, 1f)
                                    brightness = newBrightness

                                    val layoutParams = activity?.window?.attributes
                                    layoutParams?.screenBrightness = newBrightness
                                    activity?.window?.attributes = layoutParams

                                    showBrightnessOverlay = true
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
                                    onDragStart = { showControls = false },
                                    onDragEnd = {
                                        scope.launch { delay(500); showVolumeOverlay = false }
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    volume = (volume - dragAmount / 3000f).coerceIn(0f, 1f)
                                    player.volume = volume
                                    showVolumeOverlay = true
                                }
                            }
                    )
                }
            }

            if (showSeekOverlay) {
                Box(
                    modifier = Modifier
                        .align(if (seekDirection < 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 48.dp)
                        .size(60.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (seekDirection < 0) Icons.Default.FastRewind else Icons.Default.FastForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Text("10s", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (showVolumeOverlay) {
                Box(modifier = Modifier.align(Alignment.Center)) {
                    GestureOverlay(
                        icon = if (volume == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        percentage = (volume * 100).toInt()
                    )
                }
            }

            if (showBrightnessOverlay) {
                Box(modifier = Modifier.align(Alignment.Center)) {
                    GestureOverlay(
                        icon = Icons.Default.Brightness6,
                        percentage = (brightness * 100).toInt()
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { showControls = false }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { activity?.finish() }) {
                            Icon(Icons.Default.KeyboardArrowDown, "Back", tint = Color.White)
                        }

                        if (isLandscape) {
                            Text(
                                text = videoTitle,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                onClick = { showQualityDialog = true },
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
                            IconButton(onClick = { /* Settings */ }) {
                                Icon(Icons.Default.Settings, "Settings", tint = Color.White)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(40.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else player.seekTo(0)
                        }) {
                            Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(40.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                                .clickable {
                                    if (isPlaying) player.pause() else player.play()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        IconButton(onClick = {
                            if (player.hasNextMediaItem()) player.seekToNextMediaItem()
                        }) {
                            Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(formatTime(currentPosition), color = Color.White, fontSize = 12.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(formatTime(duration), color = Color.White, fontSize = 12.sp)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ModernProgressBar(
                                    currentPosition = currentPosition,
                                    duration = duration,
                                    bufferedPercentage = bufferedPercentage,
                                    onSeek = { pos ->
                                        player.seekTo(pos)
                                        currentPosition = pos
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            IconButton(onClick = {
                                if (isLandscape) {
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                            }) {
                                Icon(
                                    imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            if (showQualityDialog) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(10f)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable { showQualityDialog = false },
                    contentAlignment = Alignment.Center
                ) {
                    QualityDialog(
                        currentQuality = quality,
                        onQualitySelect = {
                            quality = it
                            showQualityDialog = false
                        },
                        onDismiss = { showQualityDialog = false }
                    )
                }
            }
        }

        if (!isLandscape) {
            VideoMetadata(
                title = videoTitle,
                channelName = channelName,
                views = viewCount,
                uploadTime = uploadTime,
                subscriberCount = subscriberCount,
                thumbnailUrl = thumbnailUrl,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernProgressBar(
    currentPosition: Long,
    duration: Long,
    bufferedPercentage: Int,
    onSeek: (Long) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    if (!isDragging) {
        sliderPosition = currentPosition.toFloat()
    }

    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.height(20.dp)) {
        Slider(
            value = sliderPosition,
            onValueChange = {
                isDragging = true
                sliderPosition = it
            },
            onValueChangeFinished = {
                onSeek(sliderPosition.toLong())
                isDragging = false
            },
            valueRange = 0f..(duration.coerceAtLeast(1L).toFloat()),
            colors = SliderDefaults.colors(
                thumbColor = Color.Red,
                activeTrackColor = Color.Red,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            ),
            thumb = {
                val thumbSize = if (isDragging) 20.dp else 12.dp
                Box(modifier = Modifier.size(thumbSize).background(Color.Red, CircleShape))
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
fun QualityDialog(currentQuality: String, onQualitySelect: (String) -> Unit, onDismiss: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF222222), modifier = Modifier.width(250.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Select Quality", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 12.dp))
            listOf("Auto", "1080p", "720p", "480p", "360p").forEach { q ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onQualitySelect(q) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(q, color = Color.White)
                    if (q == currentQuality) Icon(Icons.Default.Check, null, tint = Color.Red, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun VideoMetadata(
    title: String,
    channelName: String,
    views: String,
    uploadTime: String,
    subscriberCount: String,
    thumbnailUrl: String,
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
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Channel Avatar with better error handling
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(thumbnailUrl.ifEmpty { null })
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
                        modifier = Modifier.fillMaxSize().background(Color.DarkGray),
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
                        modifier = Modifier.fillMaxSize().background(Color.DarkGray),
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
                    text = channelName,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                if (subscriberCount.isNotEmpty()) {
                    Text(
                        text = subscriberCount,
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

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}