package org.cssnr.zipline.ui.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import androidx.work.WorkManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cssnr.zipline.R
import org.cssnr.zipline.api.FeedbackApi
import org.cssnr.zipline.ui.dialogs.FolderFragment
import org.cssnr.zipline.work.enqueueWorkRequest

class SettingsFragment : PreferenceFragmentCompat() {

    private val navController by lazy { findNavController() }

    private val workMeteredPref by lazy { findPreference<SwitchPreferenceCompat>("work_metered") }
    private val workUpdateStatsPref by lazy { findPreference<SwitchPreferenceCompat>("work_update_stats") }
    private val workUpdateUserPref by lazy { findPreference<SwitchPreferenceCompat>("work_update_user") }
    private val workUpdateAvatarPref by lazy { findPreference<SwitchPreferenceCompat>("work_update_avatar") }

    private lateinit var preferences: SharedPreferences

    //// TODO: Determine why I put this here...
    //override fun onCreateView(
    //    inflater: LayoutInflater,
    //    container: ViewGroup?,
    //    savedInstanceState: Bundle?,
    //): View {
    //    Log.d("SettingsFragment", "onCreateView: $savedInstanceState")
    //    val view: View = super.onCreateView(inflater, container, savedInstanceState)
    //    val color = MaterialColors.getColor(view, android.R.attr.colorBackground)
    //    view.setBackgroundColor(color)
    //    return view
    //}

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
        // TODO: Determine how to initialize preferences
        preferences = preferenceManager.sharedPreferences!!
        //preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // Start Destination
        val startDestination = findPreference<ListPreference>("start_destination")
        startDestination?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()

        // File Name Format
        val fileNameFormat = findPreference<ListPreference>("file_name_format")
        fileNameFormat?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()

        // File Deletes At
        // TODO: Use a custom dialog with examples and link to docs...
        val fileDeletesAt = findPreference<EditTextPreference>("file_deletes_at")
        fileDeletesAt?.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
        fileDeletesAt?.setOnPreferenceChangeListener { preference, newValue ->
            Log.d("setOnPreferenceChangeListener", "fileDeletesAt: \"${(newValue as String)}\"")
            val normalized = newValue.filter { it.isLetterOrDigit() }
            if (normalized.isNotEmpty()) {
                val pattern = Regex(
                    "^\\d+(ms|msec|msecs|millisecond|milliseconds|s|sec|secs|second|seconds|m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days|w|week|weeks|y|yr|yrs|year|years)$",
                    RegexOption.IGNORE_CASE
                )
                if (pattern.matches(normalized)) {
                    (preference as EditTextPreference).text = normalized
                }
            } else {
                (preference as EditTextPreference).text = null
            }
            false
        }

        // File Folder
        val fileFolderName = preferences.getString("file_folder_name", null)
        val fileFolderId = findPreference<Preference>("file_folder_id")
        fileFolderId?.setSummary(fileFolderName ?: "Not Set")
        fileFolderId?.setOnPreferenceClickListener {
            setFragmentResultListener("folder_fragment_result") { _, bundle ->
                Log.d("setFragmentResultListener", "bundle: $bundle")
                val folderId = bundle.getString("folderId")
                val folderName = bundle.getString("folderName")
                Log.i("setFragmentResultListener", "SAVE FOLDER: $folderName - $folderId")
                preferences.edit {
                    putString("file_folder_id", folderId)
                    putString("file_folder_name", folderName)
                }
                fileFolderId.setSummary(folderName ?: "Not Set")
            }

            lifecycleScope.launch {
                val folderFragment = FolderFragment()
                val latestId = folderFragment.setFolderData(ctx)
                folderFragment.show(parentFragmentManager, "FolderFragment")
                Log.d("Settings", "latestId: $latestId")
                val currentId = preferences.getString("file_folder_id", null)
                Log.d("Settings", "currentId: $currentId")
                if (currentId != latestId) {
                    Log.i("Settings", "RESET FOLDER: currentId != latestId!")
                    fileFolderId.setSummary("Not Set")
                    preferences.edit {
                        putString("file_folder_id", null)
                        putString("file_folder_name", null)
                    }
                }
            }
            false
        }

        // File Compression
        val fileCompression = preferences.getInt("file_compression", 0)
        Log.d("onCreatePreferences", "fileCompression: $fileCompression")
        val fileCompressionBar = findPreference<SeekBarPreference>("file_compression")
        fileCompressionBar?.summary = "Current Value: ${fileCompression}%"
        fileCompressionBar?.apply {
            setOnPreferenceChangeListener { pref, newValue ->
                pref.summary = "Current Value: ${newValue}%"
                true
            }
        }

        // Custom Headers
        findPreference<Preference>("custom_headers")?.setOnPreferenceClickListener {
            Log.d("custom_headers", "setOnPreferenceClickListener")
            navController.navigate(R.id.nav_item_headers)
            false
        }

        // Files Per Page
        val filesPerPage = preferences.getInt("files_per_page", 25)
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
            navController.navigate(R.id.nav_action_settings_widget, arguments)
            false
        }

        // Work Update Interval
        val workInterval = findPreference<ListPreference>("work_interval")
        updateWorkIntervalSettings(workInterval?.value)
        workInterval?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        workInterval?.setOnPreferenceChangeListener { _, newValue ->
            Log.d("work_interval", "newValue: $newValue")
            updateWorkIntervalSettings(newValue as String)
            ctx.updateWorkManager(newValue, workInterval.value)
        }

        // Work Metered
        workMeteredPref?.setOnPreferenceChangeListener { _, newValue ->
            Log.d("work_metered", "newValue: $newValue")
            val result = newValue as Boolean
            Log.d("work_metered", "result: $result")
            workMeteredPref?.isChecked = result
            ctx.enqueueWorkRequest()
            false
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

        // Debugging
        val viewDebugLogs = findPreference<Preference>("view_debug_logs")
        viewDebugLogs?.setOnPreferenceClickListener {
            Log.d("viewDebugLogs", "setOnPreferenceClickListener")
            navController.navigate(R.id.nav_item_settings_debug)
            false
        }
    }

    private fun updateWorkIntervalSettings(selectedValue: String?) {
        Log.i("updateWorkIntervalSettings", "selectedValue: $selectedValue")
        if (selectedValue != null) {
            val enabled = selectedValue != "0"
            Log.d("updateWorkIntervalSettings", "enabled: $enabled")
            workMeteredPref?.isEnabled = enabled
            workUpdateStatsPref?.isEnabled = enabled
            workUpdateUserPref?.isEnabled = enabled
            workUpdateAvatarPref?.isEnabled = enabled
        }
    }

    private fun Context.updateWorkManager(newValue: String?, curValue: String? = null): Boolean {
        Log.i("updateWorkManager", "newValue: $newValue - curValue: $curValue")
        if (newValue.isNullOrEmpty()) {
            Log.w("updateWorkManager", "newValue.isNullOrEmpty() - false")
            return false
        } else if (curValue == newValue) {
            Log.i("updateWorkManager", "curValue == newValue - false")
            return false
        } else {
            Log.d("updateWorkManager", "ELSE - RESCHEDULING WORK - true")
            if (newValue == "0" || newValue.toLongOrNull() == null) {
                Log.i("updateWorkManager", "DISABLING WORK - newValue is 0 or null")
                WorkManager.getInstance(this).cancelUniqueWork("app_worker")
                return true
            } else {
                enqueueWorkRequest(newValue)
                return true
            }
        }
    }

    private fun Context.toggleAnalytics(switchPreference: SwitchPreferenceCompat, newValue: Any) {
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

    private fun Context.showFeedbackDialog() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_feedback, null)

        val input = view.findViewById<EditText>(R.id.feedback_input)
        val websiteLink = view.findViewById<TextView>(R.id.website_link)
        val githubLink = view.findViewById<TextView>(R.id.github_link)

        websiteLink.paint?.isUnderlineText = true
        websiteLink.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, websiteLink.tag.toString().toUri()))
        }
        githubLink.paint?.isUnderlineText = true
        githubLink.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, githubLink.tag.toString().toUri()))
        }

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
        }
        dialog.show()
    }

    private fun Context.showAppInfoDialog() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_app_info, null)

        val appId = view.findViewById<TextView>(R.id.app_identifier)
        val versionName = view.findViewById<TextView>(R.id.version_name)
        val githubLink = view.findViewById<TextView>(R.id.github_link)
        val websiteLink = view.findViewById<TextView>(R.id.website_link)

        githubLink.paint?.isUnderlineText = true
        githubLink.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, githubLink.tag.toString().toUri()))
        }
        websiteLink.paint?.isUnderlineText = true
        websiteLink.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, websiteLink.tag.toString().toUri()))
        }

        val packageInfo = this.packageManager.getPackageInfo(this.packageName, 0)
        val formattedVersion = getString(
            R.string.version_code_string,
            packageInfo.versionName,
            packageInfo.versionCode.toString()
        )
        Log.d("showAppInfoDialog", "formattedVersion: $formattedVersion")

        appId.text = this.packageName
        versionName.text = formattedVersion

        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setNegativeButton("Close", null)
            .create()
            .show()
    }
}
