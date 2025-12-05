package com.github.opentube.player.manager

import android.app.Activity
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.opentube.player.state.ControlsState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages player controls UI state including auto-hide, overlays, brightness, and quality selection.
 * Designed for Jetpack Compose video player with gesture-based controls.
 */
class ControlsManager(private val context: Context) : ViewModel() {

    private val _controlsState = mutableStateOf(ControlsState())
    val controlsState: State<ControlsState> = _controlsState

    private var autoHideJob: Job? = null

    companion object {
        private const val AUTO_HIDE_DELAY = 3000L
        private const val OVERLAY_HIDE_DELAY = 500L
        private const val SEEK_OVERLAY_DURATION = 800L
    }

    init {
        initializeBrightness()
    }

    /**
     * Initializes brightness from system settings or window attributes.
     */
    private fun initializeBrightness() {
        val brightness = when (val activity = context as? Activity) {
            null -> 0.5f
            else -> {
                activity.window?.attributes?.screenBrightness?.takeIf { it >= 0f }
                    ?: try {
                        Settings.System.getFloat(
                            context.contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS,
                            128f
                        ) / 255f
                    } catch (e: Exception) {
                        0.5f
                    }
            }
        }
        _controlsState.value = _controlsState.value.copy(brightness = brightness)
    }

    /**
     * Toggles controls visibility and manages auto-hide timer.
     */
    fun toggleControls() {
        val currentState = _controlsState.value
        val newState = !currentState.showControls
        _controlsState.value = currentState.copy(showControls = newState)

        if (newState) {
            scheduleAutoHide()
        } else {
            cancelAutoHide()
        }
    }

    /**
     * Shows controls and starts auto-hide timer.
     */
    fun showControls() {
        val currentState = _controlsState.value
        _controlsState.value = currentState.copy(showControls = true)
        scheduleAutoHide()
    }

    /**
     * Hides controls and cancels auto-hide timer.
     */
    fun hideControls() {
        val currentState = _controlsState.value
        _controlsState.value = currentState.copy(showControls = false)
        cancelAutoHide()
    }

    /**
     * Schedules controls to auto-hide after delay.
     */
    private fun scheduleAutoHide() {
        cancelAutoHide()
        autoHideJob = viewModelScope.launch {
            delay(AUTO_HIDE_DELAY)
            val currentState = _controlsState.value
            if (currentState.showControls) {
                _controlsState.value = currentState.copy(showControls = false)
            }
        }
    }

    /**
     * Cancels auto-hide timer.
     */
    private fun cancelAutoHide() {
        autoHideJob?.cancel()
        autoHideJob = null
    }

    /**
     * Shows seek preview overlay with direction indicator.
     * @param direction Positive for forward, negative for backward seek
     */
    fun showSeekOverlay(direction: Int) {
        val currentState = _controlsState.value
        _controlsState.value = currentState.copy(
            showSeekOverlay = true,
            seekDirection = direction
        )

        viewModelScope.launch {
            delay(SEEK_OVERLAY_DURATION)
            val state = _controlsState.value
            if (state.showSeekOverlay) {
                _controlsState.value = state.copy(showSeekOverlay = false)
            }
        }
    }

    /**
     * Shows volume overlay (hides other controls).
     */
    fun showVolumeOverlay() {
        hideControls()
        val currentState = _controlsState.value
        _controlsState.value = currentState.copy(showVolumeOverlay = true)
    }

    /**
     * Hides volume overlay after delay.
     */
    fun hideVolumeOverlay() {
        viewModelScope.launch {
            delay(OVERLAY_HIDE_DELAY)
            val state = _controlsState.value
            if (state.showVolumeOverlay) {
                _controlsState.value = state.copy(showVolumeOverlay = false)
            }
        }
    }

    /**
     * Shows brightness overlay (hides other controls).
     */
    fun showBrightnessOverlay() {
        hideControls()
        val currentState = _controlsState.value
        _controlsState.value = currentState.copy(showBrightnessOverlay = true)
    }

    /**
     * Hides brightness overlay after delay.
     */
    fun hideBrightnessOverlay() {
        viewModelScope.launch {
            delay(OVERLAY_HIDE_DELAY)
            val state = _controlsState.value
            if (state.showBrightnessOverlay) {
                _controlsState.value = state.copy(showBrightnessOverlay = false)
            }
        }
    }

    /**
     * Sets brightness value and applies to window.
     * @param brightness Value between 0.0f and 1.0f
     */
    fun setBrightness(brightness: Float) {
        val clamped = brightness.coerceIn(0f, 1f)
        val currentState = _controlsState.value
        _controlsState.value = currentState.copy(brightness = clamped)

        val activity = context as? Activity
        activity?.window?.let { window ->
            val layoutParams = window.attributes
            layoutParams.screenBrightness = clamped
            window.attributes = layoutParams
        }
    }

    /**
     * Adjusts brightness by delta amount.
     * @param delta Positive or negative adjustment value
     */
    fun adjustBrightness(delta: Float) {
        val currentBrightness = _controlsState.value.brightness
        setBrightness(currentBrightness + delta)
    }

    /**
     * Shows quality selection dialog.
     */
    fun showQualityDialog() {
        val currentState = _controlsState.value
        _controlsState.value = currentState.copy(showQualityDialog = true)
    }

    /**
     * Hides quality selection dialog.
     */
    fun hideQualityDialog() {
        val currentState = _controlsState.value
        _controlsState.value = currentState.copy(showQualityDialog = false)
    }

    /**
     * Sets selected quality and hides dialog.
     * @param quality Quality string (e.g., "1080p", "720p60")
     */
    fun setQuality(quality: String) {
        val currentState = _controlsState.value
        _controlsState.value = currentState.copy(quality = quality)
        hideQualityDialog()
    }

    override fun onCleared() {
        super.onCleared()
        cancelAutoHide()
    }
}
