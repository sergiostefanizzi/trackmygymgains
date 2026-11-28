package com.trackmygymgains.testing

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a minimal but valid xlsx file as bytes, for tests (JVM and instrumented).
 *
 * ```
 * val bytes = XlsxTestBuilder()
 *     .sheet("2.0 8ª 091126") {
 *         cell("A1", "FULL BODY 1")
 *         row(2, "ESERCIZIO", "SET", "TARGET REP")   // row number is 1-based, values start at column A
 *         row(3, "PULL UP", 3.0, "6-8")
 *     }
 *     .build()
 * ```
 * Values may be String, Number, Boolean or null (skipped). Strings become shared or inline strings
 * (see [useSharedStrings]); numbers are written as numeric cells with their `toString()` text.
 */
class XlsxTestBuilder {
    class SheetBuilder internal constructor() {
        internal val cells = sortedMapOf<Int, java.util.SortedMap<Int, Any>>()

        /** Sets a cell by A1-style reference. A null [value] leaves the cell empty. */
        fun cell(ref: String, value: Any?): SheetBuilder {
            val split = ref.indexOfFirst { it.isDigit() }
            require(split > 0) { "Invalid cell reference: $ref" }
            return cell(ref.substring(split).toInt(), columnIndex(ref.substring(0, split)), value)
        }

        /** Sets a cell by 1-based [row] and 0-based [col]. */
        fun cell(row: Int, col: Int, value: Any?): SheetBuilder {
            require(row >= 1 && col >= 0)
            if (value != null) cells.getOrPut(row) { sortedMapOf() }[col] = value
            return this
        }

        /** Writes [values] on the 1-based [row] starting at column A (null values leave gaps). */
        fun row(row: Int, vararg values: Any?): SheetBuilder {
            values.forEachIndexed { i, v -> cell(row, i, v) }
            return this
        }
    }

    private class Sheet(val name: String, val body: SheetBuilder)

    private val sheets = mutableListOf<Sheet>()
    private var shared = true
    private var fileNumbers: List<Int>? = null
    private var absoluteTargets = false

    fun sheet(name: String, block: SheetBuilder.() -> Unit = {}): XlsxTestBuilder {
        sheets += Sheet(name, SheetBuilder().apply(block))
        return this
    }

    /** true (default): strings go to sharedStrings.xml; false: inline strings. */
    fun useSharedStrings(value: Boolean): XlsxTestBuilder {
        shared = value
        return this
    }

    /** Numbers of the worksheets/sheetN.xml files, one per sheet in order (default 1..n). */
    fun sheetFileNumbers(vararg numbers: Int): XlsxTestBuilder {
        fileNumbers = numbers.toList()
        return this
    }

    /** Use absolute relationship targets (`/xl/worksheets/sheetN.xml`). */
    fun absoluteTargets(value: Boolean): XlsxTestBuilder {
        absoluteTargets = value
        return this
    }

    fun build(): ByteArray {
        require(sheets.isNotEmpty()) { "At least one sheet is required" }
        val numbers = fileNumbers ?: sheets.indices.map { it + 1 }
        require(numbers.size == sheets.size) { "sheetFileNumbers must match sheet count" }

        val sst = LinkedHashMap<String, Int>()
        val sheetXml = sheets.map { renderSheet(it.body, sst) }

        val files = LinkedHashMap<String, String>()
        files["[Content_Types].xml"] = contentTypes(numbers)
        files["_rels/.rels"] = HEADER +
            """<Relationships xmlns="$PKG_REL_NS"><Relationship Id="rId1" Type="$REL_NS/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
        files["xl/workbook.xml"] = workbookXml()
        files["xl/_rels/workbook.xml.rels"] = workbookRels(numbers)
        sheetXml.forEachIndexed { i, xml -> files["xl/worksheets/sheet${numbers[i]}.xml"] = xml }
        if (shared) files["xl/sharedStrings.xml"] = sharedStringsXml(sst)
        return zip(files)
    }

    private fun renderSheet(sheet: SheetBuilder, sst: MutableMap<String, Int>): String {
        val sb = StringBuilder(HEADER)
        sb.append("""<worksheet xmlns="$MAIN_NS"><sheetData>""")
        for ((row, cols) in sheet.cells) {
            sb.append("""<row r="$row">""")
            for ((col, value) in cols) {
                val ref = columnLetters(col) + row
                when (value) {
                    is Number -> sb.append("""<c r="$ref"><v>$value</v></c>""")
                    is Boolean -> sb.append("""<c r="$ref" t="b"><v>${if (value) 1 else 0}</v></c>""")
                    else -> {
                        val text = value.toString()
                        if (shared) {
                            val idx = sst.getOrPut(text) { sst.size }
                            sb.append("""<c r="$ref" t="s"><v>$idx</v></c>""")
                        } else {
                            sb.append("""<c r="$ref" t="inlineStr"><is>${t(text)}</is></c>""")
                        }
                    }
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun sharedStringsXml(sst: Map<String, Int>): String {
        val sb = StringBuilder(HEADER)
        sb.append("""<sst xmlns="$MAIN_NS" count="${sst.size}" uniqueCount="${sst.size}">""")
        for (s in sst.keys) sb.append("<si>${t(s)}</si>")
        sb.append("</sst>")
        return sb.toString()
    }

    private fun workbookXml(): String {
        val sb = StringBuilder(HEADER)
        sb.append("""<workbook xmlns="$MAIN_NS" xmlns:r="$REL_NS"><sheets>""")
        sheets.forEachIndexed { i, s ->
            sb.append("""<sheet name="${escape(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        sb.append("</sheets></workbook>")
        return sb.toString()
    }

    private fun workbookRels(numbers: List<Int>): String {
        val sb = StringBuilder(HEADER)
        sb.append("""<Relationships xmlns="$PKG_REL_NS">""")
        numbers.forEachIndexed { i, n ->
            val target = if (absoluteTargets) "/xl/worksheets/sheet$n.xml" else "worksheets/sheet$n.xml"
            sb.append("""<Relationship Id="rId${i + 1}" Type="$REL_NS/worksheet" Target="$target"/>""")
        }
        if (shared) {
            sb.append("""<Relationship Id="rId${numbers.size + 1}" Type="$REL_NS/sharedStrings" Target="sharedStrings.xml"/>""")
        }
        sb.append("</Relationships>")
        return sb.toString()
    }

    private fun contentTypes(numbers: List<Int>): String {
        val sb = StringBuilder(HEADER)
        sb.append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        sb.append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        sb.append("""<Default Extension="xml" ContentType="application/xml"/>""")
        sb.append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        for (n in numbers) {
            sb.append("""<Override PartName="/xl/worksheets/sheet$n.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        if (shared) {
            sb.append("""<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>""")
        }
        sb.append("</Types>")
        return sb.toString()
    }

    companion object {
        private const val HEADER = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
        private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private const val PKG_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"

        /** Builds a zip with arbitrary entries (UTF-8), for hand-crafted or malformed fixtures. */
        fun zip(entries: Map<String, String>): ByteArray {
            val bos = ByteArrayOutputStream()
            ZipOutputStream(bos).use { z ->
                for ((name, content) in entries) {
                    z.putNextEntry(ZipEntry(name))
                    z.write(content.toByteArray(Charsets.UTF_8))
                    z.closeEntry()
                }
            }
            return bos.toByteArray()
        }

        private fun t(text: String) = """<t xml:space="preserve">${escape(text)}</t>"""

        private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;")

        private fun columnIndex(letters: String): Int =
            letters.uppercase().fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) } - 1

        private fun columnLetters(index: Int): String {
            val sb = StringBuilder()
            var n = index + 1
            while (n > 0) {
                sb.append('A' + (n - 1) % 26)
                n = (n - 1) / 26
            }
            return sb.reverse().toString()
        }
    }
}
