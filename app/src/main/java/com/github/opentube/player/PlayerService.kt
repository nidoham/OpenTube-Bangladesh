package com.github.opentube.player

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
import com.github.extractor.stream.StreamInfo as LocalStreamInfo
import com.github.extractor.stream.VideoMetadata
import com.github.opentube.PlayerActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo

@OptIn(UnstableApi::class)
class PlayerService : MediaLibraryService() {

    private var player: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "PlayerService"
        const val ACTION_PLAY_YOUTUBE_URLS = "com.github.opentube.action.PLAY_YOUTUBE_URLS"
        const val EXTRA_YOUTUBE_URLS = "extra_youtube_urls"
        const val EXTRA_START_INDEX = "extra_start_index"
        const val EXTRA_METADATA_OBJECT = "extra_metadata_object"

        private const val CHANNEL_ID = "opentube_playback_channel"
        private const val CHANNEL_NAME = "OpenTube Playback"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializePlayer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
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
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Playback controls"
                setShowBadge(false)
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
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
        ).setSessionActivity(pendingIntent).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    private fun processYoutubeUrls(youtubeUrls: List<String>, startIndex: Int) {
        serviceScope.launch {
            try {
                val mediaItems = mutableListOf<MediaItem>()
                withContext(Dispatchers.IO) {
                    youtubeUrls.forEachIndexed { index, url ->
                        try {
                            val streamInfo = StreamInfo.getInfo(url)
                            createMediaItemFromStreamInfo(streamInfo, index)?.let {
                                mediaItems.add(it)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing $url", e)
                        }
                    }
                }

                if (mediaItems.isNotEmpty()) {
                    player?.apply {
                        setMediaItems(
                            mediaItems,
                            startIndex.coerceAtMost(mediaItems.lastIndex),
                            0L
                        )
                        prepare()
                        playWhenReady = true
                    }
                } else {
                    Log.w(TAG, "No valid media items, stopping service")
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing YouTube URLs", e)
                stopSelf()
            }
        }
    }

    private fun createMediaItemFromStreamInfo(
        streamInfo: StreamInfo,
        index: Int
    ): MediaItem? {
        val localInfo = LocalStreamInfo.from(streamInfo)
        val videoMetadata = VideoMetadata.from(localInfo)
        val streamUrl = selectBestStreamUrl(localInfo)

        if (streamUrl == null) {
            Log.w(TAG, "No valid stream URL found for ${videoMetadata.name}")
            return null
        }

        // Create extras bundle with serializable metadata
        val extras = Bundle().apply {
            putSerializable(EXTRA_METADATA_OBJECT, videoMetadata)
        }

        val infoText = buildString {
            if (videoMetadata.viewCount >= 0) {
                append("${videoMetadata.viewCount} views")
            }
            if (!videoMetadata.uploadDate.isNullOrBlank()) {
                if (isNotEmpty()) append(" • ")
                append(videoMetadata.uploadDate)
            }
        }

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(videoMetadata.name)
            .setArtist(videoMetadata.uploaderName)
            .setDescription(infoText)
            .setExtras(extras)
            .apply {
                videoMetadata.thumbnailsString()?.let { artworkUrl ->
                    if (artworkUrl.isNotBlank()) {
                        try {
                            setArtworkUri(Uri.parse(artworkUrl))
                        } catch (e: Exception) {
                            Log.w(TAG, "Invalid artwork URL: $artworkUrl", e)
                        }
                    }
                }
            }
            .build()

        return MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaId(localInfo.id.ifBlank { "video_$index" })
            .setMediaMetadata(mediaMetadata)
            .build()
    }

    private fun selectBestStreamUrl(streamInfo: LocalStreamInfo): String? {
        return streamInfo.bestVideoUrl()
            ?: streamInfo.dashMpdUrl
            ?: streamInfo.hlsUrl
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        player?.let {
            if (!it.playWhenReady || it.playbackState == Player.STATE_ENDED) {
                stopSelf()
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        player?.release()
        player = null
        super.onDestroy()
    }
}