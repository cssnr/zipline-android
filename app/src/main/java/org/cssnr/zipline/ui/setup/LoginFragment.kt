package org.cssnr.zipline.ui.setup

import android.animation.ObjectAnimator
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.cssnr.zipline.MainActivity
import org.cssnr.zipline.R
import org.cssnr.zipline.api.ServerApi
import org.cssnr.zipline.api.ServerApi.LoginData
import org.cssnr.zipline.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SetupViewModel by activityViewModels()

    private val navController by lazy { findNavController() }
    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }

    companion object {
        const val LOG_TAG = "LoginFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }

    override fun onDestroyView() {
        Log.d(LOG_TAG, "onDestroyView")
        super.onDestroyView()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        Log.d("Login[onStart]", "onStart - Hide UI and Lock Drawer")
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav).visibility = View.GONE
        (activity as? MainActivity)?.setDrawerLockMode(false)
    }

    override fun onStop() {
        Log.d("Login[onStop]", "onStop - Show UI and Unlock Drawer")
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav).visibility =
            View.VISIBLE
        (activity as? MainActivity)?.setDrawerLockMode(true)
        super.onStop()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("onViewCreated", "savedInstanceState: $savedInstanceState")

        //// TODO: Determine if this is necessary...
        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        //    binding.root.setOnApplyWindowInsetsListener { _, insets ->
        //        Log.i("setOnApplyWindowInsetsListener", "insets: $insets")
        //        val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
        //        binding.root.setPadding(0, 0, 0, imeInsets.bottom)
        //        insets
        //    }
        //}

        val ctx = requireContext()

        val versionName = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        binding.appVersion.text = ctx.getString(R.string.version_string, versionName)

        val linkText = getString(R.string.github_link, "github.com/cssnr/zipline-android")
        binding.githubLink.text = Html.fromHtml(linkText, Html.FROM_HTML_MODE_LEGACY)
        binding.githubLink.movementMethod = LinkMovementMethod.getInstance()

        binding.serverText.text =
            Html.fromHtml(getString(R.string.setup_zipline_text), Html.FROM_HTML_MODE_LEGACY)
        binding.serverText.movementMethod = LinkMovementMethod.getInstance()

        val enableDebugLogs = preferences.getBoolean("enable_debug_logs", false)
        Log.d(LOG_TAG, "enableDebugLogs: $enableDebugLogs")
        binding.debugLogging.visibility = if (enableDebugLogs) View.VISIBLE else View.GONE
        binding.toggleDebugLogs.isChecked = enableDebugLogs

        viewModel.totp.observe(viewLifecycleOwner) { totp ->
            Log.i(LOG_TAG, "viewModel.totp.observe: totp: $totp")
            if (totp) _binding?.loginCode?.visibility = View.VISIBLE
        }

        if (arguments?.getString("url") != null) {
            Log.i(LOG_TAG, "url: ${arguments?.getString("url")}")
            binding.loginHostname.setText(arguments?.getString("url").toString())
            arguments?.remove("url")
            binding.loginUsername.requestFocus()
        } else {
            binding.loginHostname.requestFocus()
        }

        binding.headersButton.setOnClickListener {
            navController.navigate(R.id.nav_item_headers)
        }

        binding.toggleDebugLogs.setOnClickListener {
            Log.d("toggleDebugLogs", "setOnClickListener")
            val result = !preferences.getBoolean("enable_debug_logs", false)
            Log.d("toggleDebugLogs", "result: $result")
            preferences.edit {
                putBoolean("enable_debug_logs", result)
            }
            binding.debugLogging.visibility = if (result) View.VISIBLE else View.GONE
        }
        binding.debugLogging.setOnClickListener {
            Log.d("debugLogging", "setOnClickListener: navigate(R.id.nav_item_settings_debug)")
            navController.navigate(R.id.nav_item_settings_debug)
        }

        binding.loginCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                Log.d("beforeTextChanged", "s: $s - start=$start, count=$count, after=$after")
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                Log.d("onTextChanged", "s: $s - start=$start, before=$before, count=$count")
            }

            override fun afterTextChanged(s: Editable?) {
                Log.d("afterTextChanged", "${s?.length} - $s")
                if (s?.length == 6) {
                    lifecycleScope.launch { ctx.processLogin() }
                }
            }
        })

        binding.loginButton.setOnClickListener {
            lifecycleScope.launch { ctx.processLogin() }
        }
    }

    private suspend fun Context.processLogin() {
        _binding?.loginButton?.isEnabled = false
        _binding?.loginError?.visibility = View.INVISIBLE
        val inputHost = _binding?.loginHostname?.text.toString().trim()
        Log.d("loginButton", "inputHost: $inputHost")
        val host = parseHost(inputHost)
        if (inputHost != host) {
            _binding?.loginHostname?.setText(host)
        }
        Log.d("loginButton", "host: $host")
        val user = _binding?.loginUsername?.text.toString().trim()
        Log.d("loginButton", "user: $user")
        val pass = _binding?.loginPassword?.text.toString().trim()
        Log.d("loginButton", "pass: $pass")
        val code = _binding?.loginCode?.text.toString().trim()
        Log.d("loginButton", "code: $code")

        var valid = true
        if (host.isEmpty() || host == "https://") {
            _binding?.loginHostname?.error = "Required"
            valid = false
        }
        if (valid && host.toHttpUrlOrNull() == null) {
            _binding?.loginHostname?.error = "Invalid Host"
            valid = false
        }
        if (user.isEmpty()) {
            _binding?.loginUsername?.error = "Required"
            valid = false
        }
        if (pass.isEmpty()) {
            _binding?.loginPassword?.error = "Required"
            valid = false
        }
        if (!valid) {
            _binding?.loginButton?.isEnabled = true
            return
        }

        val api = ServerApi(this, host)
        val auth: LoginData = api.login(host, user, pass, code)
        Log.d("loginButton", "auth: $auth")
        if (auth.totp == true) {
            Log.d("loginButton", "TOTP REQUIRED")
            showErrorAnimation(_binding?.loginButton, _binding?.loginError, "Two-Factor Required")
            viewModel.totp.value = true
            _binding?.loginCode?.requestFocus()
        } else if (auth.error != null) {
            Log.d("loginButton", "LOGIN FAILED")
            showErrorAnimation(_binding?.loginButton, _binding?.loginError, auth.error)
            Firebase.analytics.logEvent("login_failed", null)
        } else {
            Log.d("loginButton", "LOGIN SUCCESS")
            preferences.edit {
                putString("ziplineUrl", host)
                putString("ziplineToken", auth.token)
            }
            Log.d("loginButton", "ziplineUrl: $host")
            Log.d("loginButton", "ziplineToken: ${auth.token}")
            Firebase.analytics.logEvent("login_success", null)
            //GlobalScope.launch(Dispatchers.IO) { ctx.updateStats() }
            // TODO: Consider managing first run logic in MainActivity...
            if (!preferences.getBoolean("first_run_shown", false)) {
                preferences.edit { putBoolean("first_run_shown", true) }
                navController.navigate(
                    R.id.nav_action_login_setup, null, NavOptions.Builder()
                        .setPopUpTo(navController.graph.id, true)
                        .build()
                )
            } else {
                navController.navigate(
                    navController.graph.startDestinationId, null, NavOptions.Builder()
                        .setPopUpTo(navController.graph.id, true)
                        .build()
                )
            }
        }
        binding.loginButton.isEnabled = true
        Log.d("loginButton", "lifecycleScope: DONE")
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
        return url
    }

    fun showErrorAnimation(buttonView: View?, textView: TextView? = null, text: String? = null) {
        Log.d("loginFailed", "Context.loginFailed")
        textView.let {
            it?.text = text
            it?.visibility = View.VISIBLE
        }
        val shake = buttonView.let {
            ObjectAnimator.ofFloat(
                it, "translationX",
                0f, 25f, -25f, 20f, -20f, 15f, -15f, 6f, -6f, 0f
            )
        }
        shake?.duration = 800
        shake?.start()
        val red = ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
        val original = ContextCompat.getColor(requireContext(), R.color.button_color)
        buttonView?.setBackgroundColor(red)
        lifecycleScope.launch {
            delay(700)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                buttonView?.setBackgroundColor(original)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            buttonView?.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            buttonView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
}
