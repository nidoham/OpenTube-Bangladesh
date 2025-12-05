package com.github.extractor

import com.github.libretube.api.obj.ChapterSegment
import com.github.libretube.api.obj.PipedStream
import com.github.libretube.api.obj.PreviewFrames
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.api.obj.Streams
import com.github.libretube.api.obj.Subtitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Extractor class for fetching stream data using NewPipe Extractor
 */
class StreamsExtractor {

    /**
     * Fetches stream information for a given video URL or ID
     * @param videoUrl The URL or ID of the video to fetch
     * @return Streams object containing all video information
     */
    suspend fun fetchStreams(videoUrl: String): Streams = withContext(Dispatchers.IO) {
        val service = ServiceList.YouTube
        val streamInfo = StreamInfo.getInfo(service, videoUrl)
        return@withContext streamInfo.toStreams()
    }

    /**
     * Checks if a video is available
     * @param videoUrl The URL or ID to check
     * @return true if available, false otherwise
     */
    suspend fun isVideoAvailable(videoUrl: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val service = ServiceList.YouTube
            StreamInfo.getInfo(service, videoUrl)
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Converts NewPipe StreamInfo to LibreTube Streams object
 */
private fun StreamInfo.toStreams(): Streams {
    return Streams(
        title = name ?: "",
        description = description?.content ?: "",
        uploadTimestamp = uploadDate?.offsetDateTime()?.toInstant(),
        uploaded = uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli(),
        uploader = uploaderName ?: "",
        uploaderUrl = uploaderUrl ?: "",
        uploaderAvatar = thumbnails.firstOrNull()?.url,
        thumbnailUrl = thumbnails.firstOrNull()?.url ?: "",
        category = category ?: "",
        license = licence ?: "YouTube licence",
        visibility = "public",
        tags = tags ?: emptyList(),
        metaInfo = emptyList(),
        hls = hlsUrl,
        dash = dashMpdUrl,
        uploaderVerified = false,
        duration = duration,
        views = viewCount,
        likes = likeCount,
        dislikes = dislikeCount,
        audioStreams = audioStreams ?: emptyList(),
        videoStreams = videoStreams?: emptyList(),
        relatedStreams = relatedItems?.filterIsInstance<StreamInfoItem>()?.map { it.toStreamItem() } ?: emptyList(),
        subtitles = subtitles?.map { it.toSubtitle() } ?: emptyList(),
        livestream = streamType?.name == "LIVE_STREAM",
        proxyUrl = null,
        chapters = emptyList(),
        uploaderSubscriberCount = uploaderSubscriberCount,
        previewFrames = emptyList()
    )
}

/**
 * Converts NewPipe VideoStream to PipedStream
 */
private fun VideoStream.toPipedStream(): PipedStream {
    return PipedStream(
        url = content,
        format = format?.getName(),
        quality = resolution,
        mimeType = format?.mimeType,
        codec = codec,
        videoOnly = isVideoOnly,
        bitrate = bitrate,
        width = width,
        height = height,
        fps = fps,
        contentLength = -1
    )
}

/**
 * Converts NewPipe AudioStream to PipedStream
 */
private fun AudioStream.toPipedStream(): PipedStream {
    return PipedStream(
        url = content,
        format = format?.getName(),
        quality = bitrate.toString() + " kbps",
        mimeType = format?.mimeType,
        codec = codec,
        videoOnly = false,
        bitrate = bitrate,
        audioTrackName = audioTrackName,
        audioTrackId = audioTrackId,
        audioTrackType = audioTrackType?.name,
        audioTrackLocale = audioLocale?.toLanguageTag(),
        contentLength = -1
    )
}

/**
 * Converts NewPipe StreamInfoItem to StreamItem
 */
private fun StreamInfoItem.toStreamItem(): StreamItem {
    return StreamItem(
        url = url,
        type = StreamItem.TYPE_STREAM,
        title = name,
        thumbnail = thumbnails.firstOrNull()?.url,
        uploaderName = uploaderName,
        uploaderUrl = uploaderUrl,
        uploaderAvatar = null,
        uploadedDate = uploadDate?.offsetDateTime()?.toString(),
        duration = duration,
        views = viewCount,
        uploaderVerified = false,
        uploaded = uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: 0,
        shortDescription = shortDescription,
        isShort = isShortFormContent
    )
}

/**
 * Converts NewPipe Subtitle to LibreTube Subtitle
 */
private fun org.schabi.newpipe.extractor.stream.SubtitlesStream.toSubtitle(): Subtitle {
    return Subtitle(
        url = content,
        mimeType = format?.mimeType,
        name = displayLanguageName,
        code = languageTag,
        autoGenerated = isAutoGenerated
    )
}

/**
 * Converts NewPipe StreamSegment to ChapterSegment
 */
private fun org.schabi.newpipe.extractor.stream.StreamSegment.toChapterSegment(): ChapterSegment {
    return ChapterSegment(
        title = title ?: "",
        image = previewUrl ?: "",
        start = startTimeSeconds.toLong()
    )
}

/**
 * Converts NewPipe Frameset to PreviewFrames
 */
private fun org.schabi.newpipe.extractor.stream.Frameset.toPreviewFrames(): PreviewFrames {
    return PreviewFrames(
        urls = urls ?: emptyList(),
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        totalCount = totalCount,
        durationPerFrame = durationPerFrame.toLong(),
        framesPerPageX = framesPerPageX,
        framesPerPageY = framesPerPageY
    )
}