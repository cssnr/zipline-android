package org.cssnr.zipline.ui.setup

import android.animation.ObjectAnimator
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import org.cssnr.zipline.MainActivity
import org.cssnr.zipline.R
import org.cssnr.zipline.api.ServerApi
import org.cssnr.zipline.databinding.FragmentSetupBinding

class SetupFragment : Fragment() {

    private var _binding: FragmentSetupBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSetupBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }

    override fun onDestroyView() {
        Log.d("SetupFragment", "onDestroyView")
        super.onDestroyView()
        _binding = null
        // Unlock Navigation Drawer
        (requireActivity() as MainActivity).setDrawerLockMode(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("onViewCreated", "savedInstanceState: $savedInstanceState")

        // Lock Navigation Drawer
        (requireActivity() as MainActivity).setDrawerLockMode(false)

        // TODO: Determine if this is necessary...
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            binding.root.setOnApplyWindowInsetsListener { _, insets ->
                val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                binding.root.setPadding(0, 0, 0, imeInsets.bottom)
                insets
            }
        }

        val ctx = requireContext()

        //binding.loginHostname.setText("https://")
        binding.loginHostname.requestFocus()

        val versionName = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        binding.appVersion.text = ctx.getString(R.string.version_string, versionName)

        val link: TextView = binding.githubLink
        val linkText = getString(R.string.github_link, "github.com/cssnr/zipline-android")
        link.text = Html.fromHtml(linkText, Html.FROM_HTML_MODE_LEGACY)
        link.movementMethod = LinkMovementMethod.getInstance()

        binding.serverText.text =
            Html.fromHtml(getString(R.string.setup_zipline_text), Html.FROM_HTML_MODE_LEGACY)

        binding.loginButton.setOnClickListener {
            it.isEnabled = false
            binding.loginError.visibility = View.INVISIBLE
            val inputHost = binding.loginHostname.text.toString().trim()
            Log.d("loginButton", "inputHost: $inputHost")
            val host = parseHost(inputHost)
            if (inputHost != host) {
                binding.loginHostname.setText(host)
            }
            Log.d("loginButton", "host: $host")
            val user = binding.loginUsername.text.toString().trim()
            Log.d("loginButton", "User: $user")
            val pass = binding.loginPassword.text.toString().trim()
            Log.d("loginButton", "Pass: $pass")

            var valid = true
            if (host.isEmpty() || host == "https://") {
                binding.loginHostname.error = "Required"
                valid = false
            }
            if (user.isEmpty()) {
                binding.loginUsername.error = "Required"
                valid = false
            }
            if (pass.isEmpty()) {
                binding.loginPassword.error = "Required"
                valid = false
            }
            if (!valid) {
                it.isEnabled = true
                return@setOnClickListener
            }

            Log.d("loginButton", "lifecycleScope.launch")
            lifecycleScope.launch {
                val api = ServerApi(ctx, host)
                val token = api.login(host, user, pass)
                Log.d("loginButton", "token: $token")
                if (token.isNullOrEmpty()) {
                    Log.d("loginButton", "LOGIN FAILED")
                    binding.loginError.visibility = View.VISIBLE
                    Toast.makeText(ctx, "Login Failed!", Toast.LENGTH_SHORT).show()

                    val shake = ObjectAnimator.ofFloat(
                        binding.loginButton, "translationX",
                        0f, 25f, -25f, 20f, -20f, 15f, -15f, 6f, -6f, 0f
                    )
                    shake.duration = 800
                    shake.start()
                    val red =
                        ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                    val original = ContextCompat.getColor(requireContext(), R.color.button_color)
                    binding.loginButton.setBackgroundColor(red)
                    binding.loginButton.postDelayed({
                        binding.loginButton.setBackgroundColor(original)
                    }, 700)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        binding.loginButton.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    } else {
                        binding.loginButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }

                    Firebase.analytics.logEvent("login_failed", null)
                } else {
                    Log.d("loginButton", "LOGIN SUCCESS")
                    val preferences =
                        PreferenceManager.getDefaultSharedPreferences(ctx)
                    preferences?.edit { putString("ziplineUrl", host) }
                    Log.d("loginButton", "ziplineUrl: $host")
                    preferences?.edit { putString("ziplineToken", token) }
                    Log.d("loginButton", "ziplineToken: $token")
                    Firebase.analytics.logEvent("login_success", null)
                    val bundle = bundleOf()
                    // TODO: Consider managing first run logic in MainActivity...
                    if (!preferences.getBoolean("first_run_shown", false)) {
                        preferences?.edit { putBoolean("first_run_shown", true) }
                        bundle.putBoolean("isFirstRun", true)
                    }
                    Log.d("loginButton", "bundle: $bundle")
                    findNavController().navigate(
                        R.id.nav_item_home, bundle, NavOptions.Builder()
                            .setPopUpTo(R.id.nav_item_setup, true)
                            .build()
                    )
                }
                it.isEnabled = true
                Log.d("loginButton", "lifecycleScope: DONE")
            }
        }
    }

    private fun parseHost(urlString: String): String {
        var url = urlString.trim()
        if (url.isEmpty()) {
            return ""
        }
        if (!url.lowercase().startsWith("http")) {
            url = "https://$url"
        }
        if (url.length < 9) {
            return "https://"
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length - 1)
        }
        //if (!Patterns.WEB_URL.matcher(url).matches()) {
        //    Log.d("parseHost", "Patterns.WEB_URL.matcher Failed")
        //    return ""
        //}
        return url
    }
}
