package com.github.opentube.player.controller

import android.app.Application
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.github.extractor.stream.VideoMetadata
import com.github.opentube.player.PlayerService
import com.github.opentube.player.state.PlayerState
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Serializable

class PlayerController(application: Application) : AndroidViewModel(application) {

    private val _playerState = mutableStateOf(PlayerState())
    val playerState: State<PlayerState> = _playerState

    private val _videoMetadata = mutableStateOf(
        VideoMetadata(
            name = "Loading...",
            thumbnailUrl = null,
            uploaderName = "",
            uploaderAvatarUrl = null,
            uploaderSubscriberCount = -1L,
            viewCount = -1L,
            uploadDate = null
        )
    )
    val videoMetadata: State<VideoMetadata> = _videoMetadata

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var _mediaController: MediaController? = null
    private var positionUpdateJob: Job? = null

    companion object {
        private const val TAG = "PlayerController"
        private const val POSITION_UPDATE_INTERVAL = 100L
    }

    init {
        initializeController()
    }

    fun initializeController() {
        Log.d(TAG, "Initializing controller")
        updatePlayerState { it.copy(isConnecting = true, hasError = false) }

        val context = getApplication<Application>()
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlayerService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get()
                if (controller != null) {
                    onControllerConnected(controller)
                } else {
                    Log.e(TAG, "Controller is null after future completed")
                    updatePlayerState { it.copy(isConnecting = false, hasError = true) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to controller", e)
                updatePlayerState { it.copy(isConnecting = false, hasError = true) }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun onControllerConnected(controller: MediaController) {
        Log.d(TAG, "Controller connected successfully")
        _mediaController = controller
        setupPlayerListener(controller)
        updateMetadataFromPlayer(controller)
        startPositionUpdates()

        updatePlayerState { state ->
            state.copy(
                isConnecting = false,
                isConnected = true,
                isPlaying = controller.isPlaying,
                currentPosition = controller.currentPosition,
                duration = controller.duration.coerceAtLeast(0L),
                bufferedPercentage = controller.bufferedPercentage
            )
        }
    }

    private fun setupPlayerListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "onIsPlayingChanged: $isPlaying")
                updatePlayerState { it.copy(isPlaying = isPlaying) }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) ||
                    events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
                ) {
                    Log.d(TAG, "Media metadata or item changed")
                    updateMetadataFromPlayer(player)
                }

                if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                    Log.d(TAG, "Playback state: ${player.playbackState}")
                }
            }
        })
    }

    private fun updateMetadataFromPlayer(player: Player) {
        val mediaItem = player.currentMediaItem
        if (mediaItem == null) {
            Log.w(TAG, "No current media item")
            return
        }

        val metadata = mediaItem.mediaMetadata
        val extras = metadata.extras

        if (extras == null) {
            Log.w(TAG, "No extras in media metadata")
            setFallbackMetadata(metadata)
            return
        }

        // Set ClassLoader to avoid ClassNotFoundException
        extras.classLoader = VideoMetadata::class.java.classLoader

        try {
            val receivedMetadata = getSerializableCompat(
                extras,
                PlayerService.EXTRA_METADATA_OBJECT,
                VideoMetadata::class.java
            )

            if (receivedMetadata != null) {
                _videoMetadata.value = receivedMetadata
                Log.d(TAG, "Successfully received VideoMetadata: ${receivedMetadata.name}")
            } else {
                Log.w(TAG, "VideoMetadata is null, using fallback")
                setFallbackMetadata(metadata)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting VideoMetadata", e)
            setFallbackMetadata(metadata)
        }
    }

    private fun setFallbackMetadata(metadata: androidx.media3.common.MediaMetadata) {
        _videoMetadata.value = VideoMetadata(
            name = metadata.title?.toString() ?: "Unknown",
            thumbnailUrl = metadata.artworkUri?.toString(),
            uploaderName = metadata.artist?.toString() ?: "",
            uploaderAvatarUrl = null,
            uploaderSubscriberCount = -1L,
            viewCount = -1L,
            uploadDate = metadata.description?.toString()
        )
    }

    private fun <T : Serializable> getSerializableCompat(
        bundle: Bundle,
        key: String,
        clazz: Class<T>
    ): T? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getSerializable(key, clazz)
            } else {
                @Suppress("DEPRECATION")
                bundle.getSerializable(key) as? T
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting serializable from bundle", e)
            null
        }
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                _mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        updatePlayerState {
                            it.copy(
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

    private inline fun updatePlayerState(transform: (PlayerState) -> PlayerState) {
        _playerState.value = transform(_playerState.value)
    }

    fun play() {
        _mediaController?.play()
        Log.d(TAG, "Play called")
    }

    fun pause() {
        _mediaController?.pause()
        Log.d(TAG, "Pause called")
    }

    fun togglePlayPause() {
        if (_mediaController?.isPlaying == true) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(position: Long) {
        _mediaController?.seekTo(position)
        updatePlayerState { it.copy(currentPosition = position) }
        Log.d(TAG, "Seek to: $position")
    }

    fun seekToNext() {
        if (_mediaController?.hasNextMediaItem() == true) {
            _mediaController?.seekToNextMediaItem()
            Log.d(TAG, "Seeking to next media item")
        }
    }

    fun seekToPrevious() {
        val controller = _mediaController
        if (controller != null) {
            if (controller.currentPosition > 3000) {
                // If more than 3 seconds into the track, restart it
                seekTo(0)
            } else if (controller.hasPreviousMediaItem()) {
                controller.seekToPreviousMediaItem()
                Log.d(TAG, "Seeking to previous media item")
            } else {
                seekTo(0)
            }
        }
    }

    fun setVolume(volume: Float) {
        _mediaController?.volume = volume.coerceIn(0f, 1f)
        Log.d(TAG, "Volume set to: $volume")
    }

    override fun onCleared() {
        Log.d(TAG, "PlayerController cleared")
        positionUpdateJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        _mediaController = null
        super.onCleared()
    }
}