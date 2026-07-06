package com.wysp.krysp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SummaryTest {

    @Test
    fun `empty segments yield empty summary`() {
        assertEquals(emptyList(), summarize(emptyList()))
    }

    @Test
    fun `picks the sentences repeating the most salient words`() {
        val segments = listOf(
            TranscriptSegment(Track.YOU, "You", 0.0, 3.0, "The budget review is the main topic today."),
            TranscriptSegment(Track.CALL, "Call", 3.0, 6.0, "I saw a bird outside my window."),
            TranscriptSegment(Track.YOU, "You", 6.0, 9.0, "Let's go over the budget numbers in detail."),
        )

        val summary = summarize(segments, maxSentences = 1)

        assertEquals(1, summary.size)
        assertTrue(summary[0].text.contains("budget", ignoreCase = true))
    }

    @Test
    fun `keeps summary sentences in chronological order`() {
        val segments = listOf(
            TranscriptSegment(Track.YOU, "You", 0.0, 1.0, "Budget budget budget."),
            TranscriptSegment(Track.CALL, "Call", 1.0, 2.0, "Timeline timeline timeline."),
        )

        val summary = summarize(segments, maxSentences = 2)

        assertEquals(listOf(0.0, 1.0), summary.map { it.start })
    }

    @Test
    fun `format summary joins sentences with spaces`() {
        val sentences = listOf(
            SummarySentence("First point.", "You", 0.0),
            SummarySentence("Second point.", "Call", 1.0),
        )
        assertEquals("First point. Second point.", formatSummary(sentences))
    }

    @Test
    fun `format summary handles empty list`() {
        assertEquals("No summary available.", formatSummary(emptyList()))
    }
}
