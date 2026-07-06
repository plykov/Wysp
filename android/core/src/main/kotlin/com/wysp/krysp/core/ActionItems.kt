package com.wysp.krysp.core

data class ActionItem(
    val owner: String,
    val text: String,
    val dueHint: String?,
    val sourceStart: Double,
)

private enum class CueDirection {
    SELF_COMMITMENT, // "I'll send the doc" -> owner is whoever is speaking
    REQUEST_OF_OTHER, // "can you send the doc" -> owner is whoever is being spoken to
}

private data class Cue(val regex: Regex, val direction: CueDirection)

private val CUES = listOf(
    Cue(Regex("""\bi'?ll\b""", RegexOption.IGNORE_CASE), CueDirection.SELF_COMMITMENT),
    Cue(Regex("""\bi will\b""", RegexOption.IGNORE_CASE), CueDirection.SELF_COMMITMENT),
    Cue(Regex("""\bi need to\b""", RegexOption.IGNORE_CASE), CueDirection.SELF_COMMITMENT),
    Cue(Regex("""\bi'?m going to\b""", RegexOption.IGNORE_CASE), CueDirection.SELF_COMMITMENT),
    Cue(Regex("""\blet me\b""", RegexOption.IGNORE_CASE), CueDirection.SELF_COMMITMENT),
    Cue(Regex("""\bcan you\b""", RegexOption.IGNORE_CASE), CueDirection.REQUEST_OF_OTHER),
    Cue(Regex("""\bcould you\b""", RegexOption.IGNORE_CASE), CueDirection.REQUEST_OF_OTHER),
    Cue(Regex("""\bwould you\b""", RegexOption.IGNORE_CASE), CueDirection.REQUEST_OF_OTHER),
    Cue(Regex("""\bplease\b""", RegexOption.IGNORE_CASE), CueDirection.REQUEST_OF_OTHER),
    Cue(Regex("""\byou need to\b""", RegexOption.IGNORE_CASE), CueDirection.REQUEST_OF_OTHER),
)

private val DUE_HINTS = listOf(
    Regex("""\btoday\b""", RegexOption.IGNORE_CASE),
    Regex("""\btonight\b""", RegexOption.IGNORE_CASE),
    Regex("""\btomorrow\b""", RegexOption.IGNORE_CASE),
    Regex("""\bthis week\b""", RegexOption.IGNORE_CASE),
    Regex("""\bnext week\b""", RegexOption.IGNORE_CASE),
    Regex("""\bend of day\b|\beod\b""", RegexOption.IGNORE_CASE),
    Regex("""\bby (monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", RegexOption.IGNORE_CASE),
)

/**
 * Rule-based (not ML) action-item extraction: flags segments containing commitment/request
 * cue phrases ("I'll...", "can you...", etc.) and assigns an owner heuristically from who's
 * speaking and the cue's direction. Intentionally simple and fully on-device / offline;
 * it will have false positives/negatives compared to an LLM-based extractor, by design tradeoff.
 */
fun extractActionItems(segments: List<TranscriptSegment>): List<ActionItem> {
    val items = mutableListOf<ActionItem>()
    for (seg in segments) {
        val cue = CUES.firstOrNull { it.regex.containsMatchIn(seg.text) } ?: continue
        val owner = when (cue.direction) {
            CueDirection.SELF_COMMITMENT -> seg.speakerLabel
            CueDirection.REQUEST_OF_OTHER -> otherSpeaker(seg.speakerLabel, segments)
        }
        val dueHint = DUE_HINTS.firstNotNullOfOrNull { it.find(seg.text)?.value }
        items.add(ActionItem(owner = owner, text = seg.text.trim(), dueHint = dueHint, sourceStart = seg.start))
    }
    return items
}

private fun otherSpeaker(speakerLabel: String, allSegments: List<TranscriptSegment>): String {
    val distinctLabels = allSegments.map { it.speakerLabel }.distinct()
    return distinctLabels.firstOrNull { it != speakerLabel } ?: "Other participant"
}

fun formatActionItems(items: List<ActionItem>): String {
    if (items.isEmpty()) return "No action items detected."
    return items.joinToString("\n") { item ->
        val due = item.dueHint?.let { " (due: $it)" } ?: ""
        "- [${item.owner}] ${item.text}$due"
    }
}
