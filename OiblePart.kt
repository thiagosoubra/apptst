package com.agospace.bokob

data class OiblePart(
    val title: String,
    val durationSec: Int, // Duration of this part in seconds
    val filePath: String,
    val textFilePath: String,
    var status: String = "unplayed", // "unplayed", "incomplete", "complete"
    var currentTime: Long = 0L // Current playback position in seconds for this part
) {
    fun formattedTime(): String {
        val min = durationSec / 60
        val sec = durationSec % 60
        return String.format("%02d:%02d", min, sec)
    }
}