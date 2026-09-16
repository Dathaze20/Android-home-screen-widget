package com.dathaze.pagewall.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import java.util.concurrent.Executors
import com.dathaze.pagewall.R
import com.dathaze.pagewall.data.PageStore
import com.dathaze.pagewall.ui.ConfigActivity

/**
 * A small home screen widget showing which page you are on and what is assigned to it.
 *
 * The widget is a convenience, not the mechanism: the wallpaper itself does the picture
 * swapping. Tapping the widget jumps straight into the settings for the page you are looking
 * at, so re-assigning a photo takes one tap instead of opening the app and finding the page.
 */
class PageWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id -> render(context, appWidgetManager, id, page = null) }
    }

    internal fun render(context: Context, manager: AppWidgetManager, widgetId: Int, page: Int?) {
        val store = PageStore(context)
        val resolved = (page ?: store.currentPage).coerceIn(0, store.pageCount - 1)
        val config = store.page(resolved)

        val views = RemoteViews(context.packageName, R.layout.widget_page).apply {
            setTextViewText(
                R.id.widget_page_label,
                context.getString(R.string.widget_page, resolved + 1),
            )
            setTextViewText(
                R.id.widget_page_detail,
                when {
                    config.hasMedia -> config.kindLabel
                    else -> context.getString(R.string.widget_tap_to_set)
                },
            )
            // Videos carry a poster frame saved at import, so this never starts a decoder.
            val thumbnail = store.fileFor(config.thumbnailFile)
                ?.let { decodeThumbnail(it.absolutePath) }
            if (thumbnail != null) {
                setImageViewBitmap(R.id.widget_thumbnail, thumbnail)
            } else {
                setImageViewResource(R.id.widget_thumbnail, R.drawable.widget_empty_slot)
            }
            setOnClickPendingIntent(R.id.widget_root, configPendingIntent(context, resolved))
        }

        manager.updateAppWidget(widgetId, views)
    }

    /** RemoteViews bitmaps cross a binder transaction, so keep the thumbnail genuinely small. */
    private fun decodeThumbnail(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= THUMBNAIL_PX) sample *= 2
        return runCatching {
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    private fun configPendingIntent(context: Context, page: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            page,
            Intent(context, ConfigActivity::class.java)
                .putExtra(ConfigActivity.EXTRA_PAGE, page)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        private const val THUMBNAIL_PX = 256

        /**
         * Thumbnail decoding happens here rather than on the caller's thread: the wallpaper
         * engine calls this from its render thread on every page change.
         */
        private val updateExecutor = Executors.newSingleThreadExecutor()

        /** Redraws every placed widget. Called by the wallpaper engine when the page changes. */
        fun notifyPageChanged(context: Context, page: Int) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, PageWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val appContext = context.applicationContext
            val provider = PageWidgetProvider()
            updateExecutor.execute {
                ids.forEach { id -> provider.render(appContext, manager, id, page) }
            }
        }

        /** Opens the settings screen focused on [page]. Used by the wallpaper's double-tap. */
        fun launchConfig(context: Context, page: Int) {
            context.startActivity(
                Intent(context, ConfigActivity::class.java)
                    .putExtra(ConfigActivity.EXTRA_PAGE, page)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }
}
