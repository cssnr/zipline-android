package org.cssnr.zipline.ui.setup

import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
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
import org.cssnr.zipline.api.ZiplineApi
import org.cssnr.zipline.databinding.FragmentSetupBinding

class SetupFragment : Fragment() {

    private var _binding: FragmentSetupBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.d("SetupFragment", "onCreateView: $savedInstanceState")
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            binding.root.setOnApplyWindowInsetsListener { _, insets ->
                val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                binding.root.setPadding(0, 0, 0, imeInsets.bottom)
                insets
            }
        }

        val link: TextView = binding.githubLink
        val linkText = getString(R.string.github_link, "github.com/cssnr/zipline-android")
        link.text = Html.fromHtml(linkText, Html.FROM_HTML_MODE_LEGACY)
        link.movementMethod = LinkMovementMethod.getInstance()

        binding.loginHostname.setText("https://")
        binding.loginHostname.requestFocus()

        binding.loginButton.setOnClickListener {
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
            if (!valid) return@setOnClickListener

            Log.d("loginButton", "lifecycleScope.launch")
            lifecycleScope.launch {
                val api = ZiplineApi(requireContext())
                val token = api.login(host, user, pass)
                Log.d("loginButton", "token: $token")
                if (token.isNullOrEmpty()) {
                    Log.d("loginButton", "LOGIN FAILED")
                    Toast.makeText(context, "Login Failed!", Toast.LENGTH_SHORT).show()
                    Firebase.analytics.logEvent("login_failed", null)
                } else {
                    Log.d("loginButton", "LOGIN SUCCESS")
                    val preferences =
                        PreferenceManager.getDefaultSharedPreferences(requireContext())
                    preferences?.edit { putString("ziplineUrl", host) }
                    Log.d("loginButton", "ziplineUrl: $host")
                    preferences?.edit { putString("ziplineToken", token) }
                    Log.d("loginButton", "ziplineToken: $token")
                    Firebase.analytics.logEvent("login_success", null)
                    findNavController().navigate(
                        R.id.nav_item_home, null, NavOptions.Builder()
                            .setPopUpTo(R.id.nav_item_setup, true)
                            .build()
                    )
                }
            }
            Log.d("loginButton", "DONE")
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
