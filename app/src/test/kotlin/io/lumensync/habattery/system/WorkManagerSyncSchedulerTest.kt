package io.lumensync.habattery.system

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkManagerSyncSchedulerTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val scheduler = WorkManagerSyncScheduler(context, hasWidgets = { true })

  @Before
  fun setUp() {
    WorkManagerTestInitHelper.initializeTestWorkManager(
      context,
      Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
    )
  }

  private fun enqueued(name: String) =
    WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()
      .filter { it.state == WorkInfo.State.ENQUEUED }

  @Test
  fun redrawAt_keepsOneWorkWithTheNewDelay() {
    val now = System.currentTimeMillis()
    scheduler.redrawAt(now + 10 * 60_000L)
    scheduler.redrawAt(now + 5 * 60_000L)
    val infos = enqueued("widget_redraw_late")
    assertEquals(1, infos.size)
    assertTrue(infos.single().initialDelayMillis in (4 * 60_000L)..(5 * 60_000L))
  }
}
