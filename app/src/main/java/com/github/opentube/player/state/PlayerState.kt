package com.github.opentube.player.state

import androidx.compose.runtime.Stable

@Stable
data class PlayerState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPercentage: Int = 0,
    val volume: Float = 1f,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = true,
    val hasError: Boolean = false
)

@Stable
data class VideoMetadata(
    val title: String = "Loading...",
    val channelName: String = "",
    val thumbnailUrl: String = "",
    val viewCount: String = "",
    val uploadTime: String = "",
    val subscriberCount: String = "",
    val channelAvatar: String = ""
)

@Stable
data class ControlsState(
    val showControls: Boolean = true,
    val showVolumeOverlay: Boolean = false,
    val showBrightnessOverlay: Boolean = false,
    val showSeekOverlay: Boolean = false,
    val showQualityDialog: Boolean = false,
    val seekDirection: Int = 0,
    val quality: String = "Auto",
    val brightness: Float = 0.5f
)

@Stable
data class OrientationState(
    val isLandscape: Boolean = false,
    val isFullscreen: Boolean = false
)