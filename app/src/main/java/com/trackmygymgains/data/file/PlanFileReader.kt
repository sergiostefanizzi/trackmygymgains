package com.trackmygymgains.data.file

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/** Content of a file chosen by the user. */
class PickedFile(val displayName: String?, val bytes: ByteArray)

/** Why a file could not be read. */
class PlanFileException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { TOO_BIG, UNREADABLE, UNSUPPORTED_GOOGLE_FILE }
}

/** Reads the bytes of a user-picked file (a SAF uri, as string). Throws [PlanFileException] on failure. */
interface PlanFileReader {
    suspend fun read(uri: String): PickedFile
}

class ContentResolverPlanFileReader(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxBytes: Int = MAX_BYTES,
) : PlanFileReader {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun read(uri: String): PickedFile = withContext(ioDispatcher) {
        try {
            val parsed = Uri.parse(uri)
            var name: String? = null
            var size: Long = -1
            var virtual = false
            try {
                resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                    ?.use { c ->
                        if (c.moveToFirst()) {
                            val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (n >= 0 && !c.isNull(n)) name = c.getString(n)
                            val s = c.getColumnIndex(OpenableColumns.SIZE)
                            if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                        }
                    }
            } catch (_: Exception) {
                // Metadata is optional: fall through and try to read the content anyway.
            }
            try {
                // Optional column (document providers only): absent -> not virtual.
                resolver.query(parsed, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)
                    ?.use { c ->
                        if (c.moveToFirst()) {
                            val f = c.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                            if (f >= 0 && !c.isNull(f)) {
                                virtual = c.getInt(f) and DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT != 0
                            }
                        }
                    }
            } catch (_: Exception) {
                // Not a document provider.
            }

            // The size of a virtual document (e.g. a Google Sheet) is unreliable: rely on the streaming cap.
            if (!virtual && size > maxBytes) throw PlanFileException(PlanFileException.Reason.TOO_BIG)

            var input: InputStream? = null
            if (!virtual) {
                input = try {
                    resolver.openInputStream(parsed)
                } catch (_: FileNotFoundException) {
                    null
                }
            }
            if (input == null) {
                // Virtual document (or a provider that refused a plain open): ask for an xlsx export.
                val exportable = try {
                    resolver.getStreamTypes(parsed, "*/*")?.any { it.equals(XLSX_MIME, ignoreCase = true) } == true
                } catch (_: Exception) {
                    false
                }
                if (!exportable) {
                    throw PlanFileException(
                        if (virtual) PlanFileException.Reason.UNSUPPORTED_GOOGLE_FILE
                        else PlanFileException.Reason.UNREADABLE,
                    )
                }
                input = resolver.openTypedAssetFileDescriptor(parsed, XLSX_MIME, null)?.createInputStream()
                    ?: throw PlanFileException(PlanFileException.Reason.UNREADABLE)
            }

            val out = ByteArrayOutputStream()
            input.use {
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = it.read(buf)
                    if (n < 0) break
                    if (out.size() + n > maxBytes) throw PlanFileException(PlanFileException.Reason.TOO_BIG)
                    out.write(buf, 0, n)
                }
            }
            // The display name is only shown in the UI: keep it as is (an exported Sheet has no extension).
            PickedFile(name, out.toByteArray())
        } catch (e: PlanFileException) {
            throw e
        } catch (e: IOException) {
            throw PlanFileException(PlanFileException.Reason.UNREADABLE, e)
        } catch (e: SecurityException) {
            throw PlanFileException(PlanFileException.Reason.UNREADABLE, e)
        } catch (e: IllegalArgumentException) {
            throw PlanFileException(PlanFileException.Reason.UNREADABLE, e)
        }
    }

    companion object {
        const val MAX_BYTES = 15 * 1024 * 1024
        const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
