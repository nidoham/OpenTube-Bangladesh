package com.github.opentube.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.github.extractor.StreamsExtractor
import com.github.libretube.api.obj.Streams
import com.github.opentube.PlayerActivity
import com.github.opentube.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class PlayerService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
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
        Log.d(TAG, "Service onStartCommand called with action: ${intent?.action}")

        startForeground(NOTIFICATION_ID, createInitialNotification())

        if (intent?.action == ACTION_PLAY_YOUTUBE_URLS) {
            val youtubeUrls = intent.getStringArrayListExtra(EXTRA_YOUTUBE_URLS)
            val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)

            if (youtubeUrls != null && youtubeUrls.isNotEmpty()) {
                Log.d(TAG, "Processing ${youtubeUrls.size} YouTube URLs")
                processYoutubeUrls(youtubeUrls, startIndex)
            } else {
                Log.w(TAG, "No YouTube URLs provided")
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Playback controls and status"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createInitialNotification(): Notification {
        val sessionActivityIntent = Intent(this, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenTube")
            .setContentText("Loading video...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    private fun initializePlayer() {
        Log.d(TAG, "Initializing player")

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val sessionActivityIntent = Intent(this, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player!!)
            .setCallback(MediaSessionCallback())
            .setSessionActivity(pendingIntent)
            .build()

        Log.d(TAG, "Player and MediaSession initialized successfully")
    }

    private fun processYoutubeUrls(youtubeUrls: List<String>, startIndex: Int) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Starting video extraction for ${youtubeUrls.size} URLs")
                val mediaItems = mutableListOf<MediaItem>()

                for ((index, url) in youtubeUrls.withIndex()) {
                    try {
                        Log.d(TAG, "Extracting video info for URL ${index + 1}/${youtubeUrls.size}: $url")

                        val videoInfo = withContext(Dispatchers.IO) {
                            videoInfoExtractor.fetchStreams(url)
                        }

                        Log.d(TAG, "Successfully extracted: ${videoInfo.title}")

                        val mediaItem = createMediaItemFromVideoInfo(videoInfo)
                        if (mediaItem != null) {
                            mediaItems.add(mediaItem)
                            Log.d(TAG, "MediaItem created successfully")
                        } else {
                            Log.w(TAG, "Failed to create MediaItem - no valid stream URL")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error extracting video info for URL: $url", e)
                    }
                }

                if (mediaItems.isNotEmpty()) {
                    Log.d(TAG, "Setting ${mediaItems.size} media items to player")
                    player?.apply {
                        setMediaItems(mediaItems, startIndex.coerceIn(0, mediaItems.size - 1), 0)
                        prepare()
                        playWhenReady = true
                    }
                    Log.d(TAG, "Playback started successfully")
                } else {
                    Log.e(TAG, "No valid media items to play, stopping service")
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error during video processing", e)
                stopSelf()
            }
        }
    }

    private fun createMediaItemFromVideoInfo(videoInfo: Streams): MediaItem? {
        val streamUrl = selectBestStreamUrl(videoInfo)

        if (streamUrl == null) {
            Log.w(TAG, "No valid stream URL found for video: ${videoInfo.title}")
            return null
        }

        Log.d(TAG, "Selected stream URL: $streamUrl")

        val formattedSubscriberCount = formatSubscriberCount(videoInfo.uploaderSubscriberCount)
        val additionalInfo = "${videoInfo.views}|${videoInfo.uploaded}|$formattedSubscriberCount"

        val thumbnailUri = videoInfo.thumbnailUrl.toUri()
        val channelAvatar = videoInfo.uploaderAvatar

        val metadata = MediaMetadata.Builder()
            .setTitle(videoInfo.title)
            .setArtist(videoInfo.uploader)
            .setDescription(additionalInfo)
            .setDisplayTitle(videoInfo.title)
            .setArtworkUri(thumbnailUri)
            .build()

        return MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun selectBestStreamUrl(videoInfo: Streams): String? {
        Log.d(TAG, "Available video streams: ${videoInfo.videoStreams.size}")
        Log.d(TAG, "Available audio streams: ${videoInfo.audioStreams.size}")

        // Try to get the best video stream
        val videoStream = videoInfo.videoStreams.firstOrNull()
        if (videoStream?.url != null && videoStream.url!!.isNotEmpty()) {
            Log.d(TAG, "Selected video stream: ${videoStream.quality} - ${videoStream.format}")
            return videoStream.url
        }

        // Fallback to audio stream
        val audioStream = videoInfo.audioStreams.firstOrNull()
        if (audioStream?.url != null && audioStream.url!!.isNotEmpty()) {
            Log.d(TAG, "Selected audio stream: ${audioStream.quality} - ${audioStream.format}")
            return audioStream.url
        }

        // Try HLS or DASH as last resort
        if (!videoInfo.hls.isNullOrEmpty()) {
            Log.d(TAG, "Selected HLS stream")
            return videoInfo.hls
        }

        if (!videoInfo.dash.isNullOrEmpty()) {
            Log.d(TAG, "Selected DASH stream")
            return videoInfo.dash
        }

        Log.w(TAG, "No valid streams found")
        return null
    }

    private fun formatSubscriberCount(count: Long): String {
        return when {
            count < 0 -> ""
            count < 1000 -> "$count subscribers"
            count < 1_000_000 -> "${count / 1000}K subscribers"
            count < 1_000_000_000 -> String.format("%.1fM subscribers", count / 1_000_000.0)
            else -> String.format("%.1fB subscribers", count / 1_000_000_000.0)
        }
    }

    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): com.google.common.util.concurrent.ListenableFuture<MutableList<MediaItem>> {
            val updatedMediaItems = mediaItems.map { item ->
                item.buildUpon()
                    .setMediaMetadata(item.mediaMetadata)
                    .build()
            }.toMutableList()
            return super.onAddMediaItems(mediaSession, controller, updatedMediaItems)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = player
        if (player != null && (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED)) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy called")

        serviceScope.cancel()

        mediaSession?.run {
            release()
            mediaSession = null
        }

        player?.run {
            release()
            player = null
        }

        super.onDestroy()
    }
}