package org.cssnr.zipline

import android.os.Bundle
import android.util.Log
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager


class SettingsFragment : PreferenceFragmentCompat() {
    companion object {
        private const val PREFS_NAME = "default_preferences"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d("SettingsFragment", "onCreatePreferences rootKey: $rootKey")

        setPreferencesFromResource(R.xml.preferences, rootKey)

        val savedUrlPref = findPreference<EditTextPreference>("saved_url")
        Log.d("SettingsFragment", "savedUrlPref: $savedUrlPref")

        //val preferences = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        Log.d("SettingsFragment", "preferences: $preferences")

        var showPreview = preferences?.getBoolean("show_preview", false)
        Log.d("SettingsFragment", "showPreview: $showPreview")
        var enableBiometrics = preferences?.getBoolean("enable_biometrics", false)
        Log.d("SettingsFragment", "enableBiometrics: $enableBiometrics")

        //PreferenceManager.getDefaultSharedPreferences(requireContext())
        //    .edit { putString("saved_url", newUrl) }
    }
}
