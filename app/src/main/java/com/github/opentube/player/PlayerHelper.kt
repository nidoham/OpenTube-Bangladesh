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
     * Initiates playback of a single YouTube video URL by starting the PlayerService with
     * the appropriate configuration. This method handles service startup and passes the
     * necessary data for video extraction and playback initialization.
     *
     * @param context the application or activity context required to start the service
     * @param youtubeUrl the YouTube video URL to extract and play
     */

    fun playVideo(context: Context, queue: PlayQueue){
        val type: QueueType = queue.queueType
        val items: List<PlayQueueItem> = queue.items

        if (type.equals(QueueType.NORMAL)) {
            val item: PlayQueueItem = items.get(0)
            playSingleVideo(context, item.videoUrl)
        } else {
            val urls = ArrayList<String>()
            val startIndex: Int = 0
            for (i in items.indices){
                val item: PlayQueueItem = items.get(i)
                urls.add(item.videoUrl)
            }
            playMultipleVideos(context, urls, startIndex)
        }
    }


    fun playSingleVideo(context: Context, youtubeUrl: String) {
        playMultipleVideos(context, listOf(youtubeUrl), 0)
    }

    /**
     * Initiates playback of multiple YouTube videos as a playlist by starting the PlayerService
     * with the complete list of URLs and the desired starting position. The service will process
     * each URL sequentially to extract stream information and build a playable queue.
     *
     * This method is ideal for playlist playback scenarios where users want to queue multiple
     * videos for continuous playback without manual intervention between videos.
     *
     * @param context the application or activity context required to start the service
     * @param youtubeUrls list of YouTube video URLs to extract and play in sequence
     * @param startIndex the zero-based index indicating which video to start playing first
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
     * Stops the PlayerService and ends all active playback sessions. This method should be
     * called when the user explicitly wants to stop playback and terminate the background
     * service completely.
     *
     * @param context the application or activity context required to stop the service
     */
    fun stopPlayback(context: Context) {
        val intent = Intent(context, PlayerService::class.java)
        context.stopService(intent)
    }
}

/**
 * Extension function for Context to provide a more idiomatic way to play YouTube videos
 * directly from any context instance. This simplifies the calling code and provides a
 * cleaner API surface for initiating playback.
 */
fun Context.playYoutubeVideo(url: String) {
    PlayerHelper.playSingleVideo(this, url)
}

/**
 * Extension function for Context to play multiple YouTube videos as a playlist with an
 * optional starting index. This provides an intuitive way to start playlist playback
 * from any context without directly interacting with the PlayerHelper object.
 */
fun Context.playYoutubePlaylist(urls: List<String>, startIndex: Int = 0) {
    PlayerHelper.playMultipleVideos(this, urls, startIndex)
}