package com.wysp.krysp.core

/**
 * A single raw ASR segment with no speaker attribution yet, e.g. straight out of Whisper
 * transcribing one mixed mono track (the mic+speakerphone fallback capture path).
 */
data class RawSegment(val start: Double, val end: Double, val text: String)

/**
 * Naive pause-based turn splitting for the single-mixed-track fallback capture path.
 *
 * This is NOT real speaker diarization (no voice embeddings/clustering) - it's a heuristic
 * that starts a new "turn" whenever the gap since the previous segment exceeds [pauseThresholdSeconds],
 * and alternates a generic Speaker 1 / Speaker 2 label across turns. It's good enough to make a
 * mic+speakerphone transcript readable, but two turns from the *same* physical speaker separated by
 * a pause will incorrectly get different labels, and it can't tell you which label is actually "you".
 * A real fix would run an on-device speaker-embedding model over each turn and cluster; see README.
 */
fun naiveDiarize(
    segments: List<RawSegment>,
    pauseThresholdSeconds: Double = 1.2,
): List<TranscriptSegment> {
    if (segments.isEmpty()) return emptyList()

    val result = mutableListOf<TranscriptSegment>()
    var currentSpeakerIndex = 0
    var previousEnd: Double? = null

    for (seg in segments) {
        val gap = previousEnd?.let { seg.start - it }
        if (gap != null && gap > pauseThresholdSeconds) {
            currentSpeakerIndex = 1 - currentSpeakerIndex
        }
        result.add(
            TranscriptSegment(
                track = Track.UNKNOWN,
                speakerLabel = "Speaker ${currentSpeakerIndex + 1}",
                start = seg.start,
                end = seg.end,
                text = seg.text,
            )
        )
        previousEnd = seg.end
    }
    return result
}
