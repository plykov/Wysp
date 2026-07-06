package com.wysp.krysp.core

/** Combines summary + action items + full transcript into one Markdown "meeting minutes" doc. */
fun formatMinutes(
    title: String,
    segments: List<TranscriptSegment>,
    maxSummarySentences: Int = 5,
): String {
    val summary = summarize(segments, maxSummarySentences)
    val actionItems = extractActionItems(segments)

    return buildString {
        appendLine("# $title")
        appendLine()
        appendLine("## Summary")
        appendLine(formatSummary(summary))
        appendLine()
        appendLine("## Action Items")
        appendLine(formatActionItems(actionItems))
        appendLine()
        appendLine("## Full Transcript")
        appendLine(formatTranscript(segments))
    }
}
