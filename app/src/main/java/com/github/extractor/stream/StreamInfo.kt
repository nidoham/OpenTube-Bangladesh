package com.github.extractor.stream

import com.github.extractor.image.ImageBitmap
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.MetaInfo
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.Frameset
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamSegment
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.Locale

data class StreamInfo(
    val id: String,
    val serviceId: Int,
    val originalUrl: String,
    val name: String,
    val description: Description?,
    val duration: Long,
    val url: String,
    val dashMpdUrl: String?,
    val hlsUrl: String?,
    val ageLimit: Int,
    val host: String,
    val thumbnails: List<Image>,
    val audioStreams: List<AudioStream>,
    val videoStreams: List<VideoStream>,
    val videoOnlyStreams: List<VideoStream>,
    val streamType: StreamType,
    val uploaderName: String,
    val uploaderAvatars: List<Image>?,
    val uploaderUrl: String?,
    val uploaderVerified: Boolean,
    val uploaderSubscriberCount: Long,
    val subChannelNames: String?,
    val subChannelAvatars: List<Image>?,
    val subChannelUrl: String?,
    val viewCount: Long,
    val likeCount: Long,
    val dislikeCount: Long,
    val relatedItems: List<InfoItem>,
    val relatedStreams: List<InfoItem>,
    val textualUploadDate: String?,
    val uploadDate: DateWrapper?,
    val subtitles: List<SubtitlesStream>,
    val category: String?,
    val languageInfo: Locale?,
    val license: String?,
    val metaInfo: List<MetaInfo>,
    val previewFrames: List<Frameset>,
    val privacy: StreamExtractor.Privacy,
    val shortFromContent: Boolean,
    val startPosition: Long,
    val streamSegments: List<StreamSegment>,
    val supportInfo: String?,
    val tags: List<String>
) {
    companion object {
        fun from(item: org.schabi.newpipe.extractor.stream.StreamInfo): StreamInfo {
            return StreamInfo(
                id = item.id,
                serviceId = item.serviceId,
                originalUrl = item.url,
                name = item.name ?: "",
                description = item.description,
                duration = item.duration,
                url = item.url,
                dashMpdUrl = item.dashMpdUrl,
                hlsUrl = item.hlsUrl,
                ageLimit = item.ageLimit,
                host = item.host ?: "",
                thumbnails = item.thumbnails ?: emptyList(),
                audioStreams = item.audioStreams ?: emptyList(),
                videoStreams = item.videoStreams ?: emptyList(),
                videoOnlyStreams = item.videoOnlyStreams ?: emptyList(),
                streamType = item.streamType,
                uploaderName = item.uploaderName ?: "",
                uploaderAvatars = item.uploaderAvatars,
                uploaderUrl = item.uploaderUrl,
                uploaderVerified = item.isUploaderVerified,
                uploaderSubscriberCount = item.uploaderSubscriberCount,
                subChannelNames = item.subChannelName,
                subChannelAvatars = item.subChannelAvatars,
                subChannelUrl = item.uploaderUrl, // Same as uploaderUrl for consistency
                viewCount = item.viewCount,
                likeCount = item.likeCount ?: 0L,
                dislikeCount = item.dislikeCount ?: 0L,
                relatedItems = item.relatedItems ?: emptyList(),
                relatedStreams = item.relatedStreams ?: emptyList(),
                textualUploadDate = item.textualUploadDate,
                uploadDate = item.uploadDate,
                subtitles = item.subtitles ?: emptyList(),
                category = item.category,
                languageInfo = item.languageInfo,
                license = item.licence,
                metaInfo = item.metaInfo ?: emptyList(),
                previewFrames = item.previewFrames ?: emptyList(),
                privacy = item.privacy,
                shortFromContent = item.isShortFormContent,
                startPosition = item.startPosition,
                streamSegments = item.streamSegments ?: emptyList(),
                supportInfo = item.supportInfo,
                tags = item.tags ?: emptyList()
            )
        }
    }

    // ⭐ ভিডিওর সেরা থাম্বনেইল URL (সবচেয়ে উচ্চ রেজোলিউশন)
    fun bestThumbnailUrl(): String? = ImageBitmap.thumbnails(thumbnails)

    // ⭐ চ্যানেলের সেরা অ্যাভাটার URL
    fun bestUploaderAvatarUrl(): String? = ImageBitmap.thumbnails(uploaderAvatars)

    // ⭐ যেকোনো ইমেজ লিস্ট থেকে সেরা URL
    fun bestImageUrl(list: List<Image>?): String? = ImageBitmap.thumbnails(list)

    // ⭐ প্রথম উপলব্ধ ভিডিও স্ট্রিম URL (উচ্চ কোয়ালিটি প্রায়োরিটি)
    fun bestVideoUrl(): String? = videoStreams.maxByOrNull { it.height }?.url

    // ⭐ প্রথম উপলব্ধ অডিও স্ট্রিম URL
    fun bestAudioUrl(): String? = audioStreams.maxByOrNull { it.bitrate ?: 0 }?.url
}
