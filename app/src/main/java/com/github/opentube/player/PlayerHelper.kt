package com.github.opentube.player

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.github.opentube.player.queue.PlayQueue
import com.github.opentube.player.queue.PlayQueueItem
import com.github.opentube.player.queue.QueueType

/**
 * Helper class providing convenient methods to start and control YouTube video playback
 * through the PlayerService. This class abstracts away the complexity of service interaction
 * and provides a simple interface for playing YouTube videos from any part of the application.
 *
 * The helper handles service intent creation and ensures proper service lifecycle management
 * by using foreground service startup when appropriate for background playback requirements.
 */
object PlayerHelper {

    /**
     * Initiates playback of videos from a PlayQueue. Handles both single video and playlist scenarios.
     *
     * @param context Application or activity context
     * @param queue The play queue containing videos to play
     */
    fun playVideo(context: Context, queue: PlayQueue) {
        val type = queue.queueType
        val items = queue.items

        when (type) {
            QueueType.NORMAL -> {
                val firstItem = items.firstOrNull()
                firstItem?.let { playSingleVideo(context, it.videoUrl) }
            }
            else -> {
                val urls = items.map { it.videoUrl }
                playMultipleVideos(context, urls, 0)
            }
        }
    }

    /**
     * Plays a single YouTube video.
     *
     * @param context Application or activity context
     * @param youtubeUrl YouTube video URL to play
     */
    fun playSingleVideo(context: Context, youtubeUrl: String) {
        playMultipleVideos(context, listOf(youtubeUrl), 0)
    }

    /**
     * Plays multiple YouTube videos as a playlist.
     *
     * @param context Application or activity context
     * @param youtubeUrls List of YouTube video URLs
     * @param startIndex Index of first video to play (default: 0)
     */
    fun playMultipleVideos(context: Context, youtubeUrls: List<String>, startIndex: Int = 0) {
        val intent = Intent(context, PlayerService::class.java).apply {
            action = PlayerService.ACTION_PLAY_YOUTUBE_URLS
            putStringArrayListExtra(PlayerService.EXTRA_YOUTUBE_URLS, ArrayList(youtubeUrls))
            putExtra(PlayerService.EXTRA_START_INDEX, startIndex)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * Stops all playback and terminates the PlayerService.
     *
     * @param context Application or activity context
     */
    fun stopPlayback(context: Context) {
        val intent = Intent(context, PlayerService::class.java)
        context.stopService(intent)
    }
}

/**
 * Extension function: Play a single YouTube video from any Context.
 */
fun Context.playYoutubeVideo(url: String) {
    PlayerHelper.playSingleVideo(this, url)
}

/**
 * Extension function: Play YouTube playlist from any Context.
 */
fun Context.playYoutubePlaylist(urls: List<String>, startIndex: Int = 0) {
    PlayerHelper.playMultipleVideos(this, urls, startIndex)
}
