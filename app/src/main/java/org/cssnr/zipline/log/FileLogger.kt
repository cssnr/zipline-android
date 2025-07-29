package org.cssnr.zipline.log

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugFileLogger {

    private val logMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debugEnabled = false
    private var lastPrefs: SharedPreferences? = null

    fun initialize(context: Context) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (preferences != lastPrefs) {
            lastPrefs = preferences
            debugEnabled = preferences.getBoolean("enable_debug_logs", false)
            preferences.registerOnSharedPreferenceChangeListener { sharedPreferences, key ->
                if (key == "enable_debug_logs") {
                    debugEnabled = sharedPreferences.getBoolean(key, false)
                }
            }
        }
    }

    fun log(context: Context, message: String) {
        if (!debugEnabled) return
        val logFile = File(context.filesDir, "debug_log.txt")
        val timeStamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
        val logMessage = "$timeStamp - $message\n"

        scope.launch {
            logMutex.withLock {
                try {
                    FileWriter(logFile, true).use { fw ->
                        BufferedWriter(fw).use { bw ->
                            bw.write(logMessage)
                        }
                    }
                } catch (e: IOException) {
                    Log.e("DebugFileLogger", "IOException writing log: $e")
                }
            }
        }
    }
}

fun Context.debugLog(message: String) {
    DebugFileLogger.initialize(this)
    DebugFileLogger.log(this, message)
}
