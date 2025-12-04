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
        private const val POSITION_UPDATE_INTERVAL = 50L
    }

    init {
        initializeController()
    }

    fun initializeController() {
        Log.d(TAG, "Initializing media controller")
        _playerState.value = _playerState.value.copy(isConnecting = true, hasError = false)

        try {
            val sessionToken = SessionToken(context, ComponentName(context, PlayerService::class.java))
            controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

            controllerFuture?.addListener({
                try {
                    val controller = controllerFuture?.get()
                    if (controller != null) {
                        Log.d(TAG, "MediaController connected successfully")
                        _mediaController = controller
                        setupPlayerListener(controller)
                        updateMetadataFromPlayer(controller)

                        _playerState.value = _playerState.value.copy(
                            isConnecting = false,
                            isConnected = true,
                            isPlaying = controller.isPlaying,
                            currentPosition = controller.currentPosition,
                            duration = controller.duration.coerceAtLeast(0L),
                            bufferedPercentage = controller.bufferedPercentage,
                            volume = controller.volume
                        )

                        startPositionUpdates()
                    } else {
                        Log.e(TAG, "MediaController is null")
                        _playerState.value = _playerState.value.copy(
                            isConnecting = false,
                            hasError = true
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting MediaController", e)
                    _playerState.value = _playerState.value.copy(
                        isConnecting = false,
                        hasError = true
                    )
                }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing controller", e)
            _playerState.value = _playerState.value.copy(
                isConnecting = false,
                hasError = true
            )
        }
    }

    private fun setupPlayerListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
                Log.d(TAG, "Playback state changed: $isPlaying")
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) ||
                    events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    updateMetadataFromPlayer(player)
                    _playerState.value = _playerState.value.copy(
                        duration = player.duration.coerceAtLeast(0L)
                    )
                }
            }
        })
    }

    private fun updateMetadataFromPlayer(player: Player) {
        val metadata = player.currentMediaItem?.mediaMetadata ?: return

        val title = metadata.title?.toString() ?: "Unknown Title"
        val channelName = metadata.artist?.toString() ?: "Unknown Channel"
        val thumbnailUrl = metadata.artworkUri?.toString() ?: ""

        val description = metadata.description?.toString() ?: ""
        val parts = description.split("|")
        val viewCount = parts.getOrNull(0)?.trim() ?: ""
        val uploadTime = parts.getOrNull(1)?.trim() ?: ""
        val subscriberCount = parts.getOrNull(2)?.trim() ?: ""
        val channelAvatar = parts.getOrNull(3)?.trim() ?: ""

            _videoMetadata.value = VideoMetadata(
            title = title,
            channelName = channelName,
            thumbnailUrl = thumbnailUrl,
            viewCount = viewCount,
            uploadTime = uploadTime,
            subscriberCount = subscriberCount,
            channelAvatar = channelAvatar
        )

        Log.d(TAG, "Video metadata updated: $title by $channelName")
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                _mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        _playerState.value = _playerState.value.copy(
                            currentPosition = controller.currentPosition,
                            duration = controller.duration.coerceAtLeast(0L),
                            bufferedPercentage = controller.bufferedPercentage
                        )
                    }
                }
                delay(POSITION_UPDATE_INTERVAL)
            }
        }
    }

    fun play() {
        _mediaController?.play()
    }

    fun pause() {
        _mediaController?.pause()
    }

    fun togglePlayPause() {
        _mediaController?.let {
            if (it.isPlaying) pause() else play()
        }
    }

    fun seekTo(position: Long) {
        _mediaController?.seekTo(position)
        _playerState.value = _playerState.value.copy(currentPosition = position)
    }

    fun seekToPrevious() {
        _mediaController?.let {
            if (it.hasPreviousMediaItem()) {
                it.seekToPreviousMediaItem()
            } else {
                it.seekTo(0)
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
        val clampedVolume = volume.coerceIn(0f, 1f)
        _mediaController?.volume = clampedVolume
        _playerState.value = _playerState.value.copy(volume = clampedVolume)
    }

    fun releaseController() {
        positionUpdateJob?.cancel()
        controllerFuture?.let { future ->
            try {
                MediaController.releaseFuture(future)
                Log.d(TAG, "MediaController released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing MediaController", e)
            }
        }
        controllerFuture = null
        _mediaController = null
        _playerState.value = PlayerState()
    }

    override fun onCleared() {
        super.onCleared()
        releaseController()
    }
}