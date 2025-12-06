package com.github.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Thin wrapper around NewPipe StreamInfo
 */
class StreamsExtractor {

    /**
     * Fetch NewPipe StreamInfo for a given URL or ID.
     */
    suspend fun fetchStreamInfo(videoUrl: String): StreamInfo = withContext(Dispatchers.IO) {
        val service = ServiceList.YouTube
        StreamInfo.getInfo(service, videoUrl)
    }

    /**
     * Check if video is available.
     */
    suspend fun isVideoAvailable(videoUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            StreamInfo.getInfo(service, videoUrl)
            true
        } catch (e: Exception) {
            false
        }
    }
}
