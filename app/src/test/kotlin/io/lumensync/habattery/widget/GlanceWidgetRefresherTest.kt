package io.lumensync.habattery.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.domain.LateRedraw
import io.lumensync.habattery.testing.FakeReadingCache
import io.lumensync.habattery.testing.FakeSettingsRepository
import io.lumensync.habattery.testing.FakeSyncScheduler
import io.lumensync.habattery.testing.FakeTokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GlanceWidgetRefresherTest {
  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  @Test
  fun refreshAll_movesTheClockTick() = runBlocking {
    val tick = MutableStateFlow(NOW - 3_600_000L)
    val lateRedraw =
      LateRedraw(FakeSettingsRepository(), FakeReadingCache(), FakeTokenStore(), FakeSyncScheduler(), clock = { NOW })

    GlanceWidgetRefresher(context, lateRedraw, tick, clock = { NOW }).refreshAll()

    assertEquals(NOW, tick.value)
  }

  private companion object {
    const val NOW = 1_800_000_000_000L
  }
}
