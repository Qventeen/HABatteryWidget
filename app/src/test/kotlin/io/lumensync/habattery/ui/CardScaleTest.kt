package io.lumensync.habattery.ui

import io.lumensync.habattery.ui.sections.CardScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardScaleTest {
  @Test fun designSize_isOne() = assertEquals(1f, CardScale.of(360f, 180f))

  /** The 44 dp top row does not scale, so the height left for scaled parts is h − 44 of 136. */
  @Test fun minimumSize_scalesByTheHeightLeft() = assertEquals(109f / 136f, CardScale.of(306f, 153f), 1e-6f)

  @Test fun biggerThanDesign_staysOne() = assertEquals(1f, CardScale.of(460f, 460f))

  @Test fun narrowerThanMinimum_shrinksByWidth() = assertEquals(276f / 360f, CardScale.of(276f, 180f), 1e-6f)

  @Test fun widthOnly_forTheAppCard() = assertEquals(0.9f, CardScale.of(324f), 1e-6f)

  @Test
  fun ringFitsTheBody_atEveryHeight() {
    var h = 100f
    while (h <= 400f) {
      val s = CardScale.of(360f, h)
      // Card padding 2 × 12, the gap 4 under the top row, the 104 dp ring: all × s.
      val body = h - 44f - 28f * s
      assertTrue("height $h: ring ${104f * s} > body $body", 104f * s <= body + 1e-3f)
      h += 0.5f
    }
  }

  @Test fun cardHeight_atOne_isTheDesignHeight() = assertEquals(180f, CardScale.heightDp(1f), 1e-6f)

  @Test fun cardHeight_scalesAllButTheTopRow() = assertEquals(44f + 136f * 0.9f, CardScale.heightDp(0.9f), 1e-6f)
}
