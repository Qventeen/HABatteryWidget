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

sealed interface SyncResult {
  data class Success(val reading: BatteryReading) : SyncResult

  data class Failure(val error: SyncError) : SyncResult
}
