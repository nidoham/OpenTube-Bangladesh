package com.github.opentube.player.gesture

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/**
 * Handles video player gestures including double-tap seek, brightness/volume drag, and swipe detection.
 * Designed for Jetpack Compose video player with intuitive touch controls.
 */
class GestureHandler(
    private val onSeekBackward: () -> Unit,
    private val onSeekForward: () -> Unit,
    private val onBrightnessChange: (Float) -> Unit,
    private val onVolumeChange: (Float) -> Unit,
    private val onShowControls: (() -> Unit)? = null
) {

    companion object {
        /**
         * Sensitivity for brightness/volume drag gestures (pixels per unit change)
         */
        private const val DRAG_SENSITIVITY = 3000f

        /**
         * Default seek amount for double-tap (10 seconds)
         */
        private const val SEEK_AMOUNT_MS = 10000L

        /**
         * Minimum drag distance to register as gesture (pixels)
         */
        private const val MIN_DRAG_DISTANCE = 20f

        /**
         * Horizontal/vertical threshold for drag direction detection
         */
        private const val DRAG_DIRECTION_THRESHOLD = 0.3f
    }

    /**
     * Handles double-tap gesture for seeking.
     * Left side = backward 10s, right side = forward 10s.
     */
    fun handleDoubleTap(offset: Offset, screenWidth: Float) {
        val normalizedX = offset.x / screenWidth
        when {
            normalizedX < 0.33f -> onSeekBackward()
            normalizedX > 0.67f -> onSeekForward()
            else -> onShowControls?.invoke()
        }
    }

    /**
     * Handles vertical drag on left side for brightness adjustment.
     * @param dragAmount Y-axis drag distance (positive = down, negative = up)
     * @param screenHeight Screen height for normalization
     * @return True if handled as brightness gesture
     */
    fun handleBrightnessDrag(dragAmount: Float, screenHeight: Float): Boolean {
        val normalizedDrag = dragAmount / screenHeight
        val sensitivity = DRAG_SENSITIVITY / screenHeight
        val delta = -(normalizedDrag / sensitivity).coerceIn(-0.1f, 0.1f)
        onBrightnessChange(delta)
        return true
    }

    /**
     * Handles vertical drag on right side for volume adjustment.
     * @param dragAmount Y-axis drag distance (positive = down, negative = up)
     * @param screenHeight Screen height for normalization
     * @return True if handled as volume gesture
     */
    fun handleVolumeDrag(dragAmount: Float, screenHeight: Float): Boolean {
        val normalizedDrag = dragAmount / screenHeight
        val sensitivity = DRAG_SENSITIVITY / screenHeight
        val delta = -(normalizedDrag / sensitivity).coerceIn(-0.1f, 0.1f)
        onVolumeChange(delta)
        return true
    }

    /**
     * Determines drag direction and delegates to appropriate handler.
     * Left side = brightness, right side = volume.
     */
    fun handleVerticalDrag(offset: Offset, dragAmount: Float, screenWidth: Float, screenHeight: Float): Boolean {
        return if (offset.x < screenWidth / 2) {
            handleBrightnessDrag(dragAmount, screenHeight)
        } else {
            handleVolumeDrag(dragAmount, screenHeight)
        }
    }

    /**
     * Filters drag gestures based on minimum distance and direction.
     * @return True if drag is valid gesture, false if too short/random
     */
    fun isValidDrag(dragX: Float, dragY: Float, screenWidth: Float, screenHeight: Float): Boolean {
        val distance = abs(dragX) + abs(dragY)
        if (distance < MIN_DRAG_DISTANCE) return false

        val normalizedX = abs(dragX) / screenWidth
        val normalizedY = abs(dragY) / screenHeight
        return normalizedY > normalizedX * DRAG_DIRECTION_THRESHOLD
    }

    /**
     * Gets the configured seek amount in milliseconds.
     */
    fun getSeekAmount(): Long = SEEK_AMOUNT_MS

    /**
     * Performs seek operation with specified amount.
     */
    fun performSeek(isForward: Boolean) {
        if (isForward) onSeekForward() else onSeekBackward()
    }
}