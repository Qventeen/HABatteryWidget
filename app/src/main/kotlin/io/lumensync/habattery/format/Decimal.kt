package io.lumensync.habattery.format

import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale

/**
 * Parses a number typed by the user: '.' or ',' as the decimal mark in any locale, no grouping.
 * It also reads back what [formatDecimal] wrote for [locale]. The whole text must be a number.
 */
fun parseDecimal(text: String, locale: Locale): Double? {
    val trimmed = text.trim()
    if (TYPED_DECIMAL.matches(trimmed)) return trimmed.replace(',', '.').toDouble()
    return parseLocaleDecimal(trimmed, locale)
}

/** Strict parse in the locale's own form (other digits or minus sign, for example). */
private fun parseLocaleDecimal(trimmed: String, locale: Locale): Double? {
    if (trimmed.isEmpty()) return null
    val position = ParsePosition(0)
    val format = NumberFormat.getInstance(locale)
    (format as? DecimalFormat)?.isGroupingUsed = false
    val number = format.parse(trimmed, position)?.toDouble() ?: return null
    return if (position.index == trimmed.length && number.isFinite()) number else null
}

/** Formats [value] for an input field, so [parseDecimal] with the same [locale] reads it back. */
fun formatDecimal(value: Double, locale: Locale): String =
    NumberFormat.getInstance(locale).apply {
        isGroupingUsed = false
        maximumFractionDigits = MAX_INPUT_FRACTION_DIGITS
    }.format(value)

private const val MAX_INPUT_FRACTION_DIGITS = 3

/** ASCII digits with at most one '.' or ',' between them, and an optional leading '-'. */
private val TYPED_DECIMAL = Regex("-?[0-9]+([.,][0-9]+)?")
