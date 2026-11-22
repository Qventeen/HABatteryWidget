package io.lumensync.habattery.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.glance.state.GlanceStateDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A widget as the app shows it (spec 0006): [content] is the drawing the home screen composes
 * ([WidgetContent] or [InverterWidgetContent]), given the values to show instead of the stored
 * ones, no tap targets and home-screen scale 1 (spec 0007: nothing shrinks it in the app). No
 * network, no Glance state.
 */
class PreviewWidget(private val content: @Composable () -> Unit) : GlanceAppWidget() {
  override val sizeMode: SizeMode = SizeMode.Exact

  override val stateDefinition: GlanceStateDefinition<*>? = null

  override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { content() }

  /**
   * The views composed at [bucket], as on the home screen (`LocalSize` is the bucket there too).
   * They are composed with the application context of [context], as the home-screen widget is: the
   * per-app language is process-wide and the app overrides no font scale, density or night mode
   * per activity, so the texts and sizes match the activity's. Before Android 12 this context also
   * fixes day or night; from 12 on, the context that applies the views does. Drawings of both
   * widgets run one at a time. Null when Glance fails to draw them.
   */
  suspend fun views(context: Context, bucket: DpSize): RemoteViews? {
    val app = context.applicationContext
    return previewOrNull("compose") {
      drawing.withLock { compose(app, previewId(app), size = bucket) }
    }
  }

  private companion object {
    /**
     * Glance keeps a layout file and a store for every widget id it composes, for the life of the
     * process, and the store holds the context it was opened with. So every drawing uses this one
     * id, and the application context, never an activity. Glance treats ids below -1 as not bound
     * to a home screen.
     */
    const val PREVIEW_ID = -2

    /** Glance fails the older of two sessions with the same id, whichever widget they draw. */
    val drawing = Mutex()

    /** The public way to a widget id that Glance does not look up among the placed widgets. */
    fun previewId(context: Context): GlanceId =
      checkNotNull(
        GlanceAppWidgetManager(context)
          .getGlanceIdBy(Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, PREVIEW_ID))
      )
  }
}

private const val TAG = "WidgetPreview"

/**
 * Runs one [step] of a preview drawing. A failure gives null, so a broken drawing cannot crash the
 * app at every start; it is logged by class name only, since a message could carry reading values.
 * Cancellation is rethrown.
 */
internal inline fun <T : Any> previewOrNull(step: String, block: () -> T): T? =
  try {
    block()
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    Log.w(TAG, "$step failed: ${e.javaClass.name}")
    null
  }
