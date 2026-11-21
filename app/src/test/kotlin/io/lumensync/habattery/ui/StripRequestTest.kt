package io.lumensync.habattery.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.lumensync.habattery.widget.BatteryWidgetReceiver
import io.lumensync.habattery.widget.InverterLayoutSpec
import io.lumensync.habattery.widget.InverterLayoutSpec.S3X1
import io.lumensync.habattery.widget.InverterLayoutSpec.S4X1
import io.lumensync.habattery.widget.InverterLayoutSpec.S5X1
import io.lumensync.habattery.widget.InverterWidgetReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class StripRequestTest {
  private fun strip(spec: InverterLayoutSpec) =
    NamedWidget(InverterWidgetReceiver::class.java.name, DpSize(spec.bucketWidthDp.dp, spec.bucketHeightDp.dp))

  @Test
  fun named_aStrip_opensItsOwnSize() =
    InverterLayoutSpec.entries.forEach { assertEquals(it, StripRequest.named(strip(it))) }

  @Test
  fun named_noSizeYet_opensTheSmallestStrip() =
    assertEquals(S3X1, StripRequest.named(NamedWidget(InverterWidgetReceiver::class.java.name, DpSize.Zero)))

  @Test
  fun named_aBatteryWidget_namesNoStrip() =
    assertNull(StripRequest.named(NamedWidget(BatteryWidgetReceiver::class.java.name, DpSize(340.dp, 205.dp))))

  @Test
  fun named_noWidgetOrUnknownProvider_namesNoStrip() {
    assertNull(StripRequest.named(null))
    assertNull(StripRequest.named(NamedWidget(null, DpSize(340.dp, 60.dp))))
  }

  @Test
  fun next_namedSpec_isANewRequest() = assertEquals(StripRequest(S5X1, 3), StripRequest(S3X1, 2).next(S5X1))

  @Test
  fun next_noneNamed_keepsTheRequest() {
    val request = StripRequest(S4X1, 2)
    assertSame(request, request.next(null))
  }

  @Test
  fun specFor_newRequest_switchesToItsSpec() = assertEquals(S5X1, StripRequest(S5X1, 1).specFor(S3X1, seenCount = 0))

  @Test
  fun specFor_firstDrawingOrSeenRequest_keepsTheUserSpec() {
    assertEquals(S4X1, StripRequest(S5X1).specFor(S4X1, seenCount = 0))
    assertEquals(S4X1, StripRequest(S5X1, 1).specFor(S4X1, seenCount = 1))
  }
}
