package com.wysp.krysp.core

import kotlin.test.Test
import kotlin.test.assertEquals

class DiarizationTest {

    @Test
    fun `keeps same speaker label when segments are close together`() {
        val segments = listOf(
            RawSegment(0.0, 1.0, "hi there"),
            RawSegment(1.1, 2.0, "how's it going"),
        )

        val result = naiveDiarize(segments)

        assertEquals(listOf("Speaker 1", "Speaker 1"), result.map { it.speakerLabel })
    }

    @Test
    fun `switches speaker label after a long pause`() {
        val segments = listOf(
            RawSegment(0.0, 1.0, "hi there"),
            RawSegment(3.0, 4.0, "good, you?"),
        )

        val result = naiveDiarize(segments, pauseThresholdSeconds = 1.2)

        assertEquals(listOf("Speaker 1", "Speaker 2"), result.map { it.speakerLabel })
    }

    @Test
    fun `alternates back after another long pause`() {
        val segments = listOf(
            RawSegment(0.0, 1.0, "a"),
            RawSegment(3.0, 4.0, "b"),
            RawSegment(6.0, 7.0, "c"),
        )

        val result = naiveDiarize(segments, pauseThresholdSeconds = 1.2)

        assertEquals(listOf("Speaker 1", "Speaker 2", "Speaker 1"), result.map { it.speakerLabel })
    }

    @Test
    fun `empty input yields empty output`() {
        assertEquals(emptyList(), naiveDiarize(emptyList()))
    }

    @Test
    fun `tracks are marked unknown since source is a single mixed track`() {
        val segments = listOf(RawSegment(0.0, 1.0, "hi"))
        assertEquals(listOf(Track.UNKNOWN), naiveDiarize(segments).map { it.track })
    }
}
