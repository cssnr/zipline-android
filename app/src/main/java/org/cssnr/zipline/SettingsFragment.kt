package org.cssnr.zipline

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

        val fileNameFormat = findPreference<ListPreference>("file_name_format")
        fileNameFormat?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()

        val launcherAction = findPreference<ListPreference>("launcher_action")
        launcherAction?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()

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

        val sendFeedback = findPreference<Preference>("send_feedback")
        sendFeedback?.setOnPreferenceClickListener {
            Log.d("sendFeedback", "setOnPreferenceClickListener")
            showFeedbackDialog()
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

    fun showFeedbackDialog() {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_feedback, null)
        val input = view.findViewById<EditText>(R.id.feedback_input)

        val dialog = MaterialAlertDialogBuilder(requireContext())
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
                    val api = FeedbackApi(requireContext())
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
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    }
                } else {
                    sendButton.isEnabled = true
                    input.error = "Feedback is Required"
                }
            }

            input.requestFocus()
            val link = view.findViewById<TextView>(R.id.github_link)
            val linkText = getString(R.string.github_link, "Visit GitHub for More")
            link.text = Html.fromHtml(linkText, Html.FROM_HTML_MODE_LEGACY)
            link.movementMethod = LinkMovementMethod.getInstance()
            //val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            //imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Send") { _, _ -> }
        dialog.show()
    }
}
