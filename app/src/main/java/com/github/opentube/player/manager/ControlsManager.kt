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

    private fun initializeBrightness() {
        val activity = context as? Activity
        val brightness = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f }
            ?: try {
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS
                ) / 255f
            } catch (e: Exception) {
                0.5f
            }

        _controlsState.value = _controlsState.value.copy(brightness = brightness)
    }

    fun toggleControls() {
        val newState = !_controlsState.value.showControls
        _controlsState.value = _controlsState.value.copy(showControls = newState)

        if (newState) {
            scheduleAutoHide()
        } else {
            cancelAutoHide()
        }
    }

    fun showControls() {
        _controlsState.value = _controlsState.value.copy(showControls = true)
        scheduleAutoHide()
    }

    fun hideControls() {
        _controlsState.value = _controlsState.value.copy(showControls = false)
        cancelAutoHide()
    }

    private fun scheduleAutoHide() {
        cancelAutoHide()
        autoHideJob = viewModelScope.launch {
            delay(AUTO_HIDE_DELAY)
            _controlsState.value = _controlsState.value.copy(showControls = false)
        }
    }

    private fun cancelAutoHide() {
        autoHideJob?.cancel()
        autoHideJob = null
    }

    fun showSeekOverlay(direction: Int) {
        _controlsState.value = _controlsState.value.copy(
            showSeekOverlay = true,
            seekDirection = direction
        )

        viewModelScope.launch {
            delay(SEEK_OVERLAY_DURATION)
            _controlsState.value = _controlsState.value.copy(showSeekOverlay = false)
        }
    }

    fun showVolumeOverlay() {
        hideControls()
        _controlsState.value = _controlsState.value.copy(showVolumeOverlay = true)
    }

    fun hideVolumeOverlay() {
        viewModelScope.launch {
            delay(OVERLAY_HIDE_DELAY)
            _controlsState.value = _controlsState.value.copy(showVolumeOverlay = false)
        }
    }

    fun showBrightnessOverlay() {
        hideControls()
        _controlsState.value = _controlsState.value.copy(showBrightnessOverlay = true)
    }

    fun hideBrightnessOverlay() {
        viewModelScope.launch {
            delay(OVERLAY_HIDE_DELAY)
            _controlsState.value = _controlsState.value.copy(showBrightnessOverlay = false)
        }
    }

    fun setBrightness(brightness: Float) {
        val clampedBrightness = brightness.coerceIn(0f, 1f)
        _controlsState.value = _controlsState.value.copy(brightness = clampedBrightness)

        val activity = context as? Activity
        val layoutParams = activity?.window?.attributes
        layoutParams?.screenBrightness = clampedBrightness
        activity?.window?.attributes = layoutParams
    }

    fun adjustBrightness(delta: Float) {
        val newBrightness = (_controlsState.value.brightness + delta).coerceIn(0f, 1f)
        setBrightness(newBrightness)
    }

    fun showQualityDialog() {
        _controlsState.value = _controlsState.value.copy(showQualityDialog = true)
    }

    fun hideQualityDialog() {
        _controlsState.value = _controlsState.value.copy(showQualityDialog = false)
    }

    fun setQuality(quality: String) {
        _controlsState.value = _controlsState.value.copy(quality = quality)
        hideQualityDialog()
    }

    override fun onCleared() {
        super.onCleared()
        cancelAutoHide()
    }
}