package com.trackmygymgains.data.file

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackmygymgains.data.file.ContentResolverPlanFileReader.Companion.XLSX_MIME
import com.trackmygymgains.data.plan.WorkoutPlanParser
import com.trackmygymgains.data.xlsx.XlsxWorkbook
import com.trackmygymgains.testing.referencePlanExpected
import com.trackmygymgains.testing.referenceSheetXlsx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.io.File
import java.io.FileNotFoundException

private const val AUTHORITY = "com.trackmygymgains.test.files"

/** Serves documents keyed by the last path segment of the uri. */
class FakeDocProvider : ContentProvider() {
    class Doc(
        val name: String,
        val bytes: ByteArray,
        val virtual: Boolean,
        val exportTypes: Array<String>? = null,
        val reportedSize: Long? = null,
        val hasFlagsColumn: Boolean = true,
    )

    override fun onCreate() = true

    private fun doc(uri: Uri): Doc? = docs[uri.lastPathSegment]

    private fun temp(bytes: ByteArray): File =
        File.createTempFile("doc", ".bin", context!!.cacheDir).also { it.writeBytes(bytes); it.deleteOnExit() }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, order: String?): Cursor? {
        val d = doc(uri) ?: return null
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        if (!d.hasFlagsColumn && DocumentsContract.Document.COLUMN_FLAGS in cols) {
            throw IllegalArgumentException("no such column")
        }
        val row = cols.map {
            when (it) {
                OpenableColumns.DISPLAY_NAME -> d.name
                OpenableColumns.SIZE -> d.reportedSize ?: d.bytes.size.toLong()
                DocumentsContract.Document.COLUMN_FLAGS ->
                    if (d.virtual) DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT else 0
                else -> null
            }
        }
        return MatrixCursor(cols).also { it.addRow(row) }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val d = doc(uri)
        if (d == null || d.virtual) throw FileNotFoundException("not a regular file")
        return ParcelFileDescriptor.open(temp(d.bytes), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getStreamTypes(uri: Uri, mimeTypeFilter: String): Array<String>? = doc(uri)?.exportTypes

    override fun openTypedAssetFile(uri: Uri, mimeTypeFilter: String, opts: Bundle?): AssetFileDescriptor? {
        val d = doc(uri)
        // ContentResolver.openInputStream goes through here with "*/*": regular files are served as is.
        if (d != null && !d.virtual && mimeTypeFilter == "*/*") {
            return AssetFileDescriptor(openFile(uri, "r"), 0, AssetFileDescriptor.UNKNOWN_LENGTH)
        }
        if (d == null || d.exportTypes?.contains(mimeTypeFilter) != true) throw FileNotFoundException("no export")
        val pfd = ParcelFileDescriptor.open(temp(d.bytes), ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?) = 0

    companion object {
        @Volatile var docs: Map<String, Doc> = emptyMap()
    }
}

@RunWith(AndroidJUnit4::class)
class ContentResolverPlanFileReaderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val xlsx = referenceSheetXlsx()

    @Before fun setUp() {
        Robolectric.setupContentProvider(FakeDocProvider::class.java, AUTHORITY)
    }

    @After fun tearDown() {
        FakeDocProvider.docs = emptyMap()
    }

    private fun uri(key: String) = "content://$AUTHORITY/doc/$key"

    private fun reader(maxBytes: Int = ContentResolverPlanFileReader.MAX_BYTES) =
        ContentResolverPlanFileReader(context, Dispatchers.Unconfined, maxBytes)

    private fun read(key: String, maxBytes: Int = ContentResolverPlanFileReader.MAX_BYTES) =
        runBlocking { reader(maxBytes).read(uri(key)) }

    private fun reasonOf(key: String, maxBytes: Int = ContentResolverPlanFileReader.MAX_BYTES): PlanFileException.Reason =
        try {
            read(key, maxBytes)
            throw AssertionError("expected PlanFileException")
        } catch (e: PlanFileException) {
            e.reason
        }

    @Test fun regularXlsxIsRead() {
        FakeDocProvider.docs = mapOf("a" to FakeDocProvider.Doc("scheda.xlsx", xlsx, virtual = false))
        val f = read("a")
        assertEquals("scheda.xlsx", f.displayName)
        assertArrayEquals(xlsx, f.bytes)
    }

    @Test fun regularFileWithoutFlagsColumnIsRead() {
        FakeDocProvider.docs = mapOf("a" to FakeDocProvider.Doc("scheda.xlsx", xlsx, virtual = false, hasFlagsColumn = false))
        assertArrayEquals(xlsx, read("a").bytes)
    }

    @Test fun virtualGoogleSheetIsExportedToXlsx() {
        FakeDocProvider.docs = mapOf(
            "g" to FakeDocProvider.Doc(
                "Scheda Fogli", xlsx, virtual = true,
                exportTypes = arrayOf("application/pdf", XLSX_MIME), reportedSize = 0,
            ),
        )
        val f = read("g")
        assertEquals("Scheda Fogli", f.displayName)
        assertArrayEquals(xlsx, f.bytes)
        val plan = WorkoutPlanParser.parse(XlsxWorkbook.open(f.bytes.inputStream()))
        assertEquals(referencePlanExpected, plan.days)
    }

    @Test fun virtualDocWithoutXlsxExportIsUnsupported() {
        FakeDocProvider.docs = mapOf(
            "p" to FakeDocProvider.Doc("Doc", xlsx, virtual = true, exportTypes = arrayOf("application/pdf")),
            "n" to FakeDocProvider.Doc("Doc", xlsx, virtual = true, exportTypes = null),
        )
        assertEquals(PlanFileException.Reason.UNSUPPORTED_GOOGLE_FILE, reasonOf("p"))
        assertEquals(PlanFileException.Reason.UNSUPPORTED_GOOGLE_FILE, reasonOf("n"))
    }

    @Test fun tooBigRegularAndVirtual() {
        FakeDocProvider.docs = mapOf(
            // Regular file whose size column lies: caught by the streaming cap.
            "r" to FakeDocProvider.Doc("big.xlsx", xlsx, virtual = false, reportedSize = 1),
            "rs" to FakeDocProvider.Doc("big.xlsx", xlsx, virtual = false),
            "v" to FakeDocProvider.Doc("Big", xlsx, virtual = true, exportTypes = arrayOf(XLSX_MIME), reportedSize = 0),
        )
        val cap = xlsx.size - 1
        assertEquals(PlanFileException.Reason.TOO_BIG, reasonOf("r", cap))
        assertEquals(PlanFileException.Reason.TOO_BIG, reasonOf("rs", cap))
        assertEquals(PlanFileException.Reason.TOO_BIG, reasonOf("v", cap))
    }

    @Test fun unknownUriIsUnreadable() {
        FakeDocProvider.docs = emptyMap()
        assertEquals(PlanFileException.Reason.UNREADABLE, reasonOf("missing"))
    }
}
