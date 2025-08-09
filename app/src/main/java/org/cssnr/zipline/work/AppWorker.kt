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
import org.cssnr.zipline.ui.user.updateUser
import org.cssnr.zipline.widget.WidgetProvider

class AppWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        Log.d("DailyWorker", "doWork: START")
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        // TODO: Exit here if savedUrl is Null or Empty...
        val savedUrl = preferences.getString("ziplineUrl", null).toString()
        Log.d("DailyWorker", "savedUrl: $savedUrl")
        val workUpdateStats = preferences.getBoolean("work_update_stats", false)
        Log.d("DailyWorker", "workUpdateStats: $workUpdateStats")
        val workUpdateUser = preferences.getBoolean("work_update_user", false)
        Log.d("DailyWorker", "workUpdateUser: $workUpdateUser")
        val workUpdateAvatar = preferences.getBoolean("work_update_avatar", false)
        Log.d("DailyWorker", "workUpdateAvatar: $workUpdateAvatar")
        applicationContext.debugLog("DailyWorker: Running for: $savedUrl - workUpdateStats: $workUpdateStats - workUpdateUser: $workUpdateUser - workUpdateAvatar: $workUpdateAvatar")

        if (workUpdateStats) {
            Log.d("DailyWorker", "--- Update Stats")
            try {
                applicationContext.updateStats()
            } catch (e: Throwable) {
                Log.e("DailyWorker", "updateStats: Exception: $e")
                applicationContext.debugLog("updateStats: Exception: $e")
            }
        }

        if (workUpdateUser) {
            Log.d("DailyWorker", "--- Update User")
            try {
                applicationContext.updateUser()
            } catch (e: Throwable) {
                Log.e("DailyWorker", "updateUser: Exception: $e")
                applicationContext.debugLog("updateUser: Exception: $e")
            }
        }

        // TODO: This requires a Context.updateAvatar (currently only Activity)
        //if (workUpdateAvatar) {
        //    Log.d("DailyWorker", "--- Update Avatar")
        //    try {
        //        applicationContext.updateAvatar()
        //    } catch (e: Throwable) {
        //        Log.e("DailyWorker", "updateAvatar: Exception: $e")
        //        applicationContext.debugLog("updateAvatar: Exception: $e")
        //    }
        //}

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
