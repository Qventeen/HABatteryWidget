package io.lumensync.habattery.ui

import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.TokenType
import io.lumensync.habattery.testing.configuredSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiStateTest {
  private fun state(error: SyncError?, type: TokenType): MainUiState {
    val settings = configuredSettings(tokenType = type)
    return MainUiState(
      settings = settings,
      hasToken = true,
      overview = BatteryOverview.build(CacheState(error = error), settings, true, 0L),
    )
  }

  @Test
  fun showTokenTypeHint_onlyUnauthorizedAndAdmin() {
    assertTrue(state(SyncError.Unauthorized, TokenType.ADMIN).showTokenTypeHint)
    assertFalse(state(SyncError.Unauthorized, TokenType.USER).showTokenTypeHint)
    assertFalse(state(SyncError.Unreachable, TokenType.ADMIN).showTokenTypeHint)
    assertFalse(state(null, TokenType.ADMIN).showTokenTypeHint)
  }
}
