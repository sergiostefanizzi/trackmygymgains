package com.trackmygymgains.ui.importplan

/** User-facing (Italian) error messages produced by [ImportViewModel]. */
internal object ImportMessages {
    const val NOT_XLSX = "Il file selezionato non è un file Excel (.xlsx). Scegli un altro file."
    const val CORRUPTED = "Il file sembra danneggiato e non può essere letto. Prova con un altro file."
    const val TOO_BIG = "Il file è troppo grande (massimo 15 MB). Scegli un altro file."
    const val UNREADABLE = "Impossibile leggere il file selezionato. Riprova o scegli un altro file."
    const val SHEET_UNREADABLE = "Questo foglio non può essere letto. Prova a sceglierne un altro."
    const val SAVE_FAILED = "Impossibile salvare la scheda. Riprova."
}
