package io.lumensync.habattery.widget

import io.lumensync.habattery.widget.InverterLayoutSpec.S3X1
import io.lumensync.habattery.widget.InverterLayoutSpec.S4X1
import io.lumensync.habattery.widget.InverterLayoutSpec.S5X1
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class InverterLayoutSpecTest {
  @Test
  fun buckets_areTheDesignCardsMinusSix() =
    assertEquals(
      listOf(194f to 59f, 267f to 59f, 340f to 59f),
      InverterLayoutSpec.entries.map {
        it.bucketWidthDp + LayoutSpec.BUCKET_MARGIN_DP to it.bucketHeightDp + LayoutSpec.BUCKET_MARGIN_DP
      },
    )

  @Test
  fun designSizes_areTheReadmeCards() =
    assertEquals(
      listOf(194f to 59f, 267f to 59f, 340f to 59f),
      InverterLayoutSpec.entries.map { it.designSize.width.value to it.designSize.height.value },
    )

  @Test
  fun forSize_eachBucketAndCard_picksItsStrip() =
    InverterLayoutSpec.entries.forEach {
      assertSame(it, InverterLayoutSpec.forSize(it.bucketWidthDp, it.bucketHeightDp))
      assertSame(it, InverterLayoutSpec.forSize(it.bucketWidthDp + 6f, it.bucketHeightDp + 6f))
    }

  @Test fun forSize_betweenBuckets_picksTheFittingOne() = assertSame(S4X1, InverterLayoutSpec.forSize(300f, 59f))

  @Test fun forSize_wide_stretchesFiveByOne() = assertSame(S5X1, InverterLayoutSpec.forSize(460f, 59f))

  @Test fun forSize_tooSmall_takesTheSmallest() = assertSame(S3X1, InverterLayoutSpec.forSize(150f, 40f))

  /** Android shows a bucket from 1 dp below it; the card padding of 8 is inside. */
  @Test fun fitInner_isOneDpBelowTheBucket() = assertEquals(Box(171f, 36f), S3X1.fitInner(1f))

  @Test fun fitInner_scaledByTwo_isTheScaledSmallestSpace() = assertEquals(Box(342f, 72f), S3X1.fitInner(2f))

  @Test
  fun design_matchesTheReadme() {
    val d = InverterLayoutSpec.entries.map { it.design }
    assertEquals(listOf(3f, 4f, 6f), d.map { it.gap })
    assertEquals(listOf(28f, 32f, 32f), d.map { it.bigIcon })
    assertEquals(listOf(8f, 12f, 12f), d.map { it.bigPadding })
    assertEquals(listOf(true, false, false), d.map { it.wordUnderIcon })
    assertEquals(listOf(20f, 22f, 22f), d.map { it.wordIcon })
    assertEquals(listOf(10.5f, 14f, 16f), d.map { it.wordFont })
    assertEquals(listOf(44f, 72f, 90f), d.map { it.wordBox })
    assertEquals(listOf(11f, 13f, 15f), d.map { it.valueIcon })
    assertEquals(listOf(12.5f, 15f, 18f), d.map { it.valueFont })
    d.forEach {
      assertEquals(listOf(8f, 20f, 2f), listOf(it.padding, it.corner, it.valueIconGap))
      assertEquals(
        listOf(28f, 15f, 11f, 8f, 2f, 1f),
        listOf(it.refresh, it.refreshIcon, it.alertIcon, it.dot, it.dotRing, it.dotOverhang),
      )
    }
  }

  @Test
  fun scaled_includesRefreshSizes() {
    val s = InverterLayoutSpec.S3X1.design.scaled(1.28f)
    assertEquals(35.84f, s.refresh, 0.01f)
    assertEquals(14.08f, s.alertIcon, 0.01f)
  }

  /** The home-screen scale: every dp size and font × k; the shape stays. */
  @Test
  fun scaled_multipliesEverySize() =
    InverterLayoutSpec.entries.forEach { spec ->
      val k = 1.28f
      val scaled = spec.design.scaled(k)
      assertEquals(spec.name, sizes(spec.design).mapValues { it.value * k }, sizes(scaled))
      assertEquals(spec.name, spec.design.wordUnderIcon, scaled.wordUnderIcon)
    }

  @Test
  fun scaled_byOne_isTheSameSizes() =
    InverterLayoutSpec.entries.forEach { assertSame(it.design, it.design.scaled(1f)) }

  /** Every non-static `Float` field by name, so a new size cannot miss the scale. */
  private fun sizes(o: Any): Map<String, Float> =
    o.javaClass.declaredFields
      .filter { it.type == Float::class.javaPrimitiveType && !Modifier.isStatic(it.modifiers) }
      .onEach { it.isAccessible = true }
      .associate { it.name to it.getFloat(o) }
}
