package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.WidgetModel.EmptyKind
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.IconSlot
import io.lumensync.habattery.domain.model.InverterIcon
import io.lumensync.habattery.domain.model.InverterReading
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.ModeStyle
import io.lumensync.habattery.domain.model.ModeText
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.TextLevel
import io.lumensync.habattery.domain.model.TextValue
import io.lumensync.habattery.domain.model.icon

/** What the inverter strip shows; the drawing only reads it. A missing value stays null. */
sealed interface InverterWidgetModel {
  /** The inverter part's last sync error; a strip that needs setup carries none. */
  val error: SyncError?

  data class Empty(val kind: EmptyKind, override val error: SyncError?) : InverterWidgetModel

  data class Data(
    val mode: ModeLook,
    val grid: VoltageLook,
    val output: VoltageLook,
    val style: ModeStyle,
    override val error: SyncError?,
    /** One sync serves both parts, so this is the battery widget's "refreshing" mark too. */
    val refreshing: Boolean,
    /** The freshness dot turns amber ([Freshness.isLate]): the inverter reading's age only. */
    val dataLate: Boolean,
    val dataAgeMinutes: Long,
  ) : InverterWidgetModel

  companion object {
    fun from(
      cache: CacheState,
      settings: Settings,
      hasToken: Boolean,
      nowMillis: Long,
    ): InverterWidgetModel {
      if (!Validation.isInverterConfigured(settings, hasToken)) return Empty(EmptyKind.SETUP, null)
      val r = cache.inverterReading ?: return Empty(EmptyKind.NO_DATA, cache.inverterError)
      return Data(
        mode = modeLook(r, settings),
        grid = voltageLook(LevelValue.GRID_VOLTAGE, r, settings, IconSlot.GRID_VOLTAGE),
        output = voltageLook(LevelValue.OUTPUT_VOLTAGE, r, settings, IconSlot.OUTPUT_VOLTAGE),
        style = settings.inverter.modeStyle,
        error = cache.inverterError,
        refreshing = Freshness.isRefreshing(cache.refreshingSinceMillis, nowMillis),
        dataLate = Freshness.isLate(r.updatedAtMillis, nowMillis, settings.refresh),
        dataAgeMinutes = Freshness.ageMinutes(r.updatedAtMillis, nowMillis),
      )
    }

    private fun modeLook(r: InverterReading, settings: Settings): ModeLook {
      val levels = TextLevelPicker.effective(TextValue.MODE, settings)
      val level = TextLevelPicker.match(levels, r.mode)
      val text = r.mode?.takeUnless { ModeText.isNoValue(it) }
      // No value shows "—" with the "other" icon: the ESP32 is offline, no mode is known.
      val icon = (level ?: levels.first { it.other }).shownIcon
      return ModeLook(level, text, level?.shownColor ?: LevelColor.GREY, icon)
    }

    private fun voltageLook(
      value: LevelValue,
      r: InverterReading,
      settings: Settings,
      slot: IconSlot,
    ): VoltageLook {
      val volts = ShownNumber.of(value, r)
      val color =
        volts?.let {
          LevelPicker.pick(LevelPicker.effective(value, settings, LevelBasis(null, null)), it)
            .shownColor
        } ?: LevelColor.PLAIN
      return VoltageLook(volts, color, settings.inverter.icon(slot))
    }
  }
}

/**
 * The mode: [level] is the matched item, `null` when there is no value; [text] is the Home
 * Assistant text as it is, `null` when there is no value.
 */
data class ModeLook(
  val level: TextLevel?,
  val text: String?,
  val color: LevelColor,
  val icon: InverterIcon,
)

/** [volts] is whole volts as shown, picked by that same number; `null` is drawn "—" in PLAIN. */
data class VoltageLook(val volts: Double?, val color: LevelColor, val icon: InverterIcon)
