package org.cssnr.zipline.ui.settings

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.cssnr.zipline.R

class WidgetSettingsFragment : PreferenceFragmentCompat() {

    override fun onStart() {
        super.onStart()
        Log.d("Settings[onStart]", "onStart: $arguments")
        if (arguments?.getBoolean("hide_bottom_nav") == true) {
            Log.d("Settings[onStart]", "BottomNavigationView = View.GONE")
            requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav).visibility =
                View.GONE
        }
    }

    override fun onStop() {
        Log.d("Settings[onStop]", "onStop - Show UI")
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav).visibility =
            View.VISIBLE
        super.onStop()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            Log.d("ViewCompat", "top: ${bars.top}")
            v.updatePadding(top = bars.top)

            if (arguments?.getBoolean("hide_bottom_nav") == true) {
                Log.d("ViewCompat", "bottom: ${bars.bottom}")
                v.updatePadding(bottom = bars.bottom)
            }
            insets
        }
    }

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
                val stepped = ((intValue + 2) / 5) * 5
                Log.d("WidgetSettingsFragment", "stepped: $stepped")
                value = stepped
                pref.summary = "Current Value: $stepped"
                false
            }
        }
    }
}
