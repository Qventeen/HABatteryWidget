package io.lumensync.habattery.data.cache

import io.lumensync.habattery.data.store.CacheRecord
import io.lumensync.habattery.data.store.JsonSerializer
import io.lumensync.habattery.data.store.jsonDataStore
import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.SyncError
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreReadingCacheTest {
  @get:Rule val tmp = TemporaryFolder()

  private val file: File by lazy { File(tmp.root, "cache.json") }

  private fun cacheIn(scope: CoroutineScope) =
    DataStoreReadingCache(
      jsonDataStore(JsonSerializer(CacheRecord(), CacheRecord.serializer()), scope, { file })
    )

  /**
   * Writes with a first store, closes it, then reads with a second store on the same file. DataStore
   * forbids two active instances on one file, so the first scope is cancelled before the second
   * store is created.
   */
  private suspend fun TestScope.writeThenReopen(
    write: suspend (DataStoreReadingCache) -> Unit
  ): DataStoreReadingCache {
    val job = Job()
    write(cacheIn(CoroutineScope(StandardTestDispatcher(testScheduler) + job)))
    job.cancelAndJoin()
    return cacheIn(backgroundScope)
  }

  @Test
  fun errorRoundTrips() = runTest {
    val reopened =
      writeThenReopen { c -> c.update { it.copy(error = SyncError.HttpError(502)) } }
    assertEquals(SyncError.HttpError(502), reopened.state.first().error)
  }

  @Test
  fun objectErrorRoundTrips() = runTest {
    val reopened = writeThenReopen { c -> c.update { it.copy(error = SyncError.Unreachable) } }
    assertEquals(SyncError.Unreachable, reopened.state.first().error)
  }

  @Test
  fun readingRoundTripsWithNullCells() = runTest {
    val reading =
      BatteryReading(
        chargePercent = 87.5,
        powerW = -120.0,
        cellVoltagesV = listOf(3.3, null),
        updatedAtMillis = 1_700_000_000_000L,
      )
    val reopened = writeThenReopen { c -> c.update { it.copy(reading = reading) } }
    val read = reopened.state.first().reading
    assertEquals(reading, read)
    assertEquals(87.5, read!!.chargePercent, 0.0)
    assertEquals(listOf(3.3, null), read.cellVoltagesV)
    assertNull(read.cellVoltagesV[1])
    assertEquals(1_700_000_000_000L, read.updatedAtMillis)
  }
}
