package com.wysp.krysp.core

/** Which audio track a segment came from. */
enum class Track {
    YOU, // your own mic uplink
    CALL, // the far-end / call audio (privileged downlink capture, or undifferentiated mic+speakerphone mix)
    UNKNOWN, // couldn't be attributed to a track (e.g. diarized turn from a single mixed source)
}

data class TranscriptSegment(
    val track: Track,
    val speakerLabel: String,
    val start: Double, // seconds
    val end: Double,
    val text: String,
)

/** Interleaves two already-chronological segment lists into one timeline, sorted by start time. */
fun mergeSegments(youSegments: List<TranscriptSegment>, callSegments: List<TranscriptSegment>): List<TranscriptSegment> {
    return (youSegments + callSegments).sortedBy { it.start }
}

fun formatTimestamp(seconds: Double): String {
    val total = seconds.toInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val secs = total % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%02d:%02d".format(minutes, secs)
    }
}

fun formatTranscript(segments: List<TranscriptSegment>): String {
    return segments.joinToString("\n") { seg ->
        "[${formatTimestamp(seg.start)}] ${seg.speakerLabel}: ${seg.text.trim()}"
    }
}
