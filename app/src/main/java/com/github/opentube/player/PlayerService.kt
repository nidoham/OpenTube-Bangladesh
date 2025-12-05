package com.github.opentube.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.github.extractor.StreamsExtractor
import com.github.libretube.api.obj.Streams
import com.github.opentube.PlayerActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class PlayerService : MediaLibraryService() {

    private var player: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val videoInfoExtractor = StreamsExtractor()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "PlayerService"
        const val ACTION_PLAY_YOUTUBE_URLS = "com.github.opentube.action.PLAY_YOUTUBE_URLS"
        const val EXTRA_YOUTUBE_URLS = "extra_youtube_urls"
        const val EXTRA_START_INDEX = "extra_start_index"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "opentube_playback_channel"
        private const val CHANNEL_NAME = "OpenTube Playback"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate called")
        createNotificationChannel()
        initializePlayer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        if (intent?.action == ACTION_PLAY_YOUTUBE_URLS) {
            val youtubeUrls = intent.getStringArrayListExtra(EXTRA_YOUTUBE_URLS)
            val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
            youtubeUrls?.let { processYoutubeUrls(it, startIndex) }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val sessionActivityIntent = Intent(this, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            player!!,
            object : MediaLibrarySession.Callback {
                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    // Handle custom commands here if needed
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
        )
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    private fun processYoutubeUrls(youtubeUrls: List<String>, startIndex: Int) {
        serviceScope.launch {
            try {
                val mediaItems = mutableListOf<MediaItem>()

                youtubeUrls.forEachIndexed { index, url ->
                    try {
                        val videoInfo = withContext(Dispatchers.IO) {
                            videoInfoExtractor.fetchStreams(url)
                        }
                        createMediaItemFromVideoInfo(videoInfo, index)?.let {
                            mediaItems.add(it)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing $url", e)
                    }
                }

                if (mediaItems.isNotEmpty()) {
                    player?.apply {
                        setMediaItems(mediaItems, startIndex.coerceAtMost(mediaItems.lastIndex), 0L)
                        prepare()
                        playWhenReady = true
                    }
                } else {
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Processing failed", e)
                stopSelf()
            }
        }
    }

    private fun createMediaItemFromVideoInfo(videoInfo: Streams, index: Int): MediaItem? {
        val streamUrl = selectBestStreamUrl(videoInfo) ?: return null

        val formattedSubs = formatSubscriberCount(videoInfo.uploaderSubscriberCount)
        val info = "${videoInfo.views}|${videoInfo.uploaded}|$formattedSubs"

        val metadata = MediaMetadata.Builder()
            .setTitle(videoInfo.title)
            .setArtist(videoInfo.uploader)
            .setDescription(info)
            .setArtworkUri(Uri.parse(videoInfo.thumbnailUrl))
            .build()

        return MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaId("video_$index")
            .setMediaMetadata(metadata)
            .build()
    }

    private fun selectBestStreamUrl(streams: Streams): String? {
        return streams.videoStreams.firstOrNull()?.url
            ?: streams.audioStreams.firstOrNull()?.url
            ?: streams.hls
            ?: streams.dash
    }

    private fun formatSubscriberCount(count: Long): String {
        return when {
            count < 1000 -> "$count subscribers"
            count < 1_000_000 -> "${count / 1000}K subscribers"
            count < 1_000_000_000L -> "%.1fM subscribers".format(count / 1_000_000.0)
            else -> "%.1fB subscribers".format(count / 1_000_000_000.0)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        player?.let {
            if (!it.playWhenReady || it.mediaItemCount == 0 || it.playbackState == Player.STATE_ENDED) {
                stopSelf()
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        serviceScope.cancel()
        mediaLibrarySession?.release()
        player?.release()
        super.onDestroy()
    }
}
