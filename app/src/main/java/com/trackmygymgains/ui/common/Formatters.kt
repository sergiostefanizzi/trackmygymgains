package com.trackmygymgains.ui.common

import java.util.Locale

/** Formats a rest time in seconds as m:ss (e.g. 180 -> "3:00", 90 -> "1:30"). */
fun formatRest(seconds: Int): String = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
