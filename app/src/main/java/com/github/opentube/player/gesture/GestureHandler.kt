package com.github.opentube.player.gesture

import androidx.compose.ui.geometry.Offset

class GestureHandler(
    private val onSeekBackward: () -> Unit,
    private val onSeekForward: () -> Unit,
    private val onBrightnessChange: (Float) -> Unit,
    private val onVolumeChange: (Float) -> Unit
) {

    companion object {
        private const val DRAG_SENSITIVITY = 3000f
        private const val SEEK_AMOUNT_MS = 10000L
    }

    fun handleDoubleTap(offset: Offset, screenWidth: Float) {
        if (offset.x < screenWidth / 2) {
            onSeekBackward()
        } else {
            onSeekForward()
        }
    }

    fun handleBrightnessDrag(dragAmount: Float) {
        val delta = -dragAmount / DRAG_SENSITIVITY
        onBrightnessChange(delta)
    }

    fun handleVolumeDrag(dragAmount: Float) {
        val delta = -dragAmount / DRAG_SENSITIVITY
        onVolumeChange(delta)
    }

    fun getSeekAmount(): Long = SEEK_AMOUNT_MS
}