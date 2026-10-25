package com.kntndrsn.quicksearchkjvbible.data

/**
 * Allocation-light text normalization for reference, book, and verse queries.
 */
object SearchNormalizer {
    /** Characters deleted (not replaced by a space) before building verses.text_norm. */
    private const val VERSE_MARKER_CHARS = "\u2039\u203A[]\u00B6,;"

    fun normalize(text: String): String {
        val cleaned = clean(text, allowColon = true, stripVerseMarkers = false)
        return rewriteLeadingOrdinal(cleaned)
    }

    fun normalizeVerseQuery(text: String): String =
        clean(text, allowColon = false, stripVerseMarkers = false)

    /** Normalization used for the bundled database column verses.text_norm. */
    fun normalizeVerseText(text: String): String =
        clean(text, allowColon = false, stripVerseMarkers = true)

    fun compact(text: String): String = compactNormalized(normalize(text))

    internal fun compactNormalized(normalized: String): String =
        if (normalized.indexOf(' ') < 0) normalized else normalized.replace(" ", "")

    fun soundex(text: String): String = soundexOfCompact(compact(text))

    internal fun soundexOfCompact(compact: String): String {
        if (compact.isEmpty()) return ""
        val out = CharArray(4) { '0' }
        out[0] = compact[0].uppercaseChar()
        var size = 1
        var last = '\u0000'
        var i = 1
        while (i < compact.length && size < 4) {
            val digit = soundexDigit(compact[i])
            if (digit != '0' && digit != last) {
                out[size++] = digit
                last = digit
            }
            i++
        }
        return String(out)
    }

    private fun soundexDigit(char: Char): Char = when (char) {
        'b', 'f', 'p', 'v' -> '1'
        'c', 'g', 'j', 'k', 'q', 's', 'x', 'z' -> '2'
        'd', 't' -> '3'
        'l' -> '4'
        'm', 'n' -> '5'
        'r' -> '6'
        else -> '0'
    }

    /**
     * Equivalent of lowercase() -> replace([^a-z0-9( :)]) with ' ' -> collapse \s+ -> trim.
     * Every rejected char becomes a separator, so the result is the kept-char runs joined by
     * single spaces. Non-ASCII chars use full String lowercasing (e.g. U+0130 -> "i\u0307").
     */
    private fun clean(text: String, allowColon: Boolean, stripVerseMarkers: Boolean): String {
        val sb = StringBuilder(text.length)
        var pendingSpace = false
        fun accept(c: Char) {
            val kept = c in 'a'..'z' || c in '0'..'9' || (allowColon && c == ':')
            if (kept) {
                if (pendingSpace && sb.isNotEmpty()) sb.append(' ')
                pendingSpace = false
                sb.append(c)
            } else {
                pendingSpace = true
            }
        }
        for (c in text) {
            if (stripVerseMarkers && VERSE_MARKER_CHARS.indexOf(c) >= 0) continue
            when {
                c.code < 128 -> accept(if (c in 'A'..'Z') (c.code + 32).toChar() else c)
                else -> {
                    val lower = c.toString().lowercase()
                    for (lc in lower) accept(lc)
                }
            }
        }
        return sb.toString()
    }

    private fun rewriteLeadingOrdinal(cleaned: String): String {
        if (cleaned.isEmpty()) return cleaned
        val third = matchOrdinal(cleaned, "3rd", "third", "iii")
        if (third >= 0) return "3 " + cleaned.substring(third)
        val second = matchOrdinal(cleaned, "2nd", "second", "ii")
        if (second >= 0) return "2 " + cleaned.substring(second)
        val first = matchOrdinal(cleaned, "1st", "first", "i")
        if (first >= 0) return "1 " + cleaned.substring(first)
        return cleaned
    }

    /**
     * Mirrors ^(?:a|b|c)(?:\s+|(?=[a-z])). Returns the index of the remainder or -1.
     * Alternatives start with distinct chars, so at most one can match.
     */
    private fun matchOrdinal(s: String, vararg words: String): Int {
        for (word in words) {
            if (!s.startsWith(word)) continue
            val end = word.length
            if (end >= s.length) return -1
            val next = s[end]
            return when {
                next == ' ' -> end + 1
                next in 'a'..'z' -> end
                else -> -1
            }
        }
        return -1
    }
}
