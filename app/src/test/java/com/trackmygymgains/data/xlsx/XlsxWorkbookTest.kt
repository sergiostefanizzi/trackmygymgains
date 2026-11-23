package com.trackmygymgains.data.xlsx

import com.trackmygymgains.testing.XlsxTestBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class XlsxWorkbookTest {
    private val ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private val rns = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private fun open(bytes: ByteArray) = XlsxWorkbook.open(ByteArrayInputStream(bytes))

    /** Hand-written package: one sheet "S" at xl/worksheets/sheet1.xml with the given body. */
    private fun raw(sheetBody: String, sharedStrings: String? = null): ByteArray {
        val files = linkedMapOf(
            "xl/workbook.xml" to """<workbook xmlns="$ns" xmlns:r="$rns"><sheets><sheet name="S" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="x" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to """<worksheet xmlns="$ns"><sheetData>$sheetBody</sheetData></worksheet>""",
        )
        if (sharedStrings != null) files["xl/sharedStrings.xml"] = sharedStrings
        return XlsxTestBuilder.zip(files)
    }

    @Test
    fun sheetNamesInOrderWithUnicode() {
        val wb = open(
            XlsxTestBuilder().sheet("2.0 8ª 091126") { cell("A1", "x") }.sheet("old") {}.sheet("à°") {}.build(),
        )
        assertEquals(listOf("2.0 8ª 091126", "old", "à°"), wb.sheetNames)
        assertEquals("2.0 8ª 091126", wb.sheet(0).name)
        assertEquals("x", wb.sheet(0).cell("A1"))
    }

    @Test
    fun sheetsResolvedViaRelsWhenFileNumberingDiffers() {
        val bytes = XlsxTestBuilder()
            .sheet("first") { cell("A1", "one") }
            .sheet("second") { cell("A1", "two") }
            .sheetFileNumbers(2, 1)
            .build()
        val wb = open(bytes)
        assertEquals("one", wb.sheet(0).cell("A1"))
        assertEquals("two", wb.sheet(1).cell("A1"))
    }

    @Test
    fun absoluteRelationshipTargets() {
        val wb = open(XlsxTestBuilder().sheet("a") { cell("A1", "v") }.absoluteTargets(true).build())
        assertEquals("v", wb.sheet(0).cell("A1"))
    }

    @Test
    fun sharedAndInlineStringsRoundTrip() {
        for (shared in listOf(true, false)) {
            val wb = open(
                XlsxTestBuilder().useSharedStrings(shared)
                    .sheet("s") { cell("A1", "FULL BODY 1"); cell("B2", "3' & <ª>"); row(3, "PULL UP", 3.0, null, "x") }
                    .build(),
            )
            val s = wb.sheet(0)
            assertEquals("FULL BODY 1", s.cell("A1"))
            assertEquals("3' & <ª>", s.cell(1, 1))
            assertEquals("PULL UP", s.cell("A3"))
            assertEquals("3.0", s.cell("B3"))
            assertNull(s.cell("C3"))
            assertEquals("x", s.cell("D3"))
            assertEquals(3, s.rowCount)
        }
    }

    @Test
    fun richTextRunsConcatenatedAndPhoneticSkipped() {
        val sst = """<sst xmlns="$ns"><si><r><t>Hel</t></r><r><rPr/><t xml:space="preserve">lo </t></r><r><t>world</t></r></si>""" +
            """<si><t>base</t><rPh sb="0" eb="1"><t>PHONETIC</t></rPh></si></sst>"""
        val wb = open(raw("""<row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>""", sst))
        assertEquals("Hello world", wb.sheet(0).cell("A1"))
        assertEquals("base", wb.sheet(0).cell("B1"))
    }

    @Test
    fun inlineRichText() {
        val wb = open(raw("""<row r="1"><c r="A1" t="inlineStr"><is><r><t>a</t></r><r><t>b</t></r></is></c></row>"""))
        assertEquals("ab", wb.sheet(0).cell("A1"))
    }

    @Test
    fun cellTypes() {
        val wb = open(
            raw(
                """<row r="1"><c r="A1"><v>37.5</v></c><c r="B1" t="b"><v>1</v></c><c r="C1" t="b"><v>0</v></c>""" +
                    """<c r="D1" t="e"><v>#DIV/0!</v></c><c r="E1" t="str"><v>formula text</v></c><c r="F1"/>""" +
                    """<c r="G1" t="inlineStr"><is><t></t></is></c><c r="H1"><f>1+2</f><v>3</v></c></row>""",
            ),
        )
        val s = wb.sheet(0)
        assertEquals("37.5", s.cell("A1"))
        assertEquals("TRUE", s.cell("B1"))
        assertEquals("FALSE", s.cell("C1"))
        assertNull(s.cell("D1"))
        assertEquals("formula text", s.cell("E1"))
        assertNull(s.cell("F1"))
        assertNull(s.cell("G1"))
        assertEquals("3", s.cell("H1"))
        assertNull(s.cell("Z99"))
        assertNull(s.cell(500, 500))
    }

    @Test
    fun rawTextIsNotTrimmed() {
        val wb = open(raw("""<row r="1"><c r="A1" t="inlineStr"><is><t xml:space="preserve">  a </t></is></c></row>"""))
        assertEquals("  a ", wb.sheet(0).cell("A1"))
    }

    @Test
    fun cellsAndRowsWithoutRefs() {
        val wb = open(
            raw(
                """<row><c t="inlineStr"><is><t>a</t></is></c><c><v>2</v></c></row>""" +
                    """<row r="5"><c r="C5"><v>9</v></c><c><v>10</v></c></row><row><c><v>11</v></c></row>""",
            ),
        )
        val s = wb.sheet(0)
        assertEquals("a", s.cell("A1"))
        assertEquals("2", s.cell("B1"))
        assertEquals("9", s.cell("C5"))
        assertEquals("10", s.cell("D5"))
        assertEquals("11", s.cell("A6"))
        assertEquals(6, s.rowCount)
    }

    @Test
    fun emptySheetHasZeroRows() {
        assertEquals(0, open(XlsxTestBuilder().sheet("e").build()).sheet(0).rowCount)
    }

    @Test
    fun farCellReference() {
        val wb = open(XlsxTestBuilder().sheet("s") { cell("AB12", "far"); cell("AL2", "al") }.build())
        assertEquals("far", wb.sheet(0).cell("AB12"))
        assertEquals("far", wb.sheet(0).cell(11, 27))
        assertEquals("al", wb.sheet(0).cell(1, 37))
        assertEquals(12, wb.sheet(0).rowCount)
    }

    @Test
    fun columnLetterConversions() {
        val cases = mapOf("A" to 0, "Z" to 25, "AA" to 26, "AL" to 37, "AZ" to 51, "BA" to 52, "XFD" to 16383)
        for ((letters, idx) in cases) {
            assertEquals(idx, ColumnRef.toIndex(letters))
            assertEquals(letters, ColumnRef.toLetters(idx))
        }
        assertEquals(37, ColumnRef.toIndex("al"))
        assertEquals(11 to 27, ColumnRef.parseCellRef("AB12"))
        assertNull(ColumnRef.parseCellRef("12"))
        assertNull(ColumnRef.parseCellRef("A0"))
        assertNull(ColumnRef.parseCellRef("A"))
    }

    @Test
    fun invalidCellRefThrows() {
        val s = open(XlsxTestBuilder().sheet("s").build()).sheet(0)
        assertThrows(IllegalArgumentException::class.java) { s.cell("nope") }
    }

    // ---- errors ----

    @Test
    fun randomBytesAreNotAnXlsx() {
        val bytes = ByteArray(500) { (it * 31 + 7).toByte() }
        assertThrows(XlsxException.NotAnXlsx::class.java) { open(bytes) }
        assertThrows(XlsxException.NotAnXlsx::class.java) { open(ByteArray(0)) }
        assertThrows(XlsxException.NotAnXlsx::class.java) { open("plain text".toByteArray()) }
    }

    @Test
    fun zipWithoutWorkbookIsNotAnXlsx() {
        assertThrows(XlsxException.NotAnXlsx::class.java) {
            open(XlsxTestBuilder.zip(mapOf("hello.txt" to "hi")))
        }
    }

    @Test
    fun malformedSheetXmlIsCorrupted() {
        val wb = open(raw("""<row r="1"><c r="A1"><v>1</c></row>"""))
        assertThrows(XlsxException.Corrupted::class.java) { wb.sheet(0) }
    }

    @Test
    fun malformedWorkbookIsCorrupted() {
        val bytes = XlsxTestBuilder.zip(mapOf("xl/workbook.xml" to "<workbook><sheets>"))
        assertThrows(XlsxException.Corrupted::class.java) { open(bytes) }
    }

    @Test
    fun brokenRelationshipIsCorrupted() {
        val bytes = XlsxTestBuilder.zip(
            mapOf(
                "xl/workbook.xml" to """<workbook xmlns="$ns" xmlns:r="$rns"><sheets><sheet name="S" sheetId="1" r:id="rId9"/></sheets></workbook>""",
                "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>""",
            ),
        )
        assertThrows(XlsxException.Corrupted::class.java) { open(bytes) }
    }

    @Test
    fun missingSheetPartIsCorrupted() {
        val bytes = XlsxTestBuilder.zip(
            mapOf(
                "xl/workbook.xml" to """<workbook xmlns="$ns" xmlns:r="$rns"><sheets><sheet name="S" sheetId="1" r:id="rId1"/></sheets></workbook>""",
                "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="x" Target="worksheets/sheet1.xml"/></Relationships>""",
            ),
        )
        val wb = open(bytes)
        assertThrows(XlsxException.Corrupted::class.java) { wb.sheet(0) }
    }

    @Test
    fun badSharedStringIndexIsCorrupted() {
        val wb = open(raw("""<row r="1"><c r="A1" t="s"><v>7</v></c></row>""", """<sst xmlns="$ns"><si><t>a</t></si></sst>"""))
        assertThrows(XlsxException.Corrupted::class.java) { wb.sheet(0) }
    }

    @Test
    fun badSheetIndexThrowsSheetNotFound() {
        val wb = open(XlsxTestBuilder().sheet("a").build())
        assertThrows(XlsxException.SheetNotFound::class.java) { wb.sheet(1) }
        assertThrows(XlsxException.SheetNotFound::class.java) { wb.sheet(-1) }
    }

    @Test
    fun xxePayloadIsNotResolved() {
        val secret = java.io.File.createTempFile("xxe", ".txt").apply {
            writeText("TOP-SECRET-CONTENT")
            deleteOnExit()
        }
        val sst = """<?xml version="1.0"?><!DOCTYPE sst [<!ENTITY xxe SYSTEM "${secret.toURI()}">]>""" +
            """<sst xmlns="$ns"><si><t>&xxe;</t></si></sst>"""
        val bytes = raw("""<row r="1"><c r="A1" t="s"><v>0</v></c></row>""", sst)
        try {
            val value = open(bytes).sheet(0).cell("A1")
            assertEquals(false, value?.contains("TOP-SECRET") ?: false)
        } catch (e: XlsxException.Corrupted) {
            assertEquals(false, generateSequence<Throwable>(e) { it.cause }.any { it.message?.contains("TOP-SECRET") == true })
        }
    }

    @Test
    fun zipBombEntryIsRejected() {
        val big = "<a>" + "x".repeat(1024 * 1024) + "</a>"
        val entries = linkedMapOf<String, String>()
        entries["xl/workbook.xml"] = "<workbook/>"
        // 21 MB of highly compressible data in one entry
        val bomb = StringBuilder()
        repeat(21) { bomb.append(big) }
        entries["xl/worksheets/bomb.xml"] = bomb.toString()
        assertThrows(XlsxException.Corrupted::class.java) { open(XlsxTestBuilder.zip(entries)) }
    }
}
