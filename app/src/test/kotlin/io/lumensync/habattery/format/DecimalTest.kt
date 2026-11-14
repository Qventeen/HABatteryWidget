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
}
