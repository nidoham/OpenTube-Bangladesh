package com.github.extractor.stream

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamType

data class StreamInfoItem(
    val serviceId: Int,
    val name: String,
    val duration: Long,
    val url: String,
    val thumbnails: List<Image>,
    val streamType: StreamType,
    val uploaderName: String,
    val uploaderAvatars: List<Image>,
    val uploaderUrl: String,
    val viewCount: Long,
    val textualUploadDate: String,
    val uploadDate: DateWrapper?,
    val shortFromContent: Boolean
) {
    companion object {
        // StreamInfoItem → StreamInfoItem (NewPipe extractor to local model)
        fun from(item: org.schabi.newpipe.extractor.stream.StreamInfoItem): com.github.extractor.stream.StreamInfoItem {
            return StreamInfoItem(
                serviceId = item.serviceId,
                name = item.name,
                duration = item.duration,
                url = item.url,
                thumbnails = item.thumbnails,
                streamType = item.streamType,
                uploaderName = item.uploaderName,
                uploaderAvatars = item.uploaderAvatars ?: emptyList(), // Safe fallback
                uploaderUrl = item.uploaderUrl ?: "",
                viewCount = item.viewCount,
                textualUploadDate = item.textualUploadDate ?: "",
                uploadDate = item.uploadDate,
                shortFromContent = item.isShortFormContent
            )
        }
    }
}
