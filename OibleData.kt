package com.agospace.bokob

data class OibleBook(
    val title: String,
    val by: String,
    val about: String,
    var totalDurationSec: Int, // Calculated total duration of all parts
    val year: Int,
    val series: SeriesInfo? = null,
    val path: String, // Extracted path in internal folder
    val coverImagePath: String, // Path to cover.jpg
    val titleImagePath: String? = null,
    var playing: Boolean = false, // True if this oible is currently playing
    var status: String = "unplayed", // "unplayed", "incomplete", "complete"
    val addedAt: Long, // Timestamp when the oible was added
    val parts: List<OiblePart> // List of all parts within this oible
)

data class SeriesInfo(
    val title: String,
    val by: String,
    val index: String
)