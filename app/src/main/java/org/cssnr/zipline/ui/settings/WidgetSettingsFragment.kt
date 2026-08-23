package org.cssnr.zipline.ui.settings

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.cssnr.zipline.R
import org.cssnr.zipline.databinding.FragmentWidgetSettingsBinding

class WidgetSettingsFragment : PreferenceFragmentCompat() {

    private var _binding: FragmentWidgetSettingsBinding? = null
    private val binding get() = _binding!!

    private val navController by lazy { findNavController() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.d("WidgetSettingsFragment", "onCreateView: $savedInstanceState")
        // Inflate a custom layout with a title/back header and nest the
        // PreferenceFragmentCompat view (with its RecyclerView) inside of it.
        _binding = FragmentWidgetSettingsBinding.inflate(inflater, container, false)
        val prefsView = super.onCreateView(inflater, binding.preferenceContainer, savedInstanceState)
        Log.d("WidgetSettingsFragment", "prefsView: $prefsView")
        binding.preferenceContainer.addView(prefsView)
        return binding.root
    }

    override fun onDestroyView() {
        Log.d("WidgetSettingsFragment", "onDestroyView")
        super.onDestroyView()
        _binding = null
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

    override fun onStop() {
        Log.d("Settings[onStop]", "onStop - Show UI")
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav).visibility =
            View.VISIBLE
        super.onStop()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("WidgetSettingsFragment", "onViewCreated: $savedInstanceState")

        binding.goBack.setOnClickListener {
            Log.d("WidgetSettingsFragment", "binding.goBack: navController.navigateUp()")
            navController.navigateUp()
        }

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
