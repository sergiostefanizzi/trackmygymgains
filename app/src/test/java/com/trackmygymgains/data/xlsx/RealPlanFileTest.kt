package com.trackmygymgains.data.xlsx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Reads the owner's real (private, gitignored) plan when present; skipped otherwise. */
class RealPlanFileTest {
    @Test
    fun readsRealPlanFirstSheet() {
        val file = File(System.getProperty("user.dir"), "../references/scheda_gym.xlsx")
        assumeTrue(file.exists())

        val wb = file.inputStream().use { XlsxWorkbook.open(it) }
        assertEquals(10, wb.sheetNames.size)
        assertTrue(wb.sheetNames[0].startsWith("2.0"))

        val s = wb.sheet(0)
        assertEquals("FULL BODY 1", s.cell("A1"))
        assertEquals("ESERCIZIO", s.cell("A2"))
        assertEquals("REST", s.cell("E2"))
        assertEquals("PULL UP", s.cell("A3"))
        assertEquals("3'", s.cell("E3"))
        assertEquals(3.0, s.cell("B3")!!.trim().toDouble(), 0.0)
        assertEquals("FULL BODY 2", s.cell("A13"))
        assertEquals("FULL BODY 3", s.cell("A25"))
    }
}
