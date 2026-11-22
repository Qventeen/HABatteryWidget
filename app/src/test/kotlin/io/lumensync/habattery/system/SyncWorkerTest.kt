package io.lumensync.habattery.system

import androidx.work.ListenableWorker.Result
import io.lumensync.habattery.domain.model.FetchResult
import io.lumensync.habattery.domain.model.PartResult
import io.lumensync.habattery.domain.model.SyncError.HttpError
import io.lumensync.habattery.domain.model.SyncError.NotConfigured
import io.lumensync.habattery.domain.model.SyncError.Unauthorized
import io.lumensync.habattery.domain.model.SyncError.Unreachable
import io.lumensync.habattery.testing.inverterReading
import io.lumensync.habattery.testing.reading
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncWorkerTest {
  private val unreachable = PartResult.Failed(Unreachable)
  private val batteryOk = PartResult.Ok(reading())
  private val inverterOk = PartResult.Ok(inverterReading())
  private val notConfigured = PartResult.Failed(NotConfigured)

  @Test
  fun unreachable_retriesUpTo3() {
    val result = FetchResult(unreachable, unreachable)

    assertEquals(Result.retry(), result.toWorkResult(0))
    assertEquals(Result.retry(), result.toWorkResult(2))
    assertEquals(Result.success(), result.toWorkResult(3))
  }

  @Test
  fun retriesWhenAnyPartUnreachable() {
    assertEquals(Result.retry(), FetchResult(unreachable, inverterOk).toWorkResult(0))
    assertEquals(Result.retry(), FetchResult(batteryOk, unreachable).toWorkResult(0))
    assertEquals(Result.retry(), FetchResult(notConfigured, unreachable).toWorkResult(0))
  }

  @Test
  fun otherResults_succeed() {
    val unauthorized = PartResult.Failed(Unauthorized)
    val http500 = PartResult.Failed(HttpError(500))

    assertEquals(Result.success(), FetchResult(unauthorized, unauthorized).toWorkResult(0))
    assertEquals(Result.success(), FetchResult(http500, notConfigured).toWorkResult(0))
    assertEquals(Result.success(), FetchResult(batteryOk, inverterOk).toWorkResult(0))
  }
}
