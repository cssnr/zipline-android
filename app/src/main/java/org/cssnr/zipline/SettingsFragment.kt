package org.cssnr.zipline

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

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

        val ctx = requireContext()

        val launcherAction = findPreference<ListPreference>("launcher_action")
        launcherAction?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()

        val fileNameFormat = findPreference<ListPreference>("file_name_format")
        fileNameFormat?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()

        val toggleAnalytics = findPreference<SwitchPreferenceCompat>("analytics_enabled")
        toggleAnalytics?.setOnPreferenceChangeListener { _, newValue ->
            Log.d("toggleAnalytics", "analytics_enabled: $newValue")
            if (newValue as Boolean) {
                Log.d("toggleAnalytics", "ENABLE Analytics")
                Firebase.analytics.setAnalyticsCollectionEnabled(true)
                toggleAnalytics.isChecked = true
            } else {
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("Please Reconsider")
                    .setMessage("Analytics are only used to fix bugs and make improvements.")
                    .setPositiveButton("Disable Anyway") { _, _ ->
                        Log.d("toggleAnalytics", "DISABLE Analytics")
                        Firebase.analytics.setAnalyticsCollectionEnabled(false)
                        toggleAnalytics.isChecked = false
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
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
}
