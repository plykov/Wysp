package com.wysp.krysp.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TranscriptTest {

    @Test
    fun `merges two tracks in chronological order`() {
        val you = listOf(
            TranscriptSegment(Track.YOU, "You", 0.0, 2.0, "hey can you hear me"),
            TranscriptSegment(Track.YOU, "You", 5.0, 6.5, "great"),
        )
        val call = listOf(
            TranscriptSegment(Track.CALL, "Call", 2.5, 4.5, "yes loud and clear"),
        )

        val merged = mergeSegments(you, call)

        assertEquals(listOf("hey can you hear me", "yes loud and clear", "great"), merged.map { it.text })
    }

    @Test
    fun `merge of empty inputs is empty`() {
        assertEquals(emptyList(), mergeSegments(emptyList(), emptyList()))
    }

    @Test
    fun `formats timestamp without hours when under an hour`() {
        assertEquals("01:05", formatTimestamp(65.0))
    }

    @Test
    fun `formats timestamp with hours when an hour or more`() {
        assertEquals("01:02:05", formatTimestamp(3725.0))
    }

    @Test
    fun `formats transcript with timestamp label and trimmed text`() {
        val segments = listOf(TranscriptSegment(Track.YOU, "You", 65.0, 67.0, " hello there "))
        assertEquals("[01:05] You: hello there", formatTranscript(segments))
    }
}
