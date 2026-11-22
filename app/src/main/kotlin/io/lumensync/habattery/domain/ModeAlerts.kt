package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.InverterReading
import io.lumensync.habattery.domain.model.TextLevel

/** [level] is the mode the text was matched to; [text] is the Home Assistant text as it came. */
data class ModeAlert(val text: String, val level: TextLevel, val measuredAtMillis: Long)

/** [last] is the mode text to store; [alert] is what to post, if anything. */
data class ModeOutcome(val alert: ModeAlert?, val last: String?)

object ModeAlerts {
  /**
   * Compares the mode of the new text with the mode of [last], both matched with the current
   * [levels], so a list edit between two syncs is not a change. Two texts of one mode are no
   * change; two texts of "other" are, unless they differ only in case. A change alerts only when
   * the entered mode's bell is on. A reading with no mode keeps [last], so an offline ESP32 is not
   * a change. The first text seen is stored without an alert, and a change is tracked even when
   * [enabled] is false, so switching alerts on does not replay it.
   */
  fun evaluate(
    reading: InverterReading,
    levels: List<TextLevel>,
    enabled: Boolean,
    last: String?,
  ): ModeOutcome {
    val text = reading.mode ?: return ModeOutcome(null, last)
    val now = TextLevelPicker.match(levels, text) ?: return ModeOutcome(null, last)
    if (last == null) return ModeOutcome(null, text)
    val before = TextLevelPicker.match(levels, last)
    val changed =
      before != now || (now.other && !last.trim().equals(text.trim(), ignoreCase = true))
    val alert =
      ModeAlert(text, now, reading.updatedAtMillis).takeIf { changed && enabled && now.notify }
    return ModeOutcome(alert, text)
  }
}
