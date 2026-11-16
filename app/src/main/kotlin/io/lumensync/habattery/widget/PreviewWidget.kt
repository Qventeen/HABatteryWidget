package io.lumensync.habattery.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.glance.state.GlanceStateDefinition
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.FontPercents

/**
 * The widget as the app shows it (spec 0006, A1): the same [WidgetContent], drawn from the
 * [model], [fontPercents] and [colors] it is given instead of the stored ones, without tap targets,
 * at home-screen scale 1 (spec 0007, 8: nothing shrinks it in the app). No network, no Glance state.
 */
class PreviewWidget(
  private val model: WidgetModel,
  private val fontPercents: FontPercents,
  private val colors: WidgetColors,
) : GlanceAppWidget() {
  override val sizeMode: SizeMode = SizeMode.Exact

  override val stateDefinition: GlanceStateDefinition<*>? = null

  override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
    WidgetContent(model, fontPercents, colors, actions = null)
  }

  /**
   * The views of [layout], composed at its bucket as on the home screen (`LocalSize` is the bucket
   * there too). [context] gives the language, font scale and, before Android 12, day or night.
   */
  suspend fun views(context: Context, layout: LayoutSpec): RemoteViews =
    compose(context, size = layout.bucket)
}
