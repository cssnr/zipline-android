package org.cssnr.zipline

import android.os.Bundle
import android.util.Log
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.PreferenceFragmentCompat


class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d("SettingsFragment", "onCreatePreferences rootKey: $rootKey")

        preferenceManager.sharedPreferencesName = "default_preferences"
        setPreferencesFromResource(R.xml.preferences_settings, rootKey)

        val showPreviewPref = findPreference<SwitchPreferenceCompat>("show_preview")
        Log.d("SettingsFragment", "showPreviewPref: $showPreviewPref")

        showPreviewPref?.setOnPreferenceChangeListener { _, newValue ->
            val newBool = newValue as Boolean
            Log.d("showPreviewPref", "show_preview: $newBool")
            true
        }

        //val preferences = context?.getSharedPreferences("default_preferences", Context.MODE_PRIVATE)
        //val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        //Log.d("SettingsFragment", "preferences: $preferences")

        //var showPreview = preferences?.getBoolean("show_preview", false)
        //Log.d("SettingsFragment", "showPreview: $showPreview")
        //var enableBiometrics = preferences?.getBoolean("enable_biometrics", false)
        //Log.d("SettingsFragment", "enableBiometrics: $enableBiometrics")

        //PreferenceManager.getDefaultSharedPreferences(requireContext())
        //    .edit { putString("saved_url", newUrl) }
    }
}
