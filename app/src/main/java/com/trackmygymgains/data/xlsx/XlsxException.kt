package com.trackmygymgains.data.xlsx

/** Errors raised by [XlsxWorkbook] while reading an xlsx file. */
sealed class XlsxException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The input is not a zip archive, or it has no `xl/workbook.xml`. */
    class NotAnXlsx(message: String, cause: Throwable? = null) : XlsxException(message, cause)

    /** The archive looks like an xlsx but its content is malformed or inconsistent. */
    class Corrupted(message: String, cause: Throwable? = null) : XlsxException(message, cause)

    /** The requested sheet index does not exist. */
    class SheetNotFound(message: String, cause: Throwable? = null) : XlsxException(message, cause)
}
