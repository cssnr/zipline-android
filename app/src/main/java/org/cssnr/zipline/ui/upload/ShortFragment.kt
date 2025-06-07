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
import org.cssnr.zipline.api.ZiplineApi
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

        //val callback = object : OnBackPressedCallback(true) {
        //    override fun handleOnBackPressed() {
        //        requireActivity().finish()
        //    }
        //}
        //requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

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
                R.id.nav_item_setup, null, NavOptions.Builder()
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

        val ziplineUrl = preferences.getString("ziplineUrl", null)
        val ziplineToken = preferences.getString("ziplineToken", null)
        Log.d("processShort", "ziplineUrl: $ziplineUrl")
        Log.d("processShort", "ziplineToken: $ziplineToken")

        if (ziplineUrl == null || ziplineToken == null) {
            Log.e("processShort", "ziplineUrl || ziplineToken is null")
            Toast.makeText(requireContext(), "Missing Zipline Authentication!", Toast.LENGTH_LONG)
                .show()
            navController.navigate(
                R.id.nav_item_setup, null, NavOptions.Builder()
                    .setPopUpTo(R.id.nav_item_home, true)
                    .build()
            )
            return
        }

        val api = ZiplineApi(requireContext())
        lifecycleScope.launch {
            val response = api.shorten(longUrl, vanityName, ziplineUrl)
            Log.d("processShort", "response: $response")
            if (response.isSuccessful) {
                val shortResponse = response.body()
                if (shortResponse != null) {
                    Log.d("processShort", "shortResponse.url: ${shortResponse.url}")
                    copyToClipboard(requireContext(), shortResponse.url)
                    val shareUrl = preferences.getBoolean("share_after_short", true)
                    Log.d("processShort", "shareUrl: $shareUrl")
                    if (shareUrl) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shortResponse.url)
                        }
                        startActivity(Intent.createChooser(shareIntent, null))
                    }
                    navController.navigate(
                        R.id.nav_item_home,
                        bundleOf("url" to "${ziplineUrl}/dashboard/urls"),
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

//    // TODO: This is the DjangoFiles processShort, Old Zipline processShort was used above...
//    private fun processShort(url: String, vanity: String?) {
//        Log.d("processShort", "url: $url")
//        Log.d("processShort", "vanity: $vanity")
//        //val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
//        val savedUrl = preferences.getString("ziplineUrl", null)
//        Log.d("processShort", "savedUrl: $savedUrl")
//        val authToken = preferences.getString("ziplineToken", null)
//        Log.d("processShort", "authToken: $authToken")
//        if (savedUrl == null || authToken == null) {
//            // TODO: Show settings dialog here...
//            Log.w("processShort", "Missing OR savedUrl/authToken")
//            Toast.makeText(requireContext(), getString(R.string.tst_no_url), Toast.LENGTH_SHORT)
//                .show()
//            return
//        }
//        val api = ZiplineApi(requireContext())
//        Log.d("processShort", "api: $api")
//        Toast.makeText(requireContext(), "Creating Short URL...", Toast.LENGTH_SHORT).show()
//        lifecycleScope.launch {
//            try {
//                val response = api.shorten(url, vanity, savedUrl)
//                Log.d("processShort", "response: $response")
//                if (response.isSuccessful) {
//                    val shortResponse = response.body()
//                    Log.d("processShort", "shortResponse: $shortResponse")
//                    withContext(Dispatchers.Main) {
//                        if (shortResponse != null) {
//                            copyToClipboard(requireContext(), shortResponse.url)
//                            val shareUrl = sharedPreferences.getBoolean("share_after_short", true)
//                            Log.d("processShort", "shareUrl: $shareUrl")
//                            if (shareUrl) {
//                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
//                                    type = "text/plain"
//                                    putExtra(Intent.EXTRA_TEXT, shortResponse.url)
//                                }
//                                startActivity(Intent.createChooser(shareIntent, null))
//                            }
//                            navController.navigate(
//                                R.id.nav_item_home,
//                                bundleOf("url" to "${savedUrl}/shorts/#shorts-table_wrapper"),
//                                NavOptions.Builder()
//                                    .setPopUpTo(R.id.nav_graph, inclusive = true)
//                                    .build()
//                            )
//                        } else {
//                            Log.w("processShort", "shortResponse is null")
//                            val msg = "Unknown Response!"
//                            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
//                        }
//                    }
//                } else {
//                    val msg = "Error: ${response.code()}: ${response.message()}"
//                    Log.w("processShort", "Error: $msg")
//                    withContext(Dispatchers.Main) {
//                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
//                    }
//                }
//            } catch (e: Exception) {
//                e.printStackTrace()
//                val msg = e.message ?: "Unknown Error!"
//                Log.i("processShort", "msg: $msg")
//                withContext(Dispatchers.Main) {
//                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
//                }
//            }
//        }
//    }
}
