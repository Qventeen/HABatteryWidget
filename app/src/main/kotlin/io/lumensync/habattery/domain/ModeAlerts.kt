package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.InverterMode
import io.lumensync.habattery.domain.model.InverterReading

/** [mode] is `null` for a text the app has no row for; [text] is what the title shows then. */
data class ModeAlert(val text: String, val mode: InverterMode?, val measuredAtMillis: Long)

/** [last] is the mode text to store; [alert] is what to post, if anything. */
data class ModeOutcome(val alert: ModeAlert?, val last: String?)

object ModeAlerts {
  /**
   * Compares the new mode text with [last], ignoring case. A reading with no mode keeps [last],
   * so an offline ESP32 is not a change. The first text seen is stored without an alert, and a
   * change is tracked even when [enabled] is false, so switching alerts on does not replay it.
   */
  fun evaluate(reading: InverterReading, enabled: Boolean, last: String?): ModeOutcome {
    val text = reading.mode ?: return ModeOutcome(null, last)
    if (last == null || last.equals(text, ignoreCase = true)) {
      return ModeOutcome(null, last ?: text)
    }
    val alert =
      ModeAlert(text, InverterMode.of(text), reading.updatedAtMillis).takeIf { enabled }
    return ModeOutcome(alert, text)
  }
}
