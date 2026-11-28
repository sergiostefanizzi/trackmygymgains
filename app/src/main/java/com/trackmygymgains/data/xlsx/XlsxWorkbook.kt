package com.trackmygymgains.data.xlsx

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

/**
 * Minimal xlsx reader built only on `java.util.zip` and SAX, so it behaves the same on Android
 * and in plain JVM tests.
 */
class XlsxWorkbook private constructor(
    private val sheetRefs: List<SheetRef>,
    private val files: Map<String, ByteArray>,
    private val sharedStrings: List<String>,
) {
    internal class SheetRef(val name: String, val path: String)

    /** Sheet names in workbook order. */
    val sheetNames: List<String> = sheetRefs.map { it.name }

    private val cache = HashMap<Int, SheetGrid>()

    /** @throws XlsxException.SheetNotFound for an invalid [index]; [XlsxException.Corrupted] for a bad sheet. */
    fun sheet(index: Int): SheetGrid {
        if (index !in sheetRefs.indices) {
            throw XlsxException.SheetNotFound("Sheet index $index not found (workbook has ${sheetRefs.size} sheets)")
        }
        return cache.getOrPut(index) {
            val ref = sheetRefs[index]
            val bytes = files[ref.path]
                ?: throw XlsxException.Corrupted("Missing sheet part ${ref.path} for sheet '${ref.name}'")
            val handler = SheetHandler(sharedStrings)
            parse(bytes, handler, "sheet '${ref.name}'")
            SheetGrid(ref.name, handler.cells)
        }
    }

    companion object {
        private const val MAX_ENTRY_BYTES = 20L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 64L * 1024 * 1024
        private const val MAX_ENTRIES = 5000

        private const val WORKBOOK = "xl/workbook.xml"
        private const val WORKBOOK_RELS = "xl/_rels/workbook.xml.rels"
        private const val SHARED_STRINGS = "xl/sharedStrings.xml"
        private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

        /** Reads the whole archive from [input]. The caller owns (and closes) the stream. */
        fun open(input: InputStream): XlsxWorkbook {
            val files = readZip(input)
            val workbookBytes = files[WORKBOOK]
                ?: throw XlsxException.NotAnXlsx("Missing $WORKBOOK: not an xlsx file")

            val wbHandler = WorkbookHandler()
            parse(workbookBytes, wbHandler, WORKBOOK)

            val relsBytes = files[WORKBOOK_RELS]
            val rels = HashMap<String, String>()
            if (relsBytes != null) {
                val relsHandler = RelsHandler()
                parse(relsBytes, relsHandler, WORKBOOK_RELS)
                rels.putAll(relsHandler.targets)
            }

            val refs = wbHandler.sheets.map { (name, rid) ->
                val target = rels[rid]
                    ?: throw XlsxException.Corrupted("Sheet '$name' references unknown relationship '$rid'")
                SheetRef(name, resolveTarget(target))
            }

            val shared = files[SHARED_STRINGS]?.let {
                val h = SharedStringsHandler()
                parse(it, h, SHARED_STRINGS)
                h.strings
            } ?: emptyList()

            return XlsxWorkbook(refs, files, shared)
        }

        private fun resolveTarget(target: String): String {
            val t = target.replace('\\', '/')
            val full = if (t.startsWith("/")) t.substring(1) else "xl/$t"
            val parts = ArrayList<String>()
            for (seg in full.split('/')) {
                when (seg) {
                    "", "." -> {}
                    ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                    else -> parts.add(seg)
                }
            }
            return parts.joinToString("/")
        }

        private fun readZip(input: InputStream): Map<String, ByteArray> {
            val files = HashMap<String, ByteArray>()
            var total = 0L
            var entries = 0
            try {
                val zip = ZipInputStream(input)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++entries > MAX_ENTRIES) {
                        throw XlsxException.Corrupted("Too many zip entries")
                    }
                    if (entry.isDirectory) continue
                    val name = entry.name.replace('\\', '/').trimStart('/')
                    val wanted = name.endsWith(".xml") || name.endsWith(".rels")
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var size = 0L
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        size += n
                        total += n
                        if (size > MAX_ENTRY_BYTES) {
                            throw XlsxException.Corrupted("Zip entry '$name' is too large")
                        }
                        if (total > MAX_TOTAL_BYTES) {
                            throw XlsxException.Corrupted("Archive is too large when uncompressed")
                        }
                        if (wanted) out.write(buf, 0, n)
                    }
                    if (wanted) files[name] = out.toByteArray()
                }
            } catch (e: ZipException) {
                throw XlsxException.NotAnXlsx("Not a valid zip archive", e)
            } catch (e: IOException) {
                throw XlsxException.NotAnXlsx("Cannot read archive", e)
            }
            return files
        }

        private fun parse(bytes: ByteArray, handler: DefaultHandler, what: String) {
            try {
                val parser = newFactory().newSAXParser()
                val source = InputSource(ByteArrayInputStream(bytes))
                // Defense in depth: never resolve any external entity.
                val reader = parser.xmlReader
                reader.setEntityResolver { _, _ -> InputSource(StringReader("")) }
                reader.contentHandler = handler
                reader.errorHandler = handler
                reader.parse(source)
            } catch (e: XlsxException) {
                throw e
            } catch (e: SAXException) {
                throw XlsxException.Corrupted("Malformed XML in $what", e)
            } catch (e: IOException) {
                throw XlsxException.Corrupted("Cannot read $what", e)
            } catch (e: ParserConfigurationException) {
                throw XlsxException.Corrupted("XML parser unavailable", e)
            }
        }

        private fun newFactory(): SAXParserFactory {
            val f = SAXParserFactory.newInstance()
            f.isNamespaceAware = true
            // Not every parser (e.g. Android's) supports every feature: best effort.
            for ((feature, value) in listOf(
                "http://apache.org/xml/features/disallow-doctype-decl" to true,
                "http://xml.org/sax/features/external-general-entities" to false,
                "http://xml.org/sax/features/external-parameter-entities" to false,
                "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
            )) {
                try {
                    f.setFeature(feature, value)
                } catch (_: Exception) {
                }
            }
            try {
                f.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true)
            } catch (_: Exception) {
            }
            return f
        }

        // ---- handlers ----

        private class WorkbookHandler : DefaultHandler() {
            val sheets = ArrayList<Pair<String, String>>()
            override fun startElement(uri: String?, localName: String?, qName: String?, atts: Attributes) {
                if (localName == "sheet") {
                    val name = atts.getValue("name") ?: throw XlsxException.Corrupted("Sheet without name")
                    val rid = atts.getValue(REL_NS, "id") ?: throw XlsxException.Corrupted("Sheet '$name' without r:id")
                    sheets.add(name to rid)
                }
            }
        }

        private class RelsHandler : DefaultHandler() {
            val targets = HashMap<String, String>()
            override fun startElement(uri: String?, localName: String?, qName: String?, atts: Attributes) {
                if (localName == "Relationship") {
                    val id = atts.getValue("Id") ?: return
                    val target = atts.getValue("Target") ?: return
                    targets[id] = target
                }
            }
        }

        private class SharedStringsHandler : DefaultHandler() {
            val strings = ArrayList<String>()
            private var current: StringBuilder? = null
            private var inT = false
            private var phoneticDepth = 0

            override fun startElement(uri: String?, localName: String?, qName: String?, atts: Attributes) {
                when (localName) {
                    "si" -> current = StringBuilder()
                    "rPh" -> phoneticDepth++
                    "t" -> inT = true
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inT && phoneticDepth == 0) current?.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (localName) {
                    "t" -> inT = false
                    "rPh" -> phoneticDepth--
                    "si" -> {
                        strings.add(current?.toString() ?: "")
                        current = null
                    }
                }
            }
        }

        private class SheetHandler(private val shared: List<String>) : DefaultHandler() {
            val cells = HashMap<Int, HashMap<Int, String>>()

            private var rowIdx = -1
            private var nextCol = 0
            private var colIdx = 0
            private var cellType: String? = null
            private var inCell = false
            private var inV = false
            private var inIs = false
            private var inT = false
            private var phoneticDepth = 0
            private val text = StringBuilder()

            override fun startElement(uri: String?, localName: String?, qName: String?, atts: Attributes) {
                when (localName) {
                    "row" -> {
                        val r = atts.getValue("r")?.toIntOrNull()
                        rowIdx = if (r != null && r >= 1) r - 1 else rowIdx + 1
                        nextCol = 0
                    }
                    "c" -> {
                        val ref = atts.getValue("r")
                        val parsed = ref?.let { ColumnRef.parseCellRef(it) }
                        colIdx = parsed?.second ?: nextCol
                        nextCol = colIdx + 1
                        cellType = atts.getValue("t")
                        inCell = true
                        text.setLength(0)
                    }
                    "v" -> if (inCell) inV = true
                    "is" -> if (inCell) inIs = true
                    "rPh" -> phoneticDepth++
                    "t" -> if (inIs) inT = true
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inV || (inT && phoneticDepth == 0)) text.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (localName) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "is" -> inIs = false
                    "rPh" -> phoneticDepth--
                    "c" -> {
                        inCell = false
                        val value = resolve()
                        if (!value.isNullOrEmpty() && rowIdx >= 0) {
                            cells.getOrPut(rowIdx) { HashMap() }[colIdx] = value
                        }
                    }
                }
            }

            private fun resolve(): String? {
                val raw = text.toString()
                return when (cellType) {
                    "s" -> {
                        if (raw.isBlank()) return null
                        val i = raw.trim().toIntOrNull()
                            ?: throw XlsxException.Corrupted("Invalid shared string index '$raw'")
                        shared.getOrNull(i)
                            ?: throw XlsxException.Corrupted("Shared string index $i out of range")
                    }
                    "b" -> when (raw.trim()) {
                        "1" -> "TRUE"
                        "0" -> "FALSE"
                        else -> null
                    }
                    "e" -> null
                    else -> raw
                }
            }
        }
    }
}
