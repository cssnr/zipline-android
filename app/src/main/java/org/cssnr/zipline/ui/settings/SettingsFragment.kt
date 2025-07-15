package org.cssnr.zipline.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cssnr.zipline.R
import org.cssnr.zipline.api.FeedbackApi
import org.cssnr.zipline.work.APP_WORKER_CONSTRAINTS
import org.cssnr.zipline.work.AppWorker
import java.util.concurrent.TimeUnit

class SettingsFragment : PreferenceFragmentCompat() {

    // TODO: Determine why I put this here...
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.d("SettingsFragment", "onCreateView: $savedInstanceState")
        val view: View = super.onCreateView(inflater, container, savedInstanceState)
        val color = MaterialColors.getColor(view, android.R.attr.colorBackground)
        view.setBackgroundColor(color)
        return view
    }

    override fun onStart() {
        super.onStart()
        Log.d("Settings[onStart]", "onStart: $arguments")
        if (arguments?.getBoolean("hide_bottom_nav") == true) {
            Log.d("Settings[onStart]", "BottomNavigationView = View.GONE")
            requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav).visibility =
                View.GONE
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d("SettingsFragment", "rootKey: $rootKey")
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val ctx = requireContext()

        // Start Destination
        val startDestination = findPreference<ListPreference>("start_destination")
        startDestination?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()

        // File Name Option
        val fileNameFormat = findPreference<ListPreference>("file_name_format")
        fileNameFormat?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()

        // Files Per Page
        val filesPerPage = preferenceManager.sharedPreferences?.getInt("files_per_page", 25)
        Log.d("onCreatePreferences", "filesPerPage: $filesPerPage")
        val filesSeekBar = findPreference<SeekBarPreference>("files_per_page")
        filesSeekBar?.summary = "Current Value: $filesPerPage"
        filesSeekBar?.apply {
            setOnPreferenceChangeListener { pref, newValue ->
                val intValue = (newValue as Int)
                var stepped = ((intValue + 2) / 5) * 5
                if (stepped < 10) stepped = 10
                Log.d("onCreatePreferences", "stepped: $stepped")
                value = stepped
                pref.summary = "Current Value: $stepped"
                false
            }
        }

        // Widget Settings
        findPreference<Preference>("open_widget_settings")?.setOnPreferenceClickListener {
            Log.d("open_widget_settings", "setOnPreferenceClickListener")
            findNavController().navigate(R.id.nav_action_settings_widget)
            false
        }

        // Update Interval
        val workInterval = findPreference<ListPreference>("work_interval")
        workInterval?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        workInterval?.setOnPreferenceChangeListener { _, newValue ->
            Log.d("work_interval", "newValue: $newValue")
            ctx.updateWorkManager(workInterval, newValue)
        }

        // Toggle Analytics
        val analyticsEnabled = findPreference<SwitchPreferenceCompat>("analytics_enabled")
        analyticsEnabled?.setOnPreferenceChangeListener { _, newValue ->
            Log.d("analyticsEnabled", "analytics_enabled: $newValue")
            ctx.toggleAnalytics(analyticsEnabled, newValue)
            false
        }

        // Send Feedback
        val sendFeedback = findPreference<Preference>("send_feedback")
        sendFeedback?.setOnPreferenceClickListener {
            Log.d("sendFeedback", "setOnPreferenceClickListener")
            ctx.showFeedbackDialog()
            false
        }

        // Show App Info
        findPreference<Preference>("app_info")?.setOnPreferenceClickListener {
            Log.d("app_info", "showAppInfoDialog")
            ctx.showAppInfoDialog()
            false
        }

        // Open App Settings
        findPreference<Preference>("android_settings")?.setOnPreferenceClickListener {
            Log.d("android_settings", "setOnPreferenceClickListener")
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", ctx.packageName, null)
            }
            startActivity(intent)
            false
        }

        //val showPreviewPref = findPreference<SwitchPreferenceCompat>("show_preview")
        //showPreviewPref?.setOnPreferenceChangeListener { _, newValue ->
        //    val newBool = newValue as Boolean
        //    Log.d("showPreviewPref", "show_preview: $newBool")
        //    true
        //}

        //var showPreview = preferences?.getBoolean("show_preview", false)
        //Log.d("SettingsFragment", "showPreview: $showPreview")
        //var enableBiometrics = preferences?.getBoolean("biometrics_enabled", false)
        //Log.d("SettingsFragment", "enableBiometrics: $enableBiometrics")
    }

    fun Context.updateWorkManager(listPref: ListPreference, newValue: Any): Boolean {
        Log.d("updateWorkManager", "listPref: ${listPref.value} - newValue: $newValue")
        val value = newValue as? String
        Log.d("updateWorkManager", "String value: $value")
        if (value.isNullOrEmpty()) {
            Log.w("updateWorkManager", "NULL OR EMPTY - false")
            return false
        } else if (listPref.value == value) {
            Log.i("updateWorkManager", "NO CHANGE - false")
            return false
        } else {
            Log.i("updateWorkManager", "RESCHEDULING WORK - true")
            val interval = value.toLongOrNull()
            Log.i("updateWorkManager", "interval: $interval")
            if (interval == null || interval == 0L) {
                Log.i("updateWorkManager", "DISABLING WORK")
                WorkManager.getInstance(this).cancelUniqueWork("app_worker")
                return true
            } else {
                val newRequest =
                    PeriodicWorkRequestBuilder<AppWorker>(interval, TimeUnit.MINUTES)
                        .setInitialDelay(interval, TimeUnit.MINUTES)
                        .setConstraints(APP_WORKER_CONSTRAINTS)
                        .build()
                WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "app_worker",
                    ExistingPeriodicWorkPolicy.REPLACE,
                    newRequest
                )
                return true
            }
        }
    }

    fun Context.toggleAnalytics(switchPreference: SwitchPreferenceCompat, newValue: Any) {
        Log.d("toggleAnalytics", "newValue: $newValue")
        if (newValue as Boolean) {
            Log.d("toggleAnalytics", "ENABLE Analytics")
            Firebase.analytics.setAnalyticsCollectionEnabled(true)
            switchPreference.isChecked = true
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle("Please Reconsider")
                .setMessage("Analytics are only used to fix bugs and make improvements.")
                .setPositiveButton("Disable Anyway") { _, _ ->
                    Log.d("toggleAnalytics", "DISABLE Analytics")
                    Firebase.analytics.logEvent("disable_analytics", null)
                    Firebase.analytics.setAnalyticsCollectionEnabled(false)
                    switchPreference.isChecked = false
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    fun Context.showFeedbackDialog() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_feedback, null)
        val input = view.findViewById<EditText>(R.id.feedback_input)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Send", null)
            .create()

        dialog.setOnShowListener {
            val sendButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            sendButton.setOnClickListener {
                sendButton.isEnabled = false
                val message = input.text.toString().trim()
                Log.d("showFeedbackDialog", "message: $message")
                if (message.isNotEmpty()) {
                    val api = FeedbackApi(this)
                    lifecycleScope.launch {
                        val response = withContext(Dispatchers.IO) { api.sendFeedback(message) }
                        Log.d("showFeedbackDialog", "response: $response")
                        val msg = if (response.isSuccessful) {
                            findPreference<Preference>("send_feedback")?.isEnabled = false
                            dialog.dismiss()
                            "Feedback Sent. Thank You!"
                        } else {
                            sendButton.isEnabled = true
                            val params = Bundle().apply {
                                putString("message", response.message())
                                putString("code", response.code().toString())
                            }
                            Firebase.analytics.logEvent("feedback_failed", params)
                            "Error: ${response.code()}"
                        }
                        Log.d("showFeedbackDialog", "msg: $msg")
                        Toast.makeText(this@showFeedbackDialog, msg, Toast.LENGTH_LONG).show()
                    }
                } else {
                    sendButton.isEnabled = true
                    input.error = "Feedback is Required"
                }
            }

            input.requestFocus()

            val link = view.findViewById<TextView>(R.id.github_link)
            val linkText = getString(R.string.github_link, link.tag)
            link.text = Html.fromHtml(linkText, Html.FROM_HTML_MODE_LEGACY)
            link.movementMethod = LinkMovementMethod.getInstance()

            //val imm = this.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            //imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Send") { _, _ -> }
        dialog.show()
    }

    fun Context.showAppInfoDialog() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_app_info, null)
        val appId = view.findViewById<TextView>(R.id.app_identifier)
        val appVersion = view.findViewById<TextView>(R.id.app_version)
        val sourceLink = view.findViewById<TextView>(R.id.source_link)

        val sourceText = getString(R.string.github_link, sourceLink.tag)
        Log.d("showAppInfoDialog", "sourceText: $sourceText")

        val packageInfo = this.packageManager.getPackageInfo(this.packageName, 0)
        val versionName = packageInfo.versionName
        Log.d("showAppInfoDialog", "versionName: $versionName")

        val formattedVersion = getString(R.string.version_string, versionName)
        Log.d("showAppInfoDialog", "formattedVersion: $formattedVersion")

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .setNegativeButton("Close", null)
            .create()

        dialog.setOnShowListener {
            appId.text = this.packageName
            appVersion.text = formattedVersion

            sourceLink.text = Html.fromHtml(sourceText, Html.FROM_HTML_MODE_LEGACY)
            sourceLink.movementMethod = LinkMovementMethod.getInstance()
        }
        dialog.show()
    }
}
