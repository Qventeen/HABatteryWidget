package io.lumensync.habattery.domain

/**
 * How a value is coloured. PLAIN: normal text on the plain pill. OK, WARN, BAD: green, amber, red.
 * IDLE: grey text (no current flows).
 */
enum class Level {
  PLAIN,
  OK,
  WARN,
  BAD,
  IDLE,
}

/** Fixed colour limits of design v4; there are no settings for them. */
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
      else -> Level.PLAIN
    }

  /** A battery temperature sensor, °C. */
  fun battery(c: Double): Level =
    when {
      c <= 0.0 || c > 55.0 -> Level.BAD
      c < 5.0 || c > 45.0 -> Level.WARN
      else -> Level.PLAIN
    }

  /** The MOSFET (power tube) temperature, °C. */
  fun mosfet(c: Double): Level =
    when {
      c > 80.0 -> Level.BAD
      c > 65.0 -> Level.WARN
      else -> Level.PLAIN
    }
}
