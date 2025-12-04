package com.github.opentube.player.queue

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class QueueType {
    NORMAL,
    PLAYLIST,
    BACKGROUND
}

@Parcelize
data class PlayQueue(
    val currentIndex: Int,
    val items: List<PlayQueueItem>,
    val queueType: QueueType = QueueType.NORMAL
) : Parcelable {

    val currentItem: PlayQueueItem?
        get() = items.getOrNull(currentIndex)

    companion object {
        fun fromSingleItem(item: PlayQueueItem): PlayQueue {
            return PlayQueue(
                currentIndex = 0,
                items = listOf(item),
                queueType = QueueType.NORMAL
            )
        }
    }
}