package com.wysp.krysp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActionItemsTest {

    @Test
    fun `self-commitment cue assigns owner to the speaker`() {
        val segments = listOf(
            TranscriptSegment(Track.YOU, "You", 0.0, 2.0, "I'll send the notes tomorrow"),
            TranscriptSegment(Track.CALL, "Call", 2.0, 3.0, "sounds good"),
        )

        val items = extractActionItems(segments)

        assertEquals(1, items.size)
        assertEquals("You", items[0].owner)
        assertEquals("tomorrow", items[0].dueHint)
    }

    @Test
    fun `request cue assigns owner to the other participant`() {
        val segments = listOf(
            TranscriptSegment(Track.YOU, "You", 0.0, 2.0, "can you send the deck by friday"),
            TranscriptSegment(Track.CALL, "Call", 2.0, 3.0, "sure thing"),
        )

        val items = extractActionItems(segments)

        assertEquals(1, items.size)
        assertEquals("Call", items[0].owner)
        assertTrue(items[0].dueHint!!.contains("friday", ignoreCase = true))
    }

    @Test
    fun `segments without cue phrases produce no action items`() {
        val segments = listOf(
            TranscriptSegment(Track.YOU, "You", 0.0, 2.0, "that meeting was interesting"),
        )

        assertEquals(emptyList(), extractActionItems(segments))
    }

    @Test
    fun `formats action items with owner and due hint`() {
        val items = listOf(ActionItem(owner = "You", text = "send the notes", dueHint = "tomorrow", sourceStart = 0.0))

        assertEquals("- [You] send the notes (due: tomorrow)", formatActionItems(items))
    }

    @Test
    fun `formats no action items message when list is empty`() {
        assertEquals("No action items detected.", formatActionItems(emptyList()))
    }
}
