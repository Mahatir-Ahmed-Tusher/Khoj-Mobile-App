package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.model.SearchMode

class KhojSearchWidgetProvider : AppWidgetProvider() {

    companion object {
        const val EXTRA_SEARCH_MODE = "com.example.extra.SEARCH_MODE"
        const val EXTRA_SEARCH_QUERY = "com.example.extra.SEARCH_QUERY"
        const val EXTRA_AUTO_FOCUS = "com.example.extra.AUTO_FOCUS"
        const val EXTRA_WIDGET_LAUNCH = "com.example.extra.WIDGET_LAUNCH"

        private const val RC_SEARCH_BOX = 1001
        private const val RC_VOICE = 1002
        private const val RC_CAMERA = 1003
        private const val RC_FACTCHECK = 1004
        private const val RC_WEBSEARCH = 1005
        private const val RC_DEEPSEARCH = 1006

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, KhojSearchWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                val intent = Intent(context, KhojSearchWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_search_bar)

        // Main Search Box -> Launch App focused in FactCheck (default) mode
        views.setOnClickPendingIntent(
            R.id.widget_search_box,
            createModePendingIntent(context, SearchMode.FACT_CHECK, RC_SEARCH_BOX)
        )

        // Voice Search Button -> Launch App directly into Voice listening mode
        views.setOnClickPendingIntent(
            R.id.widget_btn_voice,
            createModePendingIntent(context, SearchMode.VOICE_SEARCH, RC_VOICE)
        )

        // Image Search Button -> Launch App directly into Image search / picker mode
        views.setOnClickPendingIntent(
            R.id.widget_btn_camera,
            createModePendingIntent(context, SearchMode.IMAGE_SEARCH, RC_CAMERA)
        )

        // Fact Check Chip -> Launch App into Fact Check mode
        views.setOnClickPendingIntent(
            R.id.widget_chip_factcheck,
            createModePendingIntent(context, SearchMode.FACT_CHECK, RC_FACTCHECK)
        )

        // Web Search Chip -> Launch App into Web Search mode
        views.setOnClickPendingIntent(
            R.id.widget_chip_websearch,
            createModePendingIntent(context, SearchMode.WEB_SEARCH, RC_WEBSEARCH)
        )

        // Deep Search Chip -> Launch App into Deep Search mode
        views.setOnClickPendingIntent(
            R.id.widget_chip_deepsearch,
            createModePendingIntent(context, SearchMode.DEEP_SEARCH, RC_DEEPSEARCH)
        )

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun createModePendingIntent(
        context: Context,
        mode: SearchMode,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_WIDGET_LAUNCH, true)
            putExtra(EXTRA_SEARCH_MODE, mode.name)
            putExtra(EXTRA_AUTO_FOCUS, true)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
