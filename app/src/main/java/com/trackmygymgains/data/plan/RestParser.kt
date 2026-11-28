package com.trackmygymgains.data.plan

/**
 * Result of parsing a REST cell. [seconds] is null for "no rest" (empty, `/`, `-`) or unrecognised text.
 * [warning] is an Italian fragment (without row/exercise context) set when the value was ambiguous or unrecognised.
 */
data class RestResult(val seconds: Int?, val warning: String? = null)

object RestParser {
    private val SECONDS_QUOTE = Regex("""^(\d+)\s*(?:"|'')$""")
    private val MIN_SEC = Regex("""^(\d+)\s*'\s*(?:(\d+)\s*(?:"|'')?)?$""")
    private val CLOCK = Regex("""^(\d+):(\d{1,2})$""")
    private val MINUTES_WORD = Regex("""^(\d+)\s*(?:min|mins|minuti|minuto)$""")
    private val SECONDS_WORD = Regex("""^(\d+)\s*(?:s|sec|secs|secondi|secondo)$""")
    private val BARE = Regex("""^(\d+)(?:[.,]0*)?$""")

    fun parseSeconds(raw: String?): RestResult {
        val text = raw.orEmpty()
            .replace('’', '\'').replace('′', '\'').replace('‘', '\'')
            .replace('”', '"').replace('″', '"').replace('“', '"')
            .trim().lowercase()
        if (text.isEmpty() || text == "/" || text == "-") return RestResult(null)

        SECONDS_QUOTE.matchEntire(text)?.let { return RestResult(it.groupValues[1].toIntOrNull()) }
        MIN_SEC.matchEntire(text)?.let {
            val min = it.groupValues[1].toIntOrNull()
            val sec = if (it.groupValues[2].isEmpty()) 0 else it.groupValues[2].toIntOrNull()
            return if (min == null || sec == null) unrecognised(raw) else RestResult(min * 60 + sec)
        }
        CLOCK.matchEntire(text)?.let {
            val min = it.groupValues[1].toIntOrNull()
            val sec = it.groupValues[2].toInt()
            return if (min == null || sec >= 60) unrecognised(raw) else RestResult(min * 60 + sec)
        }
        MINUTES_WORD.matchEntire(text)?.let { m ->
            return m.groupValues[1].toIntOrNull()?.let { RestResult(it * 60) } ?: unrecognised(raw)
        }
        SECONDS_WORD.matchEntire(text)?.let { m ->
            return m.groupValues[1].toIntOrNull()?.let { RestResult(it) } ?: unrecognised(raw)
        }
        BARE.matchEntire(text)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: return unrecognised(raw)
            return when {
                n == 0 -> RestResult(0)
                n <= 10 -> RestResult(n * 60, "REST \"${raw!!.trim()}\" ambiguo: interpretato come $n minuti")
                else -> RestResult(n, "REST \"${raw!!.trim()}\" ambiguo: interpretato come $n secondi")
            }
        }
        return unrecognised(raw)
    }

    private fun unrecognised(raw: String?) =
        RestResult(null, "REST \"${raw.orEmpty().trim()}\" non riconosciuto: nessun timer automatico")
}
