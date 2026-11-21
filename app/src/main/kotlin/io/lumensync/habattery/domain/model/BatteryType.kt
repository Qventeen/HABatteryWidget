package io.lumensync.habattery.domain.model

import kotlinx.serialization.Serializable

/**
 * The pack chemistry. It only picks the default voltage and current levels (spec 0013); own levels
 * stay as they are. [LI_ION] means NMC. A `null` in the settings reads as [DEFAULT].
 */
@Serializable
enum class BatteryType {
  LIFEPO4,
  LI_ION,
  LTO;

  companion object {
    /** The chemistry the app was built for: settings stored before spec 0013 keep their levels. */
    val DEFAULT = LIFEPO4
  }
}
