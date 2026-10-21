package com.rdp.biblequicksearchkjv.data

object SearchNormalizer {
    private val leadingFirstRegex = Regex("^(?:1st|first|i)(?:\\s+|(?=[a-z]))")
    private val leadingSecondRegex = Regex("^(?:2nd|second|ii)(?:\\s+|(?=[a-z]))")
    private val leadingThirdRegex = Regex("^(?:3rd|third|iii)(?:\\s+|(?=[a-z]))")
    private val nonAlphanumericColonRegex = Regex("[^a-z0-9: ]")
    private val nonAlphanumericRegex = Regex("[^a-z0-9 ]")
    private val whitespaceRegex = Regex("\\s+")

    fun normalize(text: String): String {
        val cleaned = text
            .lowercase()
            .replace(nonAlphanumericColonRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()

        return cleaned
            .replace(leadingThirdRegex, "3 ")
            .replace(leadingSecondRegex, "2 ")
            .replace(leadingFirstRegex, "1 ")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    fun normalizeVerseQuery(text: String): String {
        return text
            .lowercase()
            .replace(nonAlphanumericRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    fun compact(text: String): String = normalize(text).replace(" ", "")

    fun soundex(text: String): String {
        val normalized = compact(text)
        if (normalized.isEmpty()) return ""

        val head = normalized.first().uppercaseChar()
        val tail = normalized.drop(1)
            .map(::soundexDigit)
            .filter { it != '0' }
            .fold(StringBuilder()) { acc, c ->
                if (acc.isEmpty() || acc.last() != c) acc.append(c)
                acc
            }
            .toString()

        return (head + tail).padEnd(4, '0').take(4)
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
}




