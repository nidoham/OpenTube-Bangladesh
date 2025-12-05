package com.github.opentube.player.state

import androidx.compose.runtime.Stable

/**
 * Player playback state for UI reactivity.
 * Used to drive Compose UI updates for playback controls.
 */
@Stable
data class PlayerState(
    /** Currently playing or paused */
    val isPlaying: Boolean = false,

    /** Current playback position in milliseconds */
    val currentPosition: Long = 0L,

    /** Total duration in milliseconds (0 for live streams) */
    val duration: Long = 0L,

    /** Buffer progress (0-100) */
    val bufferedPercentage: Int = 0,

    /** Volume level (0.0f - 1.0f) */
    val volume: Float = 1f,

    /** Controller connected to PlayerService */
    val isConnected: Boolean = false,

    /** Connecting to PlayerService */
    val isConnecting: Boolean = true,

    /** Connection or playback error occurred */
    val hasError: Boolean = false
) {
    /** Progress as fraction (0.0 - 1.0) for seekbar */
    val progress: Float = if (duration > 0L) currentPosition.toFloat() / duration else 0f

    /** Formatted position string for display */
    val formattedPosition: String get() = formatDuration(currentPosition)

    /** Formatted duration string for display */
    val formattedDuration: String get() = formatDuration(duration)

    /** True if player is ready for user interaction */
    val isReady: Boolean get() = isConnected && !isConnecting && !hasError
}

/**
 * Video metadata extracted from YouTube streams.
 * Populated by PlayerService from NewPipe extractor.
 */
@Stable
data class VideoMetadata(
    /** Video title */
    val title: String = "Loading...",

    /** Channel/uploader name */
    val channelName: String = "",

    /** Primary thumbnail URL */
    val thumbnailUrl: String = "",

    /** Formatted view count (e.g., "1.2M views") */
    val viewCount: String = "",

    /** Upload time (e.g., "2 days ago") */
    val uploadTime: String = "",

    /** Formatted subscriber count (e.g., "1.2M subscribers") */
    val subscriberCount: String = "",

    /** Channel avatar URL */
    val channelAvatar: String = ""
)

/**
 * Player controls UI state for overlays and dialogs.
 */
@Stable
data class ControlsState(
    /** Main controls bar visibility */
    val showControls: Boolean = true,

    /** Volume adjustment overlay */
    val showVolumeOverlay: Boolean = false,

    /** Brightness adjustment overlay */
    val showBrightnessOverlay: Boolean = false,

    /** Seek preview overlay (+10s / -10s) */
    val showSeekOverlay: Boolean = false,

    /** Quality selection dialog */
    val showQualityDialog: Boolean = false,

    /** Seek direction: positive=forward, negative=backward */
    val seekDirection: Int = 0,

    /** Selected video quality */
    val quality: String = "Auto",

    /** Screen brightness (0.0f - 1.0f) */
    val brightness: Float = 0.5f
) {
    /** True if any overlay is active */
    val hasOverlay: Boolean
        get() = showVolumeOverlay || showBrightnessOverlay || showSeekOverlay || showQualityDialog
}

/**
 * Screen orientation and fullscreen state.
 */
@Stable
data class OrientationState(
    /** Currently in landscape orientation */
    val isLandscape: Boolean = false,

    /** Fullscreen mode active */
    val isFullscreen: Boolean = false,

    /** Should enter fullscreen on landscape */
    val shouldEnterFullscreen: Boolean = false
)

/**
 * Utility function to format duration (requires TimeFormatter import)
 */
private fun formatDuration(durationMs: Long): String {
    // TimeFormatter.formatShortTime(durationMs)
    return "0:00" // Placeholder - replace with actual formatter
}
