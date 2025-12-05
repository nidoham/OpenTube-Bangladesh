package com.github.opentube.player.queue

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.schabi.newpipe.extractor.Image
import java.util.UUID

/**
 * Represents a single item in the playback queue.
 * Used for both single video playback and playlist/queue management.
 */
@Parcelize
data class PlayQueueItem(
    /**
     * Unique identifier for this queue item.
     * Distinguishes identical videos within the same queue.
     */
    val queueId: String = UUID.randomUUID().toString(),

    /**
     * Video title for display in notifications and player UI
     */
    val title: String,

    /**
     * Channel/uploader name for metadata display
     */
    val channelName: String,

    /**
     * List of thumbnail URLs (various sizes) for adaptive loading
     */
    val thumbnailUrl: List<Image>? = null,

    /**
     * YouTube watch URL (e.g., https://youtube.com/watch?v=dQw4w9WgXcQ)
     * Used for stream extraction
     */
    val videoUrl: String,

    /**
     * Video duration in milliseconds (0 = live/unavailable)
     */
    val duration: Long = 0L
) : Parcelable {

    /**
     * Returns the first available thumbnail URL or null if none available.
     */
    fun getThumbnailUrl(): String? {
        return thumbnailUrl?.firstOrNull()?.url
    }

    /**
     * Returns the highest resolution thumbnail URL available.
     */
    fun getBestThumbnailUrl(): String? {
        return thumbnailUrl
            ?.maxByOrNull { it.height * it.width }
            ?.url
    }

    /**
     * Checks if this is a live stream (duration unknown)
     */
    val isLive: Boolean
        get() = duration == 0L

    companion object {
        /**
         * Creates a PlayQueueItem from minimal data (URL + title).
         */
        fun fromUrl(videoUrl: String, title: String = "", channelName: String = ""): PlayQueueItem {
            return PlayQueueItem(
                title = title,
                channelName = channelName,
                videoUrl = videoUrl
            )
        }
    }
}
