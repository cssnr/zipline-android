package org.cssnr.zipline

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Context.MODE_PRIVATE
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cssnr.zipline.databinding.FragmentShortBinding

class ShortFragment : Fragment() {

    private var _binding: FragmentShortBinding? = null
    private val binding get() = _binding!!

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

        val url = requireArguments().getString("url")
        Log.d("Short[onViewCreated]", "url: $url")

        if (url == null) {
            // TODO: Better Handle this Error
            Log.e("Short[onViewCreated]", "URL is null")
            Toast.makeText(requireContext(), "No URL to Process!", Toast.LENGTH_LONG).show()
            return
        }

        val sharedPreferences = context?.getSharedPreferences("default_preferences", MODE_PRIVATE)
        val ziplineUrl = sharedPreferences?.getString("ziplineUrl", null)
        val ziplineToken = sharedPreferences?.getString("ziplineToken", null)
        Log.d("Short[onViewCreated]", "ziplineUrl: $ziplineUrl")
        Log.d("Short[onViewCreated]", "ziplineToken: $ziplineToken")

        if (ziplineUrl == null || ziplineToken == null) {
            Log.e("Short[onViewCreated]", "ziplineUrl || ziplineToken is null")
            Toast.makeText(requireContext(), "Missing Zipline Authentication!", Toast.LENGTH_LONG)
                .show()
            parentFragmentManager.beginTransaction()
                .replace(R.id.main, SetupFragment())
                .commit()
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
            parentFragmentManager.beginTransaction()
                .replace(R.id.main, SettingsFragment())
                .addToBackStack(null)
                .commit()
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

        val sharedPreferences = context?.getSharedPreferences("default_preferences", MODE_PRIVATE)
        val ziplineUrl = sharedPreferences?.getString("ziplineUrl", null)
        val ziplineToken = sharedPreferences?.getString("ziplineToken", null)
        Log.d("onViewCreated", "ziplineUrl: $ziplineUrl")
        Log.d("onViewCreated", "ziplineToken: $ziplineToken")

        if (ziplineUrl == null || ziplineToken == null) {
            Log.e("onViewCreated", "ziplineUrl || ziplineToken is null")
            Toast.makeText(requireContext(), "Missing Zipline Authentication!", Toast.LENGTH_LONG)
                .show()
            parentFragmentManager.beginTransaction()
                .replace(R.id.main, SetupFragment())
                .commit()
            return
        }

        val api = ZiplineApi(requireContext())
        lifecycleScope.launch {
            val response = api.shorten(longUrl, vanityName, ziplineUrl)
            Log.d("processShort", "response: $response")
            if (response != null) {
                Log.d("processShort", "result.url: ${response.url}")
                copyToClipboard(response.url)

                val activity = requireActivity()
                Log.d("processShort", "activity: $activity")
                parentFragmentManager.beginTransaction()
                    .remove(this@ShortFragment)
                    .commit()
                activity.window.decorView.post {
                    val home = HomeFragment()
                    Log.d("processShort", "home: $home")
                    home.arguments = bundleOf("url" to "${ziplineUrl}/dashboard/urls")
                    Log.d("processShort", "arguments.url: ${ziplineUrl}/dashboard/urls")
                    activity.supportFragmentManager.beginTransaction()
                        .replace(R.id.main, home)
                        .commit()
                }
                Log.d("processShort", "DONE")
            } else {
                Log.e("processShort", "uploadedFile is null")
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "File Upload Failed!", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    private fun copyToClipboard(url: String) {
        Log.d("copyToClipboard", "url: $url")
        val clipboard = requireContext().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("URL", url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "Copied URL to Clipboard.", Toast.LENGTH_SHORT).show()
    }
}
