package io.lumensync.habattery.data.store

import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.Settings
import kotlinx.serialization.Serializable

@Serializable data class SettingsRecord(val schemaVersion: Int = 1, val settings: Settings = Settings())

@Serializable data class CacheRecord(val schemaVersion: Int = 1, val state: CacheState = CacheState())

/**
 * One encrypted token per token type. [encrypted] is the Admin slot: it keeps its name from before
 * token types (spec 0009), and a token saved then was sent to the admin-only template, so an old
 * record loads as the Admin token with no migration.
 */
@Serializable
data class TokenRecord(
  val schemaVersion: Int = 1,
  val encrypted: String? = null,
  val userEncrypted: String? = null,
)
