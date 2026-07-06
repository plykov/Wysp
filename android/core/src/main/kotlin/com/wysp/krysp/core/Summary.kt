package com.wysp.krysp.core

data class SummarySentence(val text: String, val speakerLabel: String, val start: Double)

private val STOPWORDS = setOf(
    "the", "a", "an", "is", "it", "to", "and", "of", "in", "on", "for", "that", "this",
    "we", "you", "i", "be", "are", "was", "were", "will", "with", "so", "just", "like",
    "yeah", "okay", "ok", "um", "uh", "think", "know", "going", "gonna", "can", "do",
    "have", "has", "had", "at", "as", "but", "or", "if", "then", "there", "here",
)

private fun splitSentences(text: String): List<String> =
    text.split(Regex("""(?<=[.!?])\s+"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

private fun wordsOf(sentence: String): List<String> =
    Regex("""[a-zA-Z']+""").findAll(sentence.lowercase()).map { it.value }.toList()

/**
 * Lightweight, fully on-device extractive summarizer: scores sentences by frequency of their
 * (non-stopword) words across the whole transcript, keeps the top [maxSentences], and returns
 * them back in chronological order. This is deliberately not an abstractive/LLM summary - it
 * picks representative existing sentences rather than writing new ones - because that's what's
 * feasible to run entirely on a phone without a multi-hundred-MB-plus local LLM. Swap in a
 * MediaPipe LLM / llama.cpp-backed SummaryGenerator later for higher-quality abstractive minutes.
 */
fun summarize(segments: List<TranscriptSegment>, maxSentences: Int = 5): List<SummarySentence> {
    if (segments.isEmpty()) return emptyList()

    data class Candidate(val sentence: String, val speakerLabel: String, val start: Double)

    val candidates = mutableListOf<Candidate>()
    for (seg in segments) {
        for (sentence in splitSentences(seg.text)) {
            candidates.add(Candidate(sentence, seg.speakerLabel, seg.start))
        }
    }
    if (candidates.isEmpty()) return emptyList()

    val wordFreq = mutableMapOf<String, Int>()
    val candidateWords = candidates.map { wordsOf(it.sentence).filter { w -> w !in STOPWORDS } }
    candidateWords.forEach { words -> words.forEach { wordFreq[it] = (wordFreq[it] ?: 0) + 1 } }

    val scored = candidates.mapIndexed { i, candidate ->
        val words = candidateWords[i]
        val score = if (words.isEmpty()) 0.0 else words.sumOf { wordFreq[it]!!.toDouble() } / words.size
        candidate to score
    }

    val top = scored.sortedByDescending { it.second }.take(maxSentences).map { it.first }
    val topSet = top.toSet()
    return candidates
        .filter { it in topSet }
        .map { SummarySentence(it.sentence, it.speakerLabel, it.start) }
}

fun formatSummary(sentences: List<SummarySentence>): String {
    if (sentences.isEmpty()) return "No summary available."
    return sentences.joinToString(" ") { it.text.trim() }
}
