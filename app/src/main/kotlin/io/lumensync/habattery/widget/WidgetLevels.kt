package io.lumensync.habattery.widget

enum class Level {
  OK,
  WARN,
  BAD,
}

object WidgetLevels {
  fun charge(percent: Int): Level =
    when {
      percent < 15 -> Level.BAD
      percent < 25 -> Level.WARN
      else -> Level.OK
    }

  fun delta(mv: Int): Level =
    when {
      mv > 40 -> Level.BAD
      mv > 15 -> Level.WARN
      else -> Level.OK
    }
}
