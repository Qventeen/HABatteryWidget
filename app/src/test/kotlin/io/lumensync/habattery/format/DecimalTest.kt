package io.lumensync.habattery.format

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecimalTest {
    @Test
    fun parseDecimal_ukrainianComma() =
        assertEquals(314.5, parseDecimal("314,5", Locale("uk"))!!, 0.0)

    @Test
    fun parseDecimal_usDot() = assertEquals(314.5, parseDecimal("314.5", Locale.US)!!, 0.0)

    @Test
    fun parseDecimal_rejectsJunk() =
        listOf("", "abc", "12abc", " ").forEach { assertNull(parseDecimal(it, Locale.US)) }

    @Test
    fun formatDecimal_usesLocaleSeparatorWithoutGrouping() {
        assertEquals("314,5", formatDecimal(314.5, Locale.forLanguageTag("uk")))
        assertEquals("1000", formatDecimal(1000.0, Locale.US))
    }

    @Test
    fun formatDecimal_roundTripsThroughParse() =
        listOf(Locale.forLanguageTag("uk"), Locale.US, Locale.GERMANY).forEach { locale ->
            assertEquals(locale.toString(), 314.5, parseDecimal(formatDecimal(314.5, locale), locale)!!, 0.0)
        }

    @Test
    fun parseDecimal_isStrict() {
        listOf("NaN", "∞", "1,2,3", "314,5", "1.5.2").forEach { assertNull(it, parseDecimal(it, Locale.US)) }
        assertEquals(-5.0, parseDecimal("-5", Locale.US)!!, 0.0)
    }
}
