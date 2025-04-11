package org.cssnr.zipline

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.shape.CornerFamily
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

        val uri = arguments?.getString("uri")?.toUri()
        Log.d("onViewCreated", "uri: $uri")
        val type = arguments?.getString("type")
        Log.d("onViewCreated", "type: $type")

        if (uri == null) {
            // TODO: Better Handle this Error
            Log.e("onViewCreated", "URI is null")
            Toast.makeText(requireContext(), "Error Parsing URI!", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = getFileNameFromUri(requireContext(), uri)
        Log.d("onViewCreated", "fileName: $fileName")
        binding.fileName.text = fileName

        if (type?.startsWith("image/") == true) {
            // Show Image Preview
            binding.imagePreview.setImageURI(uri)
        } else {
            // Set Tint of Icon
            val typedValue = TypedValue()
            val theme = binding.imagePreview.context.theme
            theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            val tint = ContextCompat.getColor(binding.imagePreview.context, typedValue.resourceId)
            val dimmedTint = ColorUtils.setAlphaComponent(tint, (0.5f * 255).toInt())
            binding.imagePreview.setColorFilter(dimmedTint, PorterDuff.Mode.SRC_IN)
            // Set Mime Type Text
            binding.imageOverlayText.text = type
            binding.imageOverlayText.visibility = View.VISIBLE
            // Set Icon Based on Type
            // TODO: Create Mapping...
            if (type?.startsWith("text/") == true) {
                binding.imagePreview.setImageResource(R.drawable.baseline_text_snippet_24)
            } else if (type?.startsWith("video/") == true) {
                binding.imagePreview.setImageResource(R.drawable.baseline_video_file_24)
            } else if (type?.startsWith("audio/") == true) {
                binding.imagePreview.setImageResource(R.drawable.baseline_audio_file_24)
            } else {
                binding.imagePreview.setImageResource(R.drawable.baseline_insert_drive_file_24)
            }
        }

        val radius = resources.getDimension(R.dimen.image_radius)
        binding.imagePreview.setShapeAppearanceModel(
            binding.imagePreview.shapeAppearanceModel
                .toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, radius)
                .build()
        )

        val sharedPreferences = context?.getSharedPreferences("default_preferences", MODE_PRIVATE)
        val ziplineUrl = sharedPreferences?.getString("ziplineUrl", null)
        val ziplineToken = sharedPreferences?.getString("ziplineToken", null)
        Log.d("onViewCreated", "ziplineUrl: $ziplineUrl")
        Log.d("onViewCreated", "ziplineToken: $ziplineToken")

        if (ziplineUrl == null || ziplineToken == null) {
            Log.e("onViewCreated", "ziplineUrl || ziplineToken is null")
            Toast.makeText(requireContext(), "Missing Zipline Authentication!", Toast.LENGTH_SHORT)
                .show()
            parentFragmentManager.beginTransaction()
                .replace(R.id.main, SetupFragment())
                .commit()
            return
        }

        binding.shareButton.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                this.type = type
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, null))
        }

        binding.openButton.setOnClickListener {
            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, type)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(openIntent, null))
        }


        binding.uploadButton.setOnClickListener {
            processUpload(uri, ziplineUrl, ziplineToken)
        }
    }

    private fun processUpload(uri: Uri, ziplineUrl: String, ziplineToken: String) {
        // TODO: Cleanup to work with multiple files and previews...
        Log.d("processUpload", "File URI: $uri")
        //if (uri == null) {
        //    Toast.makeText(requireContext(), "Error Parsing URI!", Toast.LENGTH_SHORT).show()
        //    Log.w("processUpload", "URI is null")
        //    return
        //}
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
