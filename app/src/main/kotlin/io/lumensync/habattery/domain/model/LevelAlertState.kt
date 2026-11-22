package io.lumensync.habattery.domain.model

import kotlinx.serialization.Serializable

/**
 * What the level alerts remember for one value between syncs. [current] is the id of the level
 * the value was last in, `null` before its first check. [disarmed] holds the ids of the levels that
 * have alerted and may not alert again until the value moves away from them by the margin.
 */
@Serializable
data class LevelAlertState(val current: Int? = null, val disarmed: List<Int> = emptyList())
