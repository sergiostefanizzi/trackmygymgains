package com.trackmygymgains

/** Formats a weight in kg without a trailing ".0" (e.g. 80.0 -> "80", 82.5 -> "82.5"). */
fun formatWeightKg(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toLong().toString() else kg.toString()
