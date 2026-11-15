package io.lumensync.habattery.system

import androidx.work.ListenableWorker.Result
import io.lumensync.habattery.domain.model.SyncError.HttpError
import io.lumensync.habattery.domain.model.SyncError.Unauthorized
import io.lumensync.habattery.domain.model.SyncError.Unreachable
import io.lumensync.habattery.domain.model.SyncResult
import io.lumensync.habattery.testing.reading
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncWorkerTest {
  @Test
  fun unreachable_retriesUpTo3() {
    assertEquals(Result.retry(), SyncResult.Failure(Unreachable).toWorkResult(0))
    assertEquals(Result.retry(), SyncResult.Failure(Unreachable).toWorkResult(2))
    assertEquals(Result.success(), SyncResult.Failure(Unreachable).toWorkResult(3))
  }

  @Test
  fun otherResults_succeed() {
    assertEquals(Result.success(), SyncResult.Failure(Unauthorized).toWorkResult(0))
    assertEquals(Result.success(), SyncResult.Failure(HttpError(500)).toWorkResult(0))
    assertEquals(Result.success(), SyncResult.Success(reading()).toWorkResult(0))
  }
}
