package com.github.extractor.image

import org.schabi.newpipe.extractor.Image

class ImageBitmap {

    companion object {
        // ⭐ স্ট্যাটিক ফাংশন, সরাসরি ImageBitmap.thumbnails(...) কল করা যাবে
        @JvmStatic
        fun thumbnails(images: List<Image>?): String {
            if (images.isNullOrEmpty()) return ""

            var bestScore: Long = -1
            var bestUrl: String = ""

            for (img in images) {
                val width = img.width
                val height = img.height

                // ⭐ width * height = resolution স্কোর (Int → Long)
                val score = width.toLong() * height.toLong()

                if (score > bestScore) {
                    bestScore = score
                    bestUrl = img.url ?: ""
                }
            }

            return bestUrl
        }
    }
}