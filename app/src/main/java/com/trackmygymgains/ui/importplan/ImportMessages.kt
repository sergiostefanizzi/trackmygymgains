package com.trackmygymgains.ui.importplan

/** User-facing (Italian) error messages produced by [ImportViewModel]. */
internal object ImportMessages {
    const val NOT_XLSX = "Il file selezionato non è un file Excel (.xlsx). Scegli un altro file."
    const val CORRUPTED = "Il file sembra danneggiato e non può essere letto. Prova con un altro file."
    const val TOO_BIG = "Il file è troppo grande (massimo 15 MB). Scegli un altro file."
    const val UNREADABLE = "Impossibile leggere il file selezionato. Riprova o scegli un altro file."
    const val UNSUPPORTED_GOOGLE_FILE =
        "Questo file Google non può essere convertito in .xlsx. Aprilo in Fogli Google e scaricalo come .xlsx (File › Scarica › Microsoft Excel)."
    const val SHEET_UNREADABLE = "Questo foglio non può essere letto. Prova a sceglierne un altro."
    const val SAVE_FAILED = "Impossibile salvare la scheda. Riprova."

    /** Success message; zero counts are left out, and the history part is omitted when both are zero. */
    fun imported(importedSessions: Int, skippedDuplicates: Int): String {
        val base = "Scheda importata"
        if (importedSessions == 0 && skippedDuplicates == 0) return base
        val present = if (skippedDuplicates == 1) "1 già presente" else "$skippedDuplicates già presenti"
        if (importedSessions == 0) {
            val sessions = if (skippedDuplicates == 1) "1 allenamento" else "$skippedDuplicates allenamenti"
            return "$base · storico già presente ($sessions)"
        }
        val added = if (importedSessions == 1) "1 allenamento dello storico aggiunto" else "$importedSessions allenamenti dello storico aggiunti"
        return if (skippedDuplicates == 0) "$base · $added" else "$base · $added, $present"
    }
}
