package org.cssnr.zipline.work

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.cssnr.zipline.log.debugLog
import org.cssnr.zipline.ui.user.updateStats
import org.cssnr.zipline.widget.WidgetProvider

class AppWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        Log.d("DailyWorker", "doWork: START")
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val savedUrl = preferences.getString("ziplineUrl", null).toString()
        Log.d("DailyWorker", "savedUrl: $savedUrl")

        Log.d("DailyWorker", "--- Update Stats")
        try {
            applicationContext.updateStats()
        } catch (e: Exception) {
            Log.e("DailyWorker", "updateStats: Exception: $e")
            applicationContext.debugLog("AppWorker: doWork: Exception: $e")
        }

        // Update Widget
        // TODO: WidgetUpdate: Consolidate to a function...
        Log.d("DailyWorker", "--- Update Widget")
        val componentName = ComponentName(applicationContext, WidgetProvider::class.java)
        Log.d("DailyWorker", "componentName: $componentName")
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setClassName(
            applicationContext.packageName,
            "org.cssnr.zipline.widget.WidgetProvider"
        ).apply {
            val ids =
                AppWidgetManager.getInstance(applicationContext).getAppWidgetIds(componentName)
            Log.d("DailyWorker", "ids: $ids")
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        Log.d("DailyWorker", "sendBroadcast: $intent")
        applicationContext.sendBroadcast(intent)

        return Result.success()
    }
}
