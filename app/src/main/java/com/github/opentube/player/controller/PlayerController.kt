package com.github.opentube.player.controller

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.github.opentube.player.PlayerService
import com.github.opentube.player.state.PlayerState
import com.github.opentube.player.state.VideoMetadata
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages Media3 MediaController connection and player state for Jetpack Compose UI.
 * Handles connection, playback control, metadata updates, and position tracking.
 */
class PlayerController(private val context: Context) : ViewModel() {

    private val _playerState = mutableStateOf(PlayerState())
    val playerState: State<PlayerState> = _playerState

    private val _videoMetadata = mutableStateOf(VideoMetadata())
    val videoMetadata: State<VideoMetadata> = _videoMetadata

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var _mediaController: MediaController? = null
    val mediaController: MediaController? get() = _mediaController
    private var positionUpdateJob: Job? = null

    companion object {
        private const val TAG = "PlayerController"
        private const val POSITION_UPDATE_INTERVAL = 100L // Reduced for better perf
    }

    init {
        initializeController()
    }

    /**
     * Initializes MediaController connection to PlayerService.
     */
    fun initializeController() {
        Log.d(TAG, "Initializing media controller")
        updatePlayerState { it.copy(isConnecting = true, hasError = false) }

        val sessionToken = SessionToken(context, ComponentName(context, PlayerService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture?.addListener({
            val controller = runCatching { controllerFuture?.get() }
                .getOrNull()

            if (controller != null) {
                onControllerConnected(controller)
            } else {
                onConnectionError("MediaController is null")
            }
        }, MoreExecutors.directExecutor())
    }

    private fun onControllerConnected(controller: MediaController) {
        Log.d(TAG, "MediaController connected successfully")
        _mediaController = controller
        setupPlayerListener(controller)
        updateMetadataFromPlayer(controller)

        updatePlayerState { state ->
            state.copy(
                isConnecting = false,
                isConnected = true,
                isPlaying = controller.isPlaying,
                currentPosition = controller.currentPosition,
                duration = controller.duration.coerceAtLeast(0L),
                bufferedPercentage = controller.bufferedPercentage,
                volume = controller.volume
            )
        }
        startPositionUpdates()
    }

    private fun onConnectionError(error: String) {
        Log.e(TAG, error)
        updatePlayerState { it.copy(isConnecting = false, hasError = true) }
    }

    /**
     * Sets up Media3 Player.Listener for real-time state updates.
     */
    private fun setupPlayerListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayerState { it.copy(isPlaying = isPlaying) }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) ||
                    events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                    events.contains(Player.EVENT_POSITION_DISCONTINUITY)) {
                    updateMetadataFromPlayer(player)
                    updatePlayerState {
                        it.copy(duration = player.duration.coerceAtLeast(0L))
                    }
                }
            }
        })
    }

    /**
     * Extracts and parses video metadata from Media3 MediaMetadata.
     */
    private fun updateMetadataFromPlayer(player: Player) {
        val metadata = player.currentMediaItem?.mediaMetadata ?: return

        val title = metadata.title?.toString() ?: "Unknown Title"
        val channelName = metadata.artist?.toString() ?: "Unknown Channel"
        val thumbnailUrl = metadata.artworkUri?.toString().orEmpty()

        val descriptionParts = (metadata.description?.toString() ?: "")
            .split("|")
            .map { it.trim() }

        _videoMetadata.value = VideoMetadata(
            title = title,
            channelName = channelName,
            thumbnailUrl = thumbnailUrl,
            viewCount = descriptionParts.getOrNull(0).orEmpty(),
            uploadTime = descriptionParts.getOrNull(1).orEmpty(),
            subscriberCount = descriptionParts.getOrNull(2).orEmpty(),
            channelAvatar = descriptionParts.getOrNull(3).orEmpty()
        )

        Log.d(TAG, "Metadata updated: $title by $channelName")
    }

    /**
     * Starts continuous position tracking coroutine.
     */
    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                _mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        updatePlayerState { state ->
                            state.copy(
                                currentPosition = controller.currentPosition,
                                duration = controller.duration.coerceAtLeast(0L),
                                bufferedPercentage = controller.bufferedPercentage
                            )
                        }
                    }
                }
                delay(POSITION_UPDATE_INTERVAL)
            }
        }
    }

    /**
     * Safely updates player state with current state transformation.
     */
    private inline fun updatePlayerState(transform: (PlayerState) -> PlayerState) {
        _playerState.value = transform(_playerState.value)
    }

    // Public playback controls
    fun play() = _mediaController?.play()
    fun pause() = _mediaController?.pause()

    fun togglePlayPause() {
        _mediaController?.let {
            if (it.isPlaying) pause() else play()
        }
    }

    fun seekTo(position: Long) {
        _mediaController?.seekTo(position)
        updatePlayerState { it.copy(currentPosition = position.coerceAtLeast(0L)) }
    }

    fun seekToPrevious() {
        _mediaController?.let {
            if (it.hasPreviousMediaItem()) {
                it.seekToPreviousMediaItem()
            } else {
                seekTo(0)
            }
        }
    }

    fun seekToNext() {
        _mediaController?.let {
            if (it.hasNextMediaItem()) {
                it.seekToNextMediaItem()
            }
        }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _mediaController?.volume = clamped
        updatePlayerState { it.copy(volume = clamped) }
    }

    /**
     * Properly releases MediaController and cleans up resources.
     */
    fun releaseController() {
        positionUpdateJob?.cancel()
        controllerFuture?.let { future ->
            runCatching {
                MediaController.releaseFuture(future)
            }.onFailure { e ->
                Log.e(TAG, "Error releasing controller", e)
            }
        }
        controllerFuture = null
        _mediaController = null
        _playerState.value = PlayerState()
        Log.d(TAG, "PlayerController released")
    }

    override fun onCleared() {
        super.onCleared()
        releaseController()
    }
}
