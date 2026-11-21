package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.BatteryType

/**
 * The volts per cell of the default voltage edges. One rule for every type: [veryHigh] is the
 * cell's charge limit, [full] a usual charge target, [high] between them, [low] well above the
 * discharge cut-off (red comes before the BMS stops the pack) and [gettingLow] is [low] + 0.20 V.
 */
data class CellVolts(
  val low: Double,
  val gettingLow: Double,
  val full: Double,
  val high: Double,
  val veryHigh: Double,
)

/**
 * The C-rates of the default current edges, times the full capacity. One rule for every type and
 * direction: [high] is the standard rate, [veryHigh] about 0.8 × the maximum continuous rate and
 * [light] 0.4 × the standard rate.
 */
data class CRates(val light: Double, val high: Double, val veryHigh: Double)

/**
 * The default voltage and current edges of a [BatteryType] (spec 0013, which lists the datasheets
 * behind each number). Charge and discharge have their own rates: most chemistries allow more
 * discharge than charge. LiFePO4 keeps the owner's numbers from before the type existed.
 */
data class BatteryPreset(val cellVolts: CellVolts, val charge: CRates, val discharge: CRates) {
  companion object {
    private val LIFEPO4 =
      BatteryPreset(
        CellVolts(low = 3.00, gettingLow = 3.20, full = 3.45, high = 3.55, veryHigh = 3.65),
        // Standard 0.5 C and maximum 1 C both ways; only the short pulse differs.
        charge = CRates(light = 0.2, high = 0.5, veryHigh = 0.8),
        discharge = CRates(light = 0.2, high = 0.5, veryHigh = 0.8),
      )

    private val LI_ION =
      BatteryPreset(
        CellVolts(low = 3.40, gettingLow = 3.60, full = 4.10, high = 4.15, veryHigh = 4.20),
        charge = CRates(light = 0.2, high = 0.5, veryHigh = 0.8),
        discharge = CRates(light = 0.4, high = 1.0, veryHigh = 1.3),
      )

    private val LTO =
      BatteryPreset(
        CellVolts(low = 2.00, gettingLow = 2.20, full = 2.65, high = 2.70, veryHigh = 2.80),
        charge = CRates(light = 0.4, high = 1.0, veryHigh = 5.0),
        discharge = CRates(light = 0.4, high = 1.0, veryHigh = 6.0),
      )

    fun of(type: BatteryType): BatteryPreset =
      when (type) {
        BatteryType.LIFEPO4 -> LIFEPO4
        BatteryType.LI_ION -> LI_ION
        BatteryType.LTO -> LTO
      }
  }
}
