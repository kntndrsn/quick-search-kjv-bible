package com.rdp.biblequicksearchkjv.data

object SearchNormalizer {
    fun normalize(text: String): String {
        return text
            .lowercase()
            .replace("1st", "1")
            .replace("2nd", "2")
            .replace("3rd", "3")
            .replace("first", "1")
            .replace("second", "2")
            .replace("third", "3")
            .replace("iii ", "3 ")
            .replace("ii ", "2 ")
            .replace("i ", "1 ")
            .replace(Regex("[^a-z0-9: ]"), " ")
            .replace(Regex("\\s+"), " ")
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


