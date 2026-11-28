package com.trackmygymgains.data.xlsx

/** Read-only grid of cell texts for one worksheet. Rows and columns are 0-based. */
class SheetGrid internal constructor(
    val name: String,
    private val cells: Map<Int, Map<Int, String>>,
) {
    /** Last non-empty row index + 1 (0 for an empty sheet). */
    val rowCount: Int = (cells.keys.maxOrNull() ?: -1) + 1

    /** Raw cell text (not trimmed); null when the cell is missing, empty or an error. */
    fun cell(row: Int, col: Int): String? = cells[row]?.get(col)

    /** Same as [cell] using an A1-style reference ("A1", "AB12"). */
    fun cell(ref: String): String? {
        val (row, col) = ColumnRef.parseCellRef(ref)
            ?: throw IllegalArgumentException("Invalid cell reference: $ref")
        return cell(row, col)
    }
}
