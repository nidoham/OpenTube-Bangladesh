package com.github.libretube.api.obj

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.time.Instant

@Serializable
@Parcelize
data class Streams(
    var title: String,
    val description: String,

    @SerialName("uploadDate")
    @IgnoredOnParcel
    val uploadTimestamp: Instant? = null,
    val uploaded: Long? = null,

    val uploader: String,
    val uploaderUrl: String,
    val uploaderAvatar: String? = null,
    var thumbnailUrl: String,
    val category: String,
    val license: String = "YouTube licence",
    val visibility: String = "public",
    val tags: List<String> = emptyList(),
    val metaInfo: List<MetaInfo> = emptyList(),
    val hls: String? = null,
    val dash: String? = null,
    val uploaderVerified: Boolean,
    val duration: Long,
    val views: Long = 0,
    val likes: Long = 0,
    val dislikes: Long = 0,
    val audioStreams: List<AudioStream> = emptyList(),
    val videoStreams: List<VideoStream> = emptyList(),
    var relatedStreams: List<StreamItem> = emptyList(),
    val subtitles: List<Subtitle> = emptyList(),
    val livestream: Boolean = false,
    val proxyUrl: String? = null,
    val chapters: List<ChapterSegment> = emptyList(),
    val uploaderSubscriberCount: Long = 0,
    val previewFrames: List<PreviewFrames> = emptyList()
): Parcelable {
    @IgnoredOnParcel
    val isLive = livestream || duration <= 0

    companion object {
        const val CATEGORY_MUSIC = "Music"
    }
}