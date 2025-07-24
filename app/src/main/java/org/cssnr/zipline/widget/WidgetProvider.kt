package org.cssnr.zipline.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.format.DateFormat
import android.text.format.Formatter
import android.util.Log
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.cssnr.zipline.MainActivity
import org.cssnr.zipline.R
import org.cssnr.zipline.db.ServerDao
import org.cssnr.zipline.db.ServerDatabase
import org.cssnr.zipline.work.updateStats

class WidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.i("Widget[onReceive]", "intent: $intent")

        if (intent.action == "org.cssnr.zipline.REFRESH_WIDGET") {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                return
            }
            Log.i("Widget[onReceive]", "CoroutineScope.launch: START")
            CoroutineScope(Dispatchers.IO).launch {
                context.updateStats()
                val appWidgetManager = AppWidgetManager.getInstance(context)
                onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
                Log.i("Widget[onReceive]", "CoroutineScope.launch: DONE")
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.i("Widget[onUpdate]", "BEGIN - appWidgetIds: $appWidgetIds")

        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val savedUrl = preferences.getString("ziplineUrl", null).toString()
        Log.d("Widget[onUpdate]", "savedUrl: $savedUrl")
        val bgColor = preferences.getString("widget_bg_color", null) ?: "black"
        Log.d("Widget[onUpdate]", "bgColor: $bgColor")
        val textColor = preferences.getString("widget_text_color", null) ?: "white"
        Log.d("Widget[onUpdate]", "textColor: $textColor")
        val bgOpacity = preferences.getInt("widget_bg_opacity", 35)
        Log.d("Widget[onUpdate]", "bgOpacity: $bgOpacity")
        val workInterval = preferences.getString("work_interval", null) ?: "0"
        Log.d("Widget[onUpdate]", "workInterval: $workInterval")

        val colorMap = mapOf(
            "white" to Color.WHITE,
            "black" to Color.BLACK,
            "liberty" to "#565AA9".toColorInt(),
        )

        val selectedBgColor = colorMap[bgColor] ?: Color.BLACK
        Log.d("Widget[onUpdate]", "selectedBgColor: $selectedBgColor")
        val selectedTextColor = colorMap[textColor] ?: Color.WHITE
        Log.d("Widget[onUpdate]", "selectedTextColor: $selectedTextColor")

        val opacityPercent = bgOpacity
        val alpha = (opacityPercent * 255 / 100).coerceIn(1, 255)
        val finalBgColor = ColorUtils.setAlphaComponent(selectedBgColor, alpha)
        Log.d("Widget[onUpdate]", "finalBgColor: $finalBgColor")

        appWidgetIds.forEach { appWidgetId ->
            Log.d("Widget[onUpdate]", "START - appWidgetId: $appWidgetId")

            // Widget Root
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val pendingIntent0: PendingIntent = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).apply { action = Intent.ACTION_MAIN },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent0)

            // Set Colors
            views.setInt(R.id.widget_root, "setBackgroundColor", finalBgColor)

            views.setTextColor(R.id.files_count, selectedTextColor)
            views.setTextColor(R.id.files_size, selectedTextColor)
            views.setTextColor(R.id.files_unit, selectedTextColor)
            views.setTextColor(R.id.update_time, selectedTextColor)

            //views.setInt(R.id.files_icon, "setColorFilter", selectedTextColor)
            //views.setInt(R.id.size_icon, "setColorFilter", selectedTextColor)

            views.setInt(R.id.widget_refresh_button, "setColorFilter", selectedTextColor)
            views.setInt(R.id.widget_upload_button, "setColorFilter", selectedTextColor)
            //views.setInt(R.id.widget_recent_button, "setColorFilter", selectedTextColor)

            // Refresh
            val intent1 = Intent(context, WidgetProvider::class.java).apply {
                action = "org.cssnr.zipline.REFRESH_WIDGET"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent1 = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent1,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_button, pendingIntent1)
            appWidgetManager.updateAppWidget(appWidgetId, views)

            // Upload File
            val intent2 = Intent(context, MainActivity::class.java).apply { action = "UPLOAD_FILE" }
            val pendingIntent2 = PendingIntent.getActivity(
                context,
                0,
                intent2,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_upload_button, pendingIntent2)

            //// Recent
            //val pendingIntent3 = PendingIntent.getActivity(
            //    context,
            //    0,
            //    Intent(context, MainActivity::class.java).apply { action = "RECENT_FILE" },
            //    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            //)
            //views.setOnClickPendingIntent(R.id.widget_recent_button, pendingIntent3)

            // Room Data
            CoroutineScope(Dispatchers.IO).launch {
                val dao: ServerDao =
                    ServerDatabase.Companion.getInstance(context.applicationContext).serverDao()
                Log.d("Widget[onUpdate]", "dao: $dao")
                val server = dao.get(savedUrl)
                Log.d("Widget[onUpdate]", "server: $server")
                if (server != null) {
                    Log.d("Widget[onUpdate]", "server.filesUploaded: ${server.filesUploaded}")
                    views.setTextViewText(R.id.files_count, server.filesUploaded.toString())

                    Log.d("Widget[onUpdate]", "server.humanSize: ${server.storageUsed}")
                    val humanSize =
                        Formatter.formatShortFileSize(context, server.storageUsed?.toLong() ?: 0)
                    Log.d("Widget[onUpdate]", "humanSize: $humanSize")

                    val split = humanSize.split(' ')
                    Log.d("Widget[onUpdate]", "split: $split")
                    views.setTextViewText(R.id.files_size, split.getOrElse(0) { "0" })
                    views.setTextViewText(R.id.files_unit, split.getOrElse(1) { "" })
                }

                if (workInterval == "0") {
                    views.setTextViewText(R.id.update_time, "Disabled")
                } else if (server != null) {
                    val time = DateFormat.getTimeFormat(context).format(server.updatedAt)
                    Log.d("Widget[onUpdate]", "time: $time")
                    views.setTextViewText(R.id.update_time, time)
                } else {
                    views.setTextViewText(R.id.update_time, "Refresh Data")
                }
                Log.d("Widget[onUpdate]", "appWidgetManager.updateAppWidget: $appWidgetId")
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }

            // This is done at the end of the GlobalScope above
            //appWidgetManager.updateAppWidget(appWidgetId, views)
            Log.d("Widget[onUpdate]", "DONE - appWidgetId: $appWidgetId")
        }
        Log.i("Widget[onUpdate]", "END - onUpdate FINISHED")
    }
}
