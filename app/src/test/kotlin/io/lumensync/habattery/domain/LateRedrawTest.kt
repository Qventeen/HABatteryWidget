package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.testing.FakeReadingCache
import io.lumensync.habattery.testing.FakeSettingsRepository
import io.lumensync.habattery.testing.FakeSyncScheduler
import io.lumensync.habattery.testing.FakeTokenStore
import io.lumensync.habattery.testing.configuredSettings
import io.lumensync.habattery.testing.inverterReading
import io.lumensync.habattery.testing.reading
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LateRedrawTest {
  private val settings = configuredSettings(inverter = true)
  private val lateAfter = Freshness.lateAfterMillis(settings.refresh)
  private val scheduler = FakeSyncScheduler()

  private fun redraw(cache: CacheState) = runBlocking {
    LateRedraw(
      settings = FakeSettingsRepository(settings),
      cache = FakeReadingCache(cache),
      tokens = FakeTokenStore(initial = "t"),
      scheduler = scheduler,
      clock = { NOW },
    )()
  }

  @Test
  fun twoFreshParts_asksOnceForTheEarlierMoment() {
    redraw(
      CacheState(
        reading = reading().copy(updatedAtMillis = NOW - 60_000L),
        inverterReading = inverterReading().copy(updatedAtMillis = NOW - 120_000L),
      )
    )
    assertEquals(listOf(NOW - 120_000L + lateAfter), scheduler.redraws)
  }

  @Test
  fun nothingFresh_asksForNothing() {
    redraw(CacheState(reading = reading().copy(updatedAtMillis = NOW - lateAfter - 1)))
    assertEquals(emptyList<Long>(), scheduler.redraws)
  }

  private companion object {
    const val NOW = 10_000_000L
  }
}
