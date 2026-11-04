package com.shopmanager.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Typo/dialect tolerant search over Arabic (and Latin) names.
 *
 * Ranking tiers, best first: exact token, prefix, substring, phonetic fold
 * (confusable letters merged), vowel-less skeleton, bounded edit distance.
 * If nothing matches strictly, partial matches are returned, and if there are
 * none either, the closest few names by similarity are returned so a query
 * never ends on an empty list unless nothing even resembles it.
 */
object ArabicText {
    private val letterMap = mapOf(
        'أ' to 'ا', 'إ' to 'ا', 'آ' to 'ا', 'ٱ' to 'ا',
        'ى' to 'ي', 'ی' to 'ي', 'ئ' to 'ي',
        'ة' to 'ه', 'ہ' to 'ه', 'ھ' to 'ه',
        'ؤ' to 'و',
        'ڤ' to 'ف', 'پ' to 'ب', 'چ' to 'ج', 'گ' to 'ك', 'ک' to 'ك',
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
    )

    private val foldMap = mapOf(
        'ذ' to 'ز', 'ظ' to 'ز', 'ض' to 'ز',
        'ث' to 'س', 'ص' to 'س',
        'ط' to 'ت',
        'ق' to 'ك'
    )

    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        var last = ' '
        for (raw in input.lowercase()) {
            val code = raw.code
            if (code in 0x064B..0x065F || code == 0x0670 || code == 0x0640 || raw == 'ء') continue
            val c = letterMap[raw] ?: raw
            val out = if (c.isLetterOrDigit()) c else ' '
            if (out == last && out != ' ') continue
            if (out == ' ' && last == ' ') continue
            sb.append(out)
            last = out
        }
        return sb.toString().trim()
    }

    fun fold(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(foldMap[c] ?: c)
        return sb.toString()
    }

    fun skeleton(folded: String): String {
        if (folded.length <= 2) return folded
        val sb = StringBuilder()
        sb.append(folded[0])
        for (i in 1 until folded.length) {
            val c = folded[i]
            if (c == 'ا' || c == 'و' || c == 'ي') continue
            if (c == 'ه' && i == folded.lastIndex) continue
            sb.append(c)
        }
        return sb.toString()
    }

    fun tokens(input: String): List<String> =
        normalize(input).split(' ')
            .filter { it.isNotEmpty() }
            .map { if (it.length > 3 && it.startsWith("ال")) it.substring(2) else it }
            .filter { it.isNotEmpty() }

    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev2 = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = minOf(v, prev2[j - 2] + 1)
                }
                cur[j] = v
            }
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}

class SmartSearchIndex<T>(private val items: List<T>, name: (T) -> String) {

    private class Tok(val n: String) {
        val f: String = ArabicText.fold(n)
        val s: String = ArabicText.skeleton(f)
    }

    private class Entry(val index: Int, val tokens: List<Tok>, val compact: String, val compactFolded: String)

    private val entries: List<Entry> by lazy(LazyThreadSafetyMode.PUBLICATION) { items.mapIndexed { i, item ->
        val toks = ArabicText.tokens(name(item)).map { Tok(it) }
        val compact = toks.joinToString("") { it.n }
        Entry(i, toks, compact, ArabicText.fold(compact))
    } }

    private fun tokenScore(q: Tok, n: Tok): Double {
        val ql = q.n.length
        if (q.n == n.n) return 1.0
        if (n.n.startsWith(q.n)) return 0.8 + 0.1 * ql.toDouble() / n.n.length
        if (ql >= 2 && n.n.contains(q.n)) return 0.7
        if (ql >= 2 && q.f == n.f) return 0.85
        if (ql >= 2 && n.f.startsWith(q.f)) return 0.75
        if (ql >= 3 && n.f.contains(q.f)) return 0.6
        if (q.s.length >= 2 && n.s.isNotEmpty()) {
            if (q.s == n.s) return 0.8
            if (n.s.startsWith(q.s)) return 0.65
            if (q.s.length >= 3 && n.s.contains(q.s)) return 0.55
        }
        if (ql >= 3) {
            val maxD = if (ql <= 4) 1 else if (ql <= 7) 2 else 3
            var d = ArabicText.distance(q.f, n.f)
            if (n.f.length > ql) d = minOf(d, ArabicText.distance(q.f, n.f.take(ql)) + 1)
            if (d <= maxD) return 0.5 - 0.08 * d
            if (q.s.length >= 3 && n.s.length >= 3 && ArabicText.distance(q.s, n.s) <= 1) return 0.35
        }
        return 0.0
    }

    private fun similarity(q: Tok, n: Tok): Double {
        val a = 1.0 - ArabicText.distance(q.f, n.f).toDouble() / maxOf(q.f.length, n.f.length, 1)
        val b = if (q.s.isNotEmpty() && n.s.isNotEmpty()) {
            1.0 - ArabicText.distance(q.s, n.s).toDouble() / maxOf(q.s.length, n.s.length, 1)
        } else 0.0
        return maxOf(a, b)
    }

    fun search(query: String): List<T> {
        val qToks = ArabicText.tokens(query).map { Tok(it) }
        if (qToks.isEmpty()) return items
        val qCompact = qToks.joinToString("") { it.n }
        val qCompactFolded = ArabicText.fold(qCompact)

        val strict = ArrayList<Pair<Entry, Double>>()
        val partial = ArrayList<Pair<Entry, Double>>()
        for (e in entries) {
            if (e.tokens.isEmpty()) continue
            val per = qToks.map { q -> e.tokens.maxOf { tokenScore(q, it) } }
            var score = per.average()
            val all = per.all { it > 0.0 }
            if (qCompact.length >= 2) {
                if (e.compact.contains(qCompact)) {
                    score = maxOf(score, 0.7)
                    if (e.compact.startsWith(qCompact)) score += 0.05
                    strict.add(e to score); continue
                }
                if (e.compactFolded.contains(qCompactFolded)) {
                    strict.add(e to maxOf(score, 0.55)); continue
                }
            }
            if (all) strict.add(e to score)
            else if (score > 0.0) partial.add(e to score * 0.5)
        }
        val chosen = when {
            strict.isNotEmpty() -> strict
            partial.isNotEmpty() -> partial
            else -> entries.mapNotNull { e ->
                if (e.tokens.isEmpty()) null
                else {
                    val sim = qToks.map { q -> e.tokens.maxOf { similarity(q, it) } }.average()
                    if (sim >= 0.34) e to sim else null
                }
            }.sortedWith(compareByDescending<Pair<Entry, Double>> { it.second }.thenBy { it.first.index }).take(3)
        }
        return chosen
            .sortedWith(compareByDescending<Pair<Entry, Double>> { it.second }.thenBy { it.first.index })
            .map { items[it.first.index] }
    }
}

@Composable
fun <T> rememberSmartSearch(items: List<T>, query: String, name: (T) -> String): List<T> {
    val lowTier = LocalPerformanceTier.current == PerformanceTier.LOW
    val index = remember(items) { SmartSearchIndex(items, name) }
    // Matching runs off the main thread, and on weak devices it waits for a
    // short typing pause so every keystroke doesn't trigger a re-rank + relayout.
    val result by produceState(items, index, query) {
        if (query.isBlank()) { value = items; return@produceState }
        if (lowTier) delay(140)
        value = withContext(Dispatchers.Default) { index.search(query) }
    }
    return if (query.isBlank()) items else result
}
