package org.cssnr.zipline.work

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.cssnr.zipline.api.ServerApi
import org.cssnr.zipline.db.ServerDao
import org.cssnr.zipline.db.ServerDatabase
import org.cssnr.zipline.db.ServerEntity
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

suspend fun Context.updateStats(): Boolean {
    Log.d("updateStats", "updateStats")
    val preferences = PreferenceManager.getDefaultSharedPreferences(this)
    val savedUrl = preferences.getString("ziplineUrl", null).toString()
    Log.d("updateStats", "savedUrl: $savedUrl")
    val api = ServerApi(this, savedUrl)
    val statsResponse = api.stats()
    Log.d("updateStats", "statsResponse: $statsResponse")
    if (statsResponse.isSuccessful) {
        val stats = statsResponse.body()
        Log.d("updateStats", "stats: $stats")
        if (stats != null) {
            val dao: ServerDao = ServerDatabase.getInstance(this).serverDao()
            dao.upsert(
                ServerEntity(
                    url = savedUrl,
                    filesUploaded = stats.filesUploaded,
                    favoriteFiles = stats.favoriteFiles,
                    views = stats.views,
                    avgViews = stats.avgViews,
                    storageUsed = stats.storageUsed,
                    avgStorageUsed = stats.avgStorageUsed,
                    urlsCreated = stats.urlsCreated,
                    urlViews = stats.urlViews,
                )
            )
            Log.d("updateStats", "dao.upsert: DONE")
            return true
        }
    }
    return false
}
