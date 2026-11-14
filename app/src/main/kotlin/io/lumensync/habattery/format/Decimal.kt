package io.lumensync.habattery.format

import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale

/** Parses [text] with the locale's decimal separator. The whole text must be a number. */
fun parseDecimal(text: String, locale: Locale): Double? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val position = ParsePosition(0)
    val format = NumberFormat.getInstance(locale)
    (format as? DecimalFormat)?.isGroupingUsed = false
    val number = format.parse(trimmed, position)?.toDouble() ?: return null
    return if (position.index == trimmed.length && number.isFinite()) number else null
}
