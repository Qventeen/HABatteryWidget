package io.lumensync.habattery.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface SyncError {
  @Serializable @SerialName("not_configured") data object NotConfigured : SyncError

  @Serializable @SerialName("unauthorized") data object Unauthorized : SyncError

  @Serializable @SerialName("unreachable") data object Unreachable : SyncError

  @Serializable @SerialName("http") data class HttpError(val code: Int) : SyncError

  @Serializable @SerialName("bad_response") data object BadResponse : SyncError
}

/** What one sync asks Home Assistant for; a `null` part is not asked. */
data class FetchRequest(val battery: EntitySettings?, val inverter: InverterEntitySettings?)

sealed interface PartResult<out T> {
  data class Ok<T>(val reading: T) : PartResult<T>

  data class Failed(val error: SyncError) : PartResult<Nothing>
}

/** A part is `null` exactly when it was not asked. */
data class FetchResult(
  val battery: PartResult<BatteryReading>?,
  val inverter: PartResult<InverterReading>?,
) {
  companion object {
    /** Every asked part fails with [error]. */
    fun failed(request: FetchRequest, error: SyncError): FetchResult =
      FetchResult(
        battery = request.battery?.let { PartResult.Failed(error) },
        inverter = request.inverter?.let { PartResult.Failed(error) },
      )
  }
}

/** The battery temperature sensors Home Assistant has for a prefix (spec 0004, 5.3). */
sealed interface SensorList {
  data class Found(val ids: List<String>) : SensorList

  data class Failed(val error: SyncError) : SensorList
}
