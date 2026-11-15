package io.lumensync.habattery.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/** Whether this pixel is [other], give or take rounding of the screenshot. */
internal fun Color.isCloseTo(other: Color, tolerance: Float = 0.02f): Boolean =
  abs(red - other.red) <= tolerance &&
    abs(green - other.green) <= tolerance &&
    abs(blue - other.blue) <= tolerance
