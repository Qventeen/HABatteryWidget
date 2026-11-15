package io.lumensync.habattery.widget

/** Offsets (dp) of the label's and the under-SOC line's centres from the ring centre. */
internal data class RingTextOffsets(val label: Float, val under: Float)

/**
 * Places the three lines inside the ring as the design does (CSS: SOC number with line-height 1,
 * 2 dp gaps). Android text boxes are taller than that, so a plain column would push the under-SOC
 * line into the ring; the drawings place each line by these offsets instead.
 */
internal object RingLayout {
  /** Roboto's normal line height, per unit of font size. */
  private const val LINE_HEIGHT = 1.17f

  fun offsets(socDp: Float, labelDp: Float, underDp: Float, gapDp: Float): RingTextOffsets =
    RingTextOffsets(
      label = socDp / 2 + gapDp + labelDp * LINE_HEIGHT / 2,
      under = socDp / 2 + gapDp + underDp * LINE_HEIGHT / 2,
    )
}
