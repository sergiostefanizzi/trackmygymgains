package com.trackmygymgains.data.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Content of a file chosen by the user. */
class PickedFile(val displayName: String?, val bytes: ByteArray)

/** Why a file could not be read. */
class PlanFileException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { TOO_BIG, UNREADABLE }
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
            if (size > maxBytes) throw PlanFileException(PlanFileException.Reason.TOO_BIG)

            val input = resolver.openInputStream(parsed)
                ?: throw PlanFileException(PlanFileException.Reason.UNREADABLE)
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
    }
}
