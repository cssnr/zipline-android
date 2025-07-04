package org.cssnr.zipline.ui.upload

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cssnr.zipline.R
import org.cssnr.zipline.api.ServerApi
import org.cssnr.zipline.copyToClipboard
import org.cssnr.zipline.databinding.FragmentShortBinding

class ShortFragment : Fragment() {

    private var _binding: FragmentShortBinding? = null
    private val binding get() = _binding!!

    private lateinit var navController: NavController

    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("ShortFragment", "onCreateView: $savedInstanceState")
        _binding = FragmentShortBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }

    override fun onDestroyView() {
        Log.d("ShortFragment", "onDestroyView")
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("Short[onViewCreated]", "savedInstanceState: $savedInstanceState")
        Log.d("Short[onViewCreated]", "arguments: $arguments")

        navController = findNavController()

        val url = requireArguments().getString("url")
        Log.d("Short[onViewCreated]", "url: $url")

        if (url == null) {
            // TODO: Better Handle this Error
            Log.e("Short[onViewCreated]", "URL is null")
            Toast.makeText(requireContext(), "No URL to Process!", Toast.LENGTH_LONG).show()
            return
        }

        val savedUrl = preferences.getString("ziplineUrl", null)
        Log.d("Short[onViewCreated]", "savedUrl: $savedUrl")
        val authToken = preferences.getString("ziplineToken", null)
        Log.d("Short[onViewCreated]", "authToken: $authToken")

        if (savedUrl == null) {
            Log.e("Short[onViewCreated]", "savedUrl is null")
            Toast.makeText(requireContext(), "Missing URL!", Toast.LENGTH_LONG)
                .show()
            navController.navigate(
                R.id.nav_item_login, null, NavOptions.Builder()
                    .setPopUpTo(R.id.nav_item_home, true)
                    .build()
            )
            return
        }

        binding.urlText.setText(url)

        binding.shareButton.setOnClickListener {
            Log.d("shareButton", "setOnClickListener")
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
            startActivity(Intent.createChooser(shareIntent, null))
        }

        binding.optionsButton.setOnClickListener {
            Log.d("optionsButton", "setOnClickListener")
            navController.navigate(R.id.nav_item_settings)
        }

        binding.openButton.setOnClickListener {
            Log.d("openButton", "setOnClickListener")
            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                data = url.toUri()
            }
            startActivity(Intent.createChooser(openIntent, null))
        }

        binding.shortButton.setOnClickListener {
            val longUrl = binding.urlText.text.toString().trim()
            Log.d("uploadButton", "longUrl: $longUrl")
            val vanityName = binding.vanityName.text.toString().trim()
            Log.d("uploadButton", "vanityName: $vanityName")
            processShort(longUrl, vanityName)
        }
    }

    private fun processShort(longUrl: String, vanityName: String?) {
        Log.d("processShort", "URL: $longUrl")
        Log.d("processShort", "Vanity: $vanityName")

        val savedUrl = preferences.getString("ziplineUrl", null)
        Log.d("processShort", "savedUrl: $savedUrl")
        val authToken = preferences.getString("ziplineToken", null)
        Log.d("processShort", "authToken: $authToken")
        val shareUrl = preferences.getBoolean("share_after_short", true)
        Log.d("processShort", "shareUrl: $shareUrl")

        if (savedUrl == null || authToken == null) {
            Log.e("processShort", "ziplineUrl || ziplineToken is null")
            Toast.makeText(requireContext(), "Missing Zipline Authentication!", Toast.LENGTH_LONG)
                .show()
            navController.navigate(
                R.id.nav_item_login, null, NavOptions.Builder()
                    .setPopUpTo(R.id.nav_item_home, true)
                    .build()
            )
            return
        }

        val api = ServerApi(requireContext())
        lifecycleScope.launch {
            val response = api.shorten(longUrl, vanityName)
            Log.d("processShort", "response: $response")
            if (response.isSuccessful) {
                val shortResponse = response.body()
                if (shortResponse != null) {
                    Log.d("processShort", "shortResponse.url: ${shortResponse.url}")
                    copyToClipboard(requireContext(), shortResponse.url)
                    if (shareUrl) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shortResponse.url)
                        }
                        startActivity(Intent.createChooser(shareIntent, null))
                    }
                    navController.navigate(
                        R.id.nav_item_home,
                        bundleOf("url" to "${savedUrl}/dashboard/urls"),
                        NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, inclusive = true)
                            .build()
                    )
                    Log.d("processShort", "DONE")
                    return@launch
                }
            }
            Log.e("processShort", "response/shortResponse is null")
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "File Upload Failed!", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }
}
