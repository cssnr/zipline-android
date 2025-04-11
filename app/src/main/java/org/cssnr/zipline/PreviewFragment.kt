package org.cssnr.zipline

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Context.MODE_PRIVATE
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cssnr.zipline.databinding.FragmentPreviewBinding

class PreviewFragment : Fragment() {

    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("onCreateView", "savedInstanceState: $savedInstanceState")
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("onViewCreated", "savedInstanceState: $savedInstanceState")

        val uri = requireArguments().getString("uri")?.toUri()
        Log.d("onViewCreated", "uri: $uri")
        binding.imagePreview.setImageURI(uri)

        val sharedPreferences = context?.getSharedPreferences("default_preferences", MODE_PRIVATE)
        val ziplineUrl = sharedPreferences?.getString("ziplineUrl", null)
        val ziplineToken = sharedPreferences?.getString("ziplineToken", null)
        Log.d("handleIntent", "ziplineUrl: $ziplineUrl")
        Log.d("handleIntent", "ziplineToken: $ziplineToken")

        if (ziplineUrl == null || ziplineToken == null) {
            Log.e("handleIntent", "ziplineUrl || ziplineToken is null")
            Toast.makeText(requireContext(), "Missing Zipline Authentication!", Toast.LENGTH_SHORT)
                .show()
            parentFragmentManager.beginTransaction()
                .replace(R.id.main, SetupFragment())
                .commit()
            return
        }

        binding.uploadButton.setOnClickListener {
            processUpload(uri, ziplineUrl, ziplineToken)
        }
    }

    private fun processUpload(uri: Uri?, ziplineUrl: String, ziplineToken: String) {
        // TODO: Cleanup to work with multiple files and previews...
        Log.d("processUpload", "File URI: $uri")
        if (uri == null) {
            Toast.makeText(requireContext(), "Error Parsing URI!", Toast.LENGTH_SHORT).show()
            Log.w("processUpload", "URI is null")
            return
        }
        val api = ZiplineApi(requireContext())
        lifecycleScope.launch {
            val response = api.upload(uri, ziplineUrl, ziplineToken)
            Log.d("processUpload", "response: $response")
            val result = response?.files?.firstOrNull()
            Log.d("processUpload", "result: $result")
            if (result != null) {
                Log.d("processUpload", "result.url: ${result.url}")
                copyToClipboard(result.url)
                val main = activity as MainActivity
                main.loadUrl(result.url)
                Log.d("processUpload", "parentFragmentManager.popBackStack()")
                parentFragmentManager.beginTransaction()
                    .remove(this@PreviewFragment)
                    .commit()
            } else {
                Log.w("processUpload", "uploadedFile is null")
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "File Upload Failed!", Toast.LENGTH_SHORT)
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
