package com.wysp.krysp.core

import kotlin.test.Test
import kotlin.test.assertTrue

class MinutesTest {

    @Test
    fun `minutes doc includes title summary action items and transcript sections`() {
        val segments = listOf(
            TranscriptSegment(Track.YOU, "You", 0.0, 2.0, "I'll send the notes tomorrow."),
            TranscriptSegment(Track.CALL, "Call", 2.0, 4.0, "Sounds good, thanks."),
        )

        val minutes = formatMinutes("Weekly Sync", segments)

        assertTrue(minutes.contains("# Weekly Sync"))
        assertTrue(minutes.contains("## Summary"))
        assertTrue(minutes.contains("## Action Items"))
        assertTrue(minutes.contains("## Full Transcript"))
        assertTrue(minutes.contains("[You]"))
        assertTrue(minutes.contains("[00:00] You: I'll send the notes tomorrow."))
    }
}
