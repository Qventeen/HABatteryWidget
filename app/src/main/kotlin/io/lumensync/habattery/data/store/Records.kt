package io.lumensync.habattery.data.store

import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.Settings
import kotlinx.serialization.Serializable

@Serializable data class SettingsRecord(val schemaVersion: Int = 1, val settings: Settings = Settings())

@Serializable data class CacheRecord(val schemaVersion: Int = 1, val state: CacheState = CacheState())

@Serializable data class TokenRecord(val schemaVersion: Int = 1, val encrypted: String? = null)
