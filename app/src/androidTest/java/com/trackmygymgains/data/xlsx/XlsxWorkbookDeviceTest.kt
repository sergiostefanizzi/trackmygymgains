package com.trackmygymgains.data.xlsx

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackmygymgains.testing.XlsxTestBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

/**
 * Runs the reader on Android's own SAX implementation (Expat-based), which differs from the JVM's
 * Xerces used by the unit tests: namespaces, attributes and parser features must behave the same.
 */
@RunWith(AndroidJUnit4::class)
class XlsxWorkbookDeviceTest {

    private fun open(bytes: ByteArray) = XlsxWorkbook.open(ByteArrayInputStream(bytes))

    private fun planLike(sharedStrings: Boolean) = XlsxTestBuilder()
        .useSharedStrings(sharedStrings)
        .sheetFileNumbers(3, 1)
        .absoluteTargets(true)
        .sheet("2.0 8ª 091126") {
            cell("A1", "FULL BODY 1")
            row(2, "ESERCIZIO", "SET", "TARGET REP", "TARGET RIR", "REST", "NOTE")
            row(3, "PULL UP", 3.0, "4-6", "1-2", "3'", "NO ELASTICO - CLASSICO")
            row(4, "ADDUCTOR MACHINE", 2.0, "8-10", "1-2", "/", "JUMP SET - 30\" DI REST")
        }
        .sheet("old") { cell("A1", "legacy") }
        .build()

    @Test
    fun readsPlanLikeSheetWithSharedStrings() = assertPlanLike(open(planLike(sharedStrings = true)))

    @Test
    fun readsPlanLikeSheetWithInlineStrings() = assertPlanLike(open(planLike(sharedStrings = false)))

    private fun assertPlanLike(wb: XlsxWorkbook) {
        assertEquals(listOf("2.0 8ª 091126", "old"), wb.sheetNames)
        val s = wb.sheet(0)
        assertEquals("FULL BODY 1", s.cell("A1"))
        assertEquals("REST", s.cell("E2"))
        assertEquals("PULL UP", s.cell("A3"))
        assertEquals(3.0, s.cell("B3")!!.toDouble(), 0.0)
        assertEquals("3'", s.cell("E3"))
        assertEquals("JUMP SET - 30\" DI REST", s.cell("F4"))
        assertNull(s.cell("G3"))
        assertEquals("legacy", wb.sheet(1).cell("A1"))
    }

    @Test
    fun nonZipIsRejected() {
        assertThrows(XlsxException.NotAnXlsx::class.java) { open("not a zip".toByteArray()) }
    }

    @Test
    fun malformedSheetIsCorrupted() {
        val ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        val rns = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        val bytes = XlsxTestBuilder.zip(
            mapOf(
                "xl/workbook.xml" to """<workbook xmlns="$ns" xmlns:r="$rns"><sheets><sheet name="S" sheetId="1" r:id="rId1"/></sheets></workbook>""",
                "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="x" Target="worksheets/sheet1.xml"/></Relationships>""",
                "xl/worksheets/sheet1.xml" to """<worksheet xmlns="$ns"><sheetData><row r="1"><c r="A1"""",
            ),
        )
        val wb = open(bytes)
        assertThrows(XlsxException.Corrupted::class.java) { wb.sheet(0) }
    }
}
