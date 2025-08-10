package org.cssnr.zipline.work

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

fun Context.enqueueWorkRequest(
    workInterval: String? = null,
    existingPeriodicWorkPolicy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
) {
    Log.i("AppWorkManager", "enqueueWorkRequest: $existingPeriodicWorkPolicy")
    val preferences = PreferenceManager.getDefaultSharedPreferences(this)
    //val interval = (preferences.getString("work_interval", null) ?: "0").toLong()
    val interval = workInterval?.toLongOrNull()
        ?: preferences.getString("work_interval", null)?.toLongOrNull() ?: 0
    Log.i("AppWorkManager", "interval: $interval")
    if (interval < 15) {
        Log.i("AppWorkManager", "RETURN on interval < 15")
        return
    }
    val workRequestBuilder =
        PeriodicWorkRequestBuilder<AppWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(getWorkerConstraints(preferences))
    if (existingPeriodicWorkPolicy == ExistingPeriodicWorkPolicy.UPDATE) {
        Log.i("AppWorkManager", "workRequestBuilder.setInitialDelay: $interval")
        workRequestBuilder.setInitialDelay(interval, TimeUnit.MINUTES)
    }
    //val workRequest = workRequestBuilder.build()
    //Log.i("AppWorkManager", "workRequest: $workRequest")
    WorkManager.getInstance(this).enqueueUniquePeriodicWork(
        "app_worker",
        existingPeriodicWorkPolicy,
        workRequestBuilder.build(),
    )
}
