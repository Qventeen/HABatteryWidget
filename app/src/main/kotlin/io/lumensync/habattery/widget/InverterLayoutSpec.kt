package io.lumensync.habattery.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * The inverter strip's sizes at one width, in dp (`docs/design/README.md`, "Strip layout"). The
 * README gives no padding for the icon + word pill and the voltage pills: [wordPadding] is measured
 * from the design pictures. [valuePadding] is small at 3×1, where the design shows the values
 * nearly touching the pill ends; the larger fonts keep clear of the round ends.
 */
data class StripSizes(
  val gap: Float,
  val bigIcon: Float,
  val bigPadding: Float,
  /** Icon + word: the word under the icon (3×1) or beside it. */
  val wordUnderIcon: Boolean,
  val wordIcon: Float,
  /** Between the icon and the word beside it. */
  val wordGap: Float,
  val wordFont: Float,
  /** The widest the word may be. */
  val wordBox: Float,
  val wordPadding: Float,
  val valueIcon: Float,
  val valueFont: Float,
  val valuePadding: Float,
  val valueIconGap: Float = 2f,
  val padding: Float = 8f,
  val corner: Float = 20f,
  /** The mode pill is never narrower than the design pill is high. */
  val modeMinWidth: Float = 43f,
  // ↻ at the right end, and on a sync error the ⚠ above it, [alertTop] below the card's top.
  val refresh: Float = 28f,
  val refreshIcon: Float = 15f,
  val alertIcon: Float = 11f,
  val alertTop: Float = 2.5f,
  // The freshness dot on ↻, the card-coloured ring around it, and how far the dot's box reaches
  // past the circle's top and right edges.
  val dot: Float = 8f,
  val dotRing: Float = 2f,
  val dotOverhang: Float = 1f,
  // Setup needed or no data: one line, at the sizes of the battery widget's 2-row empty state.
  val emptyIcon: Float = 22f,
  val emptyGap: Float = 5f,
  val emptyFont: Float = 14f,
) {
  /** Every size × [k]; the shape stays (see [LayoutSpec.scaled]). */
  fun scaled(k: Float): StripSizes =
    if (k == 1f) this
    else copy(
      gap = gap * k,
      bigIcon = bigIcon * k,
      bigPadding = bigPadding * k,
      wordIcon = wordIcon * k,
      wordGap = wordGap * k,
      wordFont = wordFont * k,
      wordBox = wordBox * k,
      wordPadding = wordPadding * k,
      valueIcon = valueIcon * k,
      valueFont = valueFont * k,
      valuePadding = valuePadding * k,
      valueIconGap = valueIconGap * k,
      padding = padding * k,
      corner = corner * k,
      modeMinWidth = modeMinWidth * k,
      refresh = refresh * k,
      refreshIcon = refreshIcon * k,
      alertIcon = alertIcon * k,
      alertTop = alertTop * k,
      dot = dot * k,
      dotRing = dotRing * k,
      dotOverhang = dotOverhang * k,
      emptyIcon = emptyIcon * k,
      emptyGap = emptyGap * k,
      emptyFont = emptyFont * k,
    )
}

/**
 * One strip width. [bucketWidthDp] × [bucketHeightDp] is its `SizeMode.Responsive` size: the
 * design card − 6 dp each way, as the battery layouts ([LayoutSpec.BUCKET_MARGIN_DP]).
 */
enum class InverterLayoutSpec(
  val bucketWidthDp: Float,
  val bucketHeightDp: Float,
  val design: StripSizes,
) {
  S3X1(
    188f,
    53f,
    StripSizes(
      gap = 3f,
      bigIcon = 28f,
      bigPadding = 8f,
      wordUnderIcon = true,
      wordIcon = 20f,
      wordGap = 0f,
      wordFont = 10.5f,
      wordBox = 44f,
      wordPadding = 8f,
      valueIcon = 11f,
      valueFont = 12.5f,
      valuePadding = 2f,
    ),
  ),
  S4X1(
    261f,
    53f,
    StripSizes(
      gap = 4f,
      bigIcon = 32f,
      bigPadding = 12f,
      wordUnderIcon = false,
      wordIcon = 22f,
      wordGap = 5f,
      wordFont = 14f,
      wordBox = 72f,
      wordPadding = 11f,
      valueIcon = 13f,
      valueFont = 15f,
      valuePadding = 4f,
    ),
  ),
  S5X1(
    334f,
    53f,
    StripSizes(
      gap = 6f,
      bigIcon = 32f,
      bigPadding = 12f,
      wordUnderIcon = false,
      wordIcon = 22f,
      wordGap = 5f,
      wordFont = 16f,
      wordBox = 90f,
      wordPadding = 11f,
      valueIcon = 15f,
      valueFont = 18f,
      valuePadding = 4f,
    ),
  );

  val bucket: DpSize
    get() = DpSize(bucketWidthDp.dp, bucketHeightDp.dp)

  /** The design card: the bucket + [LayoutSpec.BUCKET_MARGIN_DP] each way. */
  val designSize: DpSize
    get() =
      DpSize(
        (bucketWidthDp + LayoutSpec.BUCKET_MARGIN_DP).dp,
        (bucketHeightDp + LayoutSpec.BUCKET_MARGIN_DP).dp,
      )

  /**
   * The card's inner box the texts are fitted to, at home-screen scale [k]: the bucket less the
   * platform rounding, the smallest space this strip is shown in (see [LayoutSpec.fitBoxes]).
   */
  fun fitInner(k: Float): Box {
    val rounding = LayoutSpec.PLATFORM_ROUNDING_DP
    val padding = 2 * design.padding
    return Box((bucketWidthDp - rounding - padding) * k, (bucketHeightDp - rounding - padding) * k)
  }

  companion object {
    /** The strip shown at [width] × [height] dp, by the best-fit rule of [LayoutSpec.forSize]. */
    fun forSize(width: Float, height: Float): InverterLayoutSpec =
      entries
        .filter { ceil(width) + 1 > it.bucketWidthDp && ceil(height) + 1 > it.bucketHeightDp }
        .minByOrNull {
          val dx = it.bucketWidthDp - width
          val dy = it.bucketHeightDp - height
          dx * dx + dy * dy
        } ?: S3X1
  }
}
