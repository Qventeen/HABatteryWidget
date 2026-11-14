package io.lumensync.habattery.format

import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale

/** Parses [text] with the locale's decimal separator. The whole text must be a number. */
fun parseDecimal(text: String, locale: Locale): Double? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val position = ParsePosition(0)
    val number = NumberFormat.getInstance(locale).parse(trimmed, position) ?: return null
    return if (position.index == trimmed.length) number.toDouble() else null
}
