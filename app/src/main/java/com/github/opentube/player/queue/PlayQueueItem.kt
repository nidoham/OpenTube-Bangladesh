package com.github.opentube.player.queue

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.schabi.newpipe.extractor.Image
import java.util.UUID

@Parcelize
data class PlayQueueItem(
    // Unique ID to distinguish identical videos in the same queue
    val queueId: String = UUID.randomUUID().toString(),

    // Metadata for UI (Notification/Player Screen)
    val title: String,
    val channelName: String,
    val thumbnailUrl: List<Image>?,

    // The actual YouTube URL (e.g., https://youtube.com/watch?v=...)
    val videoUrl: String,

    // Optional: Duration in seconds
    val duration: Long = 0L
) : Parcelable