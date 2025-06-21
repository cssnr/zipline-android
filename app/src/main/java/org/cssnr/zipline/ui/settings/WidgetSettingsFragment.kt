package org.cssnr.zipline.ui.settings

import android.os.Bundle
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import org.cssnr.zipline.R

class WidgetSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d("WidgetSettingsFragment", "rootKey: $rootKey")
        setPreferencesFromResource(R.xml.preferences_widget, rootKey)

        // Text Color
        val textColor = findPreference<ListPreference>("widget_text_color")
        Log.d("WidgetSettingsFragment", "textColor: $textColor")
        textColor?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()

        // BG Color
        val bgColor = findPreference<ListPreference>("widget_bg_color")
        Log.d("WidgetSettingsFragment", "bgColor: $bgColor")
        bgColor?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()

        // BG Opacity
        val bgOpacity = preferenceManager.sharedPreferences?.getInt("widget_bg_opacity", 25)
        Log.d("WidgetSettingsFragment", "bgOpacity: $bgOpacity")
        val seekBar = findPreference<SeekBarPreference>("widget_bg_opacity")
        seekBar?.summary = "Current Value: $bgOpacity"
        seekBar?.apply {
            setOnPreferenceChangeListener { pref, newValue ->
                val intValue = (newValue as Int)
                var stepped = ((intValue + 2) / 5) * 5
                Log.d("WidgetSettingsFragment", "stepped: $stepped")
                value = stepped
                pref.summary = "Current Value: $stepped"
                false
            }
        }
    }
}
