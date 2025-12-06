package com.github.extractor.stream

import org.schabi.newpipe.extractor.Image
import java.io.Serializable

/**
 * Serializable version of VideoMetadata for passing through Bundles.
 * Converts Image lists to simple String URLs for safe serialization.
 */
data class VideoMetadata(
    val name: String,
    val thumbnailUrl: String?,
    val uploaderName: String,
    val uploaderAvatarUrl: String?,
    val uploaderSubscriberCount: Long,
    val viewCount: Long,
    val uploadDate: String?
) : Serializable {

    companion object {
        private const val serialVersionUID = 1L

        fun from(item: com.github.extractor.stream.StreamInfo): VideoMetadata {
            return VideoMetadata(
                name = item.name ?: "",
                thumbnailUrl = item.thumbnails?.firstOrNull()?.url,
                uploaderName = item.uploaderName ?: "",
                uploaderAvatarUrl = item.uploaderAvatars?.firstOrNull()?.url,
                uploaderSubscriberCount = item.uploaderSubscriberCount ?: -1L,
                viewCount = item.viewCount ?: -1L,
                uploadDate = item.uploadDate?.date()?.toString()
            )
        }
    }

    // Keep backward compatibility with existing code
    fun thumbnailsString(): String? = thumbnailUrl
    fun uploaderAvatarsString(): String? = uploaderAvatarUrl
}