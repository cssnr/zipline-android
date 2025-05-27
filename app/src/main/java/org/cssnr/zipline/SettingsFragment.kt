package org.cssnr.zipline

import android.content.Context
import android.os.Bundle
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
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cssnr.zipline.api.FeedbackApi

class SettingsFragment : PreferenceFragmentCompat() {

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

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d("SettingsFragment", "onCreatePreferences rootKey: $rootKey")

        preferenceManager.sharedPreferencesName = "default_preferences"
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // File Name Option
        val fileNameFormat = findPreference<ListPreference>("file_name_format")
        fileNameFormat?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()

        // Launcher Icon Action
        val launcherAction = findPreference<ListPreference>("launcher_action")
        launcherAction?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()

        // Toggle Analytics
        val toggleAnalytics = findPreference<SwitchPreferenceCompat>("analytics_enabled")
        toggleAnalytics?.setOnPreferenceChangeListener { _, newValue ->
            Log.d("toggleAnalytics", "analytics_enabled: $newValue")
            if (newValue as Boolean) {
                Log.d("toggleAnalytics", "ENABLE Analytics")
                Firebase.analytics.setAnalyticsCollectionEnabled(true)
                toggleAnalytics.isChecked = true
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Please Reconsider")
                    .setMessage("Analytics are only used to fix bugs and make improvements.")
                    .setPositiveButton("Disable Anyway") { _, _ ->
                        Log.d("toggleAnalytics", "DISABLE Analytics")
                        Firebase.analytics.logEvent("disable_analytics", null)
                        Firebase.analytics.setAnalyticsCollectionEnabled(false)
                        toggleAnalytics.isChecked = false
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            false
        }

        // Send Feedback
        val sendFeedback = findPreference<Preference>("send_feedback")
        sendFeedback?.setOnPreferenceClickListener {
            Log.d("sendFeedback", "setOnPreferenceClickListener")
            requireContext().showFeedbackDialog()
            false
        }

        // Show App Info
        findPreference<Preference>("app_info")?.setOnPreferenceClickListener {
            Log.d("app_info", "showAppInfoDialog")
            requireContext().showAppInfoDialog()
            false
        }

        //val showPreviewPref = findPreference<SwitchPreferenceCompat>("show_preview")
        //showPreviewPref?.setOnPreferenceChangeListener { _, newValue ->
        //    val newBool = newValue as Boolean
        //    Log.d("showPreviewPref", "show_preview: $newBool")
        //    true
        //}

        //val preferences = context?.getSharedPreferences("default_preferences", Context.MODE_PRIVATE)
        //val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        //Log.d("SettingsFragment", "preferences: $preferences")

        //var showPreview = preferences?.getBoolean("show_preview", false)
        //Log.d("SettingsFragment", "showPreview: $showPreview")
        //var enableBiometrics = preferences?.getBoolean("biometrics_enabled", false)
        //Log.d("SettingsFragment", "enableBiometrics: $enableBiometrics")

        //PreferenceManager.getDefaultSharedPreferences(requireContext())
        //    .edit { putString("saved_url", newUrl) }
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
