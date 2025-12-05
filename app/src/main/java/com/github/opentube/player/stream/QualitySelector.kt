package com.github.opentube.player.stream

import android.util.Log
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream
import kotlin.math.abs

class QualitySelector(
    videoStreams: List<VideoStream>,
    audioStreams: List<AudioStream>
) {

    private class ProcessedVideo(val stream: VideoStream) {
        val height: Int = stream.height
        val formatPriority: Int = getFormatPriority(stream.format?.name, PREFERRED_VIDEO_FORMATS)
        val isUrlValid: Boolean = isValidUrl(stream.url)

        // Sort Score: Higher height > Better format
        val sortScore: Long = (height.toLong() * 1000) - formatPriority
    }

    private class ProcessedAudio(val stream: AudioStream) {
        val bitrate: Int = stream.averageBitrate
        val formatPriority: Int = getFormatPriority(stream.format?.name, PREFERRED_AUDIO_FORMATS)
        val isUrlValid: Boolean = isValidUrl(stream.url)
    }

    // Pre-process and filter/sort immediately
    private val sortedVideos: List<ProcessedVideo> = videoStreams
        .map { ProcessedVideo(it) }
        .filter { it.isUrlValid }
        .sortedByDescending { it.sortScore }

    private val sortedAudios: List<ProcessedAudio> = audioStreams
        .map { ProcessedAudio(it) }
        .filter { it.isUrlValid }
        .sortedWith(
            compareByDescending<ProcessedAudio> { it.bitrate }
                .thenBy { it.formatPriority }
        )

    init {
        Log.d(TAG, "QualitySelector: ${sortedVideos.size} video streams, ${sortedAudios.size} audio streams")
    }

    // Public API

    enum class QualityProfile(val targetHeight: Int) {
        DATA_SAVER(144),
        LOW(240),
        MEDIUM(480),
        HIGH(1080),
        ULTRA(2160)
    }

    fun getVideo(profile: QualityProfile): VideoStream? =
        getStreamClosestToHeight(profile.targetHeight)

    fun getVideo(targetHeight: Int): VideoStream? =
        getStreamClosestToHeight(targetHeight)

    fun getBestVideo(): VideoStream? = sortedVideos.firstOrNull()?.stream
    fun getBestAudio(): AudioStream? = sortedAudios.firstOrNull()?.stream

    fun getDataSaverAudio(): AudioStream? =
        sortedAudios.filter { it.bitrate > AudioStream.UNKNOWN_BITRATE }
            .minByOrNull { it.bitrate }?.stream

    // Core Selection Logic

    private fun getStreamClosestToHeight(target: Int): VideoStream? {
        if (sortedVideos.isEmpty()) return null

        return sortedVideos.minByOrNull { video ->
            val distance = abs(video.height - target)
            val penalty = video.formatPriority
            (distance * 100) + penalty
        }?.stream
    }

    // Info Accessors

    fun getAvailableResolutions(): List<String> =
        sortedVideos.map { it.stream.resolution }.distinct()

    fun hasVideo() = sortedVideos.isNotEmpty()
    fun hasAudio() = sortedAudios.isNotEmpty()

    // Companion Object

    companion object {
        private const val TAG = "QualitySelector"

        private val PREFERRED_VIDEO_FORMATS = arrayOf("mpeg-4", "mp4", "webm", "3gp")
        private val PREFERRED_AUDIO_FORMATS = arrayOf("m4a", "mp3", "webm", "opus")

        fun getFormatPriority(format: String?, preferred: Array<String>): Int {
            val safeFormat = format?.lowercase() ?: return 999
            return preferred.indexOfFirst { safeFormat.contains(it) }.takeIf { it >= 0 } ?: 999
        }

        fun isValidUrl(url: String?): Boolean {
            return !url.isNullOrBlank() && url.startsWith("http")
        }
    }
}
