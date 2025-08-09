package org.cssnr.zipline.work

import android.content.SharedPreferences
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType


fun getWorkerConstraints(preferences: SharedPreferences): Constraints {
    val workMetered = preferences.getBoolean("work_metered", false)
    Log.d("AppWorkManager", "getWorkerConstraints: workMetered: $workMetered")
    val networkType = if (workMetered) NetworkType.CONNECTED else NetworkType.UNMETERED
    Log.d("AppWorkManager", "getWorkerConstraints: networkType: $networkType")
    return Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .setRequiresCharging(false)
        .setRequiresDeviceIdle(false)
        .setRequiredNetworkType(networkType)
        .build()
}
