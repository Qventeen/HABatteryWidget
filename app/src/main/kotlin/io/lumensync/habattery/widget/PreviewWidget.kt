package io.lumensync.habattery.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.glance.state.GlanceStateDefinition
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.FontPercents
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
   * there too). They are composed with the application context of [context], as the home-screen
   * widget is: the per-app language is process-wide and the app overrides no font scale, density
   * or night mode per activity, so the texts and sizes match the activity's. Before Android 12 this
   * context also fixes day or night; from 12 on, the context that applies the views does.
   * Drawings run one at a time.
   */
  suspend fun views(context: Context, layout: LayoutSpec): RemoteViews {
    val app = context.applicationContext
    return drawing.withLock { compose(app, previewId(app), size = layout.bucket) }
  }

  private companion object {
    /**
     * Glance keeps a layout file and a store for every widget id it composes, for the life of the
     * process, and the store holds the context it was opened with. So every drawing uses this one
     * id, and the application context, never an activity. Glance treats ids below -1 as not bound
     * to a home screen.
     */
    const val PREVIEW_ID = -2

    /** Glance fails the older of two sessions that run with the same id. */
    val drawing = Mutex()

    /** The public way to a widget id that Glance does not look up among the placed widgets. */
    fun previewId(context: Context): GlanceId =
      checkNotNull(
        GlanceAppWidgetManager(context)
          .getGlanceIdBy(Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, PREVIEW_ID))
      )
  }
}
