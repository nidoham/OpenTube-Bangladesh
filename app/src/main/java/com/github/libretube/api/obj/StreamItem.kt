package com.github.libretube.api.obj

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class StreamItem(
    val url: String? = null,
    val type: String? = null,
    var title: String? = null,
    var thumbnail: String? = null,
    val uploaderName: String? = null,
    val uploaderUrl: String? = null,
    val uploaderAvatar: String? = null,
    val uploadedDate: String? = null,
    val duration: Long? = null,
    val views: Long? = null,
    val uploaderVerified: Boolean? = null,
    val uploaded: Long = 0,
    val shortDescription: String? = null,
    val isShort: Boolean = false
) : Parcelable {
    val isLive get() = !isShort && ((duration == null) || (duration <= 0L))
    val isUpcoming get() = uploaded > System.currentTimeMillis()

    companion object {
        const val TYPE_STREAM = "stream"
        const val TYPE_CHANNEL = "channel"
        const val TYPE_PLAYLIST = "playlist"
    }
}