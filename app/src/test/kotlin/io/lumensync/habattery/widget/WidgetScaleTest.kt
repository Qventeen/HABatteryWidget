package io.lumensync.habattery.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetScaleTest {
  @Test fun designSize_isOne() = assertEquals(1f, WidgetScale.of(360f, 180f))

  @Test fun minimumSize_is85Percent() = assertEquals(0.85f, WidgetScale.of(306f, 153f), 1e-6f)

  @Test fun biggerThanDesign_staysOne() = assertEquals(1f, WidgetScale.of(460f, 460f))

  @Test fun narrowerThanMinimum_shrinksByWidth() = assertEquals(276f / 360f, WidgetScale.of(276f, 180f), 1e-6f)

  @Test fun widthOnly_forTheAppCard() = assertEquals(0.9f, WidgetScale.of(324f), 1e-6f)
}
