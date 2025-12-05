package com.github.opentube.player.queue

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents the type of playback queue being used.
 */
enum class QueueType {
    /**
     * Standard single video or sequential playback.
     */
    NORMAL,

    /**
     * Playlist with next/previous navigation.
     */
    PLAYLIST,

    /**
     * Background playback (audio-only, no video surface).
     */
    BACKGROUND
}

/**
 * Represents a complete playback queue with current position tracking.
 * Used for managing playlists, single videos, and background playback.
 */
@Parcelize
data class PlayQueue(
    /**
     * Index of currently playing item (0-based).
     */
    val currentIndex: Int = 0,

    /**
     * List of all items in the queue.
     */
    val items: List<PlayQueueItem>,

    /**
     * Type of queue (determines playback behavior).
     */
    val queueType: QueueType = QueueType.NORMAL
) : Parcelable {

    /**
     * Currently playing item or null if index out of bounds.
     */
    val currentItem: PlayQueueItem?
        get() = items.getOrNull(currentIndex)

    /**
     * Total number of items in queue.
     */
    val size: Int
        get() = items.size

    /**
     * True if queue has more items after current position.
     */
    val hasNext: Boolean
        get() = currentIndex < items.lastIndex

    /**
     * True if queue has items before current position.
     */
    val hasPrevious: Boolean
        get() = currentIndex > 0

    /**
     * True if queue is empty.
     */
    val isEmpty: Boolean
        get() = items.isEmpty()

    /**
     * Returns next item index or current if at end.
     */
    fun nextIndex(): Int = (currentIndex + 1).coerceAtMost(items.lastIndex)

    /**
     * Returns previous item index or 0 if at start.
     */
    fun previousIndex(): Int = (currentIndex - 1).coerceAtLeast(0)

    /**
     * Creates new queue with updated current index.
     */
    fun withIndex(newIndex: Int): PlayQueue {
        return PlayQueue(
            currentIndex = newIndex.coerceIn(0, items.lastIndex),
            items = items,
            queueType = queueType
        )
    }

    companion object {
        /**
         * Creates queue from single video item.
         */
        fun fromSingleItem(item: PlayQueueItem): PlayQueue {
            return PlayQueue(
                currentIndex = 0,
                items = listOf(item),
                queueType = QueueType.NORMAL
            )
        }

        /**
         * Creates playlist queue from list of items.
         */
        fun fromPlaylist(items: List<PlayQueueItem>, startIndex: Int = 0): PlayQueue {
            return PlayQueue(
                currentIndex = startIndex.coerceIn(0, items.lastIndex),
                items = items,
                queueType = QueueType.PLAYLIST
            )
        }

        /**
         * Creates empty queue.
         */
        fun empty(): PlayQueue {
            return PlayQueue(currentIndex = 0, items = emptyList())
        }
    }
}
