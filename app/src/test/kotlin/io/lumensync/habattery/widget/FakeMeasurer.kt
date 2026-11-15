package io.lumensync.habattery.widget

/**
 * Every character is half the font size wide; Roboto-like vertical metrics; ink 0.7 × size above
 * the baseline, and 0.15 × size below it when the text has a comma.
 */
object FakeMeasurer : TextMeasurer {
  override fun width(text: String, sizeDp: Float, weight: TextWeight) = 0.5f * sizeDp * text.length

  override fun metrics(sizeDp: Float, weight: TextWeight) =
    LineMetrics(ascent = -0.93f * sizeDp, descent = 0.24f * sizeDp, top = -1.06f * sizeDp, bottom = 0.27f * sizeDp)

  override fun ink(text: String, sizeDp: Float, weight: TextWeight) =
    Ink(width = width(text, sizeDp, weight), top = -0.7f * sizeDp, bottom = if (',' in text) 0.15f * sizeDp else 0f)
}
