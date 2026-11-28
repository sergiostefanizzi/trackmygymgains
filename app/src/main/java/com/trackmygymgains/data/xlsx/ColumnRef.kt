package com.trackmygymgains.data.xlsx

/** Conversions between spreadsheet column letters and 0-based indexes (A=0, Z=25, AA=26). */
object ColumnRef {
    /** @throws IllegalArgumentException if [letters] is empty or not made of A-Z. */
    fun toIndex(letters: String): Int {
        require(letters.isNotEmpty()) { "Empty column reference" }
        var result = 0
        for (ch in letters) {
            val c = ch.uppercaseChar()
            require(c in 'A'..'Z') { "Invalid column reference: $letters" }
            result = result * 26 + (c - 'A' + 1)
            require(result <= MAX_COLUMNS) { "Column out of range: $letters" }
        }
        return result - 1
    }

    fun toLetters(index: Int): String {
        require(index >= 0) { "Negative column index: $index" }
        val sb = StringBuilder()
        var n = index + 1
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append('A' + rem)
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    /**
     * Parses a cell reference such as "AB12" into (row, col), both 0-based.
     * Returns null when the reference is malformed.
     */
    fun parseCellRef(ref: String): Pair<Int, Int>? {
        val split = ref.indexOfFirst { it.isDigit() }
        if (split <= 0) return null
        val row = ref.substring(split).toIntOrNull() ?: return null
        if (row < 1) return null
        val col = try {
            toIndex(ref.substring(0, split))
        } catch (e: IllegalArgumentException) {
            return null
        }
        return (row - 1) to col
    }

    private const val MAX_COLUMNS = 1_000_000
}
