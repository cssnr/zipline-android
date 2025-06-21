package org.cssnr.zipline.work

import androidx.work.Constraints
import androidx.work.NetworkType

val APP_WORKER_CONSTRAINTS: Constraints = Constraints.Builder()
    .setRequiresBatteryNotLow(true)
    .setRequiresCharging(false)
    .setRequiresDeviceIdle(false)
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()
