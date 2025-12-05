package com.github.extractor.stream

import com.github.extractor.image.ImageBitmap
import com.github.libretube.api.obj.Streams
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class StreamInfo(
    val id: String,
    val title: String,
    val uploaderName: String,
    val uploaderAvatars: List<Image>?,
    val thumbnails: List<Image>?,
    val duration: Long,
    val viewCount: Long,
    val uploadDate: String,
    val videoUrl: String
) {
    companion object {
        // ⭐ StreamInfoItem → StreamInfo কনভার্ট
        fun from(item: StreamInfoItem): StreamInfo {
            return StreamInfo(
                id = item.url ?: "",
                title = item.name ?: "No Title",
                uploaderName = item.uploaderName ?: "Unknown",
                uploaderAvatars = item.uploaderAvatars,
                thumbnails = item.thumbnails,
                duration = item.duration,
                viewCount = item.viewCount,
                uploadDate = try { item.textualUploadDate ?: "" } catch (e: Exception) { "" },
                videoUrl = item.url ?: ""
            )
        }

        fun from(item: Streams): StreamInfo {
            return StreamInfo(
                id = "youtube",
                title = item.title ?: "No Title",
                uploaderName = item.uploader ?: "Unknown",
                uploaderAvatars = item.uploaderAvatar as List<Image>?,
                thumbnails = emptyList(),
                duration = item.duration,
                viewCount = item.views,
                uploadDate = "",
                videoUrl = item.proxyUrl ?: ""
            )
        }
    }

    // ⭐ ভিডিওর সেরা থাম্বনেইল URL
    fun bestThumbnailUrl(): String? = ImageBitmap.thumbnails(thumbnails)

    // ⭐ চ্যানেলের সেরা অ্যাভাটার URL
    fun bestUploaderAvatarUrl(): String? = ImageBitmap.thumbnails(uploaderAvatars)

    // ⭐ যেকোনো ইমেজ লিস্ট থেকে সেরা URL
    fun bestImageUrl(list: List<Image>?): String? = ImageBitmap.thumbnails(list)
}
