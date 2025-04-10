package org.cssnr.zipline

import android.content.Context.MODE_PRIVATE
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
import kotlinx.coroutines.launch
import org.cssnr.zipline.databinding.FragmentSetupBinding

class SetupFragment : Fragment() {

    private var _binding: FragmentSetupBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.d("onCreateView", "savedInstanceState: $savedInstanceState")
        _binding = FragmentSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("onViewCreated", "savedInstanceState: $savedInstanceState")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            binding.root.setOnApplyWindowInsetsListener { _, insets ->
                val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                binding.root.setPadding(0, 0, 0, imeInsets.bottom)
                insets
            }
        }

        val link: TextView = binding.githubLink
        link.text = Html.fromHtml(getString(R.string.github_link), Html.FROM_HTML_MODE_LEGACY)
        link.movementMethod = LinkMovementMethod.getInstance()

        binding.loginHostname.setText("https://")
        binding.loginHostname.requestFocus()

        binding.loginButton.setOnClickListener {
            val inputHost = binding.loginHostname.text.toString().trim()
            Log.d("setOnClickListener", "inputHost: $inputHost")
            val host = parseHost(inputHost)
            if (inputHost != host) {
                binding.loginHostname.setText(host)
            }
            Log.d("setOnClickListener", "host: $host")
            val user = binding.loginUsername.text.toString().trim()
            Log.d("setOnClickListener", "User: $user")
            val pass = binding.loginPassword.text.toString().trim()
            Log.d("setOnClickListener", "Pass: $pass")

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

            Log.d("setOnClickListener", "lifecycleScope.launch")
            lifecycleScope.launch {
                val api = ZiplineApi(requireContext())
                val token = api.login(host, user, pass)
                Log.d("lifecycleScope.launch", "token: $token")
                if (token.isNullOrEmpty()) {
                    Log.d("lifecycleScope.launch", "LOGIN FAILED")
                    Toast.makeText(context, "Login Failed!", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d("lifecycleScope.launch", "LOGIN SUCCESS")
                    val sharedPreferences =
                        context?.getSharedPreferences("default_preferences", MODE_PRIVATE)
                    sharedPreferences?.edit { putString("ziplineUrl", host) }
                    Log.d("getSharedPreferences", "ziplineUrl: $host")
                    sharedPreferences?.edit { putString("ziplineToken", token) }
                    Log.d("getSharedPreferences", "ziplineToken: $token")
                    val mainActivity = activity as MainActivity
                    Log.d("lifecycleScope.launch", "mainActivity.loadUrl: $host")
                    mainActivity.loadUrl(host)
                    activity?.supportFragmentManager?.beginTransaction()
                        ?.remove(this@SetupFragment)
                        ?.commit()
                }
            }

            //val mainActivity = activity as MainActivity
            //mainActivity.loadUrl("https://intranet.cssnr.com")
            //activity?.supportFragmentManager?.beginTransaction()?.remove(this)?.commit()
            Log.d("setOnClickListener", "DONE")
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
