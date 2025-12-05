package com.github.opentube.player.util

import java.util.Locale

/**
 * Utility object for formatting time durations in human-readable formats.
 * Supports both long (HH:MM:SS) and short (MM:SS) time representations.
 */
object TimeFormatter {

    /**
     * Formats milliseconds into a readable time string.
     * Shows hours only when duration exceeds 1 hour.
     *
     * @param milliseconds Duration in milliseconds
     * @return Formatted time string (HH:MM:SS or MM:SS)
     */
    fun formatTime(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Formats milliseconds into short MM:SS format (always 2 digits for minutes).
     *
     * @param milliseconds Duration in milliseconds
     * @return Short formatted time string (MM:SS)
     */
    fun formatShortTime(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    /**
     * Formats milliseconds into compact duration string for UI display.
     * Examples: "2:30", "1:05:23", "45s"
     */
    fun formatCompactTime(milliseconds: Long): String {
        if (milliseconds < 1000) return "<1s"

        val totalSeconds = milliseconds / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            minutes > 0 -> String.format(Locale.US, "%d:%02d", minutes, seconds)
            else -> "${seconds}s"
        }
    }
}
