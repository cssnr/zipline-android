package org.cssnr.zipline.ui.upload

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cssnr.zipline.R
import org.cssnr.zipline.api.ZiplineApi
import org.cssnr.zipline.copyToClipboard
import org.cssnr.zipline.databinding.FragmentUploadBinding
import org.json.JSONObject

class UploadFragment : Fragment() {

    private var _binding: FragmentUploadBinding? = null
    private val binding get() = _binding!!

    private lateinit var navController: NavController

    private lateinit var player: ExoPlayer
    private lateinit var webView: WebView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("UploadFragment", "onCreateView: $savedInstanceState")
        _binding = FragmentUploadBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }

    override fun onDestroyView() {
        Log.d("UploadFragment", "onDestroyView")
        super.onDestroyView()
        if (::player.isInitialized) {
            Log.d("UploadFragment", "player.release")
            player.release()
        }
        if (::webView.isInitialized) {
            Log.d("UploadFragment", "webView.destroy")
            webView.destroy()
        }
        _binding = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    @OptIn(UnstableApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("Upload[onViewCreated]", "savedInstanceState: $savedInstanceState")
        Log.d("Upload[onViewCreated]", "arguments: $arguments")

        navController = findNavController()

        //val callback = object : OnBackPressedCallback(true) {
        //    override fun handleOnBackPressed() {
        //        requireActivity().finish()
        //    }
        //}
        //requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        //val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        //    requireArguments().getParcelable("EXTRA_INTENT", Intent::class.java)
        //} else {
        //    @Suppress("DEPRECATION")
        //    requireArguments().getParcelable("EXTRA_INTENT") as? Intent
        //}
        //Log.d("Upload[onViewCreated]", "intent: $intent")

        val uri = requireArguments().getString("uri")?.toUri()
        Log.d("Upload[onViewCreated]", "uri: $uri")

        if (uri == null) {
            // TODO: Better Handle this Error
            Log.e("Upload[onViewCreated]", "URI is null")
            Toast.makeText(requireContext(), "No URI to Process!", Toast.LENGTH_LONG).show()
            return
        }

        val mimeType = requireContext().contentResolver.getType(uri)
        Log.d("Upload[onViewCreated]", "mimeType: $mimeType")

        val fileName = getFileNameFromUri(requireContext(), uri)
        Log.d("Upload[onViewCreated]", "fileName: $fileName")
        binding.fileName.setText(fileName)

        // TODO: Overhaul with Glide and ExoPlayer...
        if (mimeType?.startsWith("video/") == true || mimeType?.startsWith("audio/") == true) {
            Log.d("Upload[onViewCreated]", "EXOPLAYER")
            binding.playerView.visibility = View.VISIBLE

            player = ExoPlayer.Builder(requireContext()).build()
            binding.playerView.player = player
            binding.playerView.controllerShowTimeoutMs = 1000
            binding.playerView.setShowNextButton(false)
            binding.playerView.setShowPreviousButton(false)
            val dataSourceFactory = DefaultDataSource.Factory(requireContext())
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri))
            player.setMediaSource(mediaSource)
            player.prepare()

        } else if (isGlideMime(mimeType.toString())) {
            Log.d("Upload[onViewCreated]", "GLIDE")
            binding.imageHolder.visibility = View.VISIBLE

            Glide.with(binding.imagePreview).load(uri).into(binding.imagePreview)

        } else if (mimeType?.startsWith("text/") == true || isCodeMime(mimeType!!)) {
            Log.d("Upload[onViewCreated]", "WEBVIEW")
            webView = WebView(requireContext())
            binding.frameLayout.addView(webView)

            val url = "file:///android_asset/preview/preview.html"
            Log.d("Upload[onViewCreated]", "url: $url")

            val content = requireContext().contentResolver.openInputStream(uri)?.bufferedReader()
                ?.use { it.readText() }
            if (content == null) {
                // TODO: Handle null content error...
                Log.w("Upload[onViewCreated]", "content is null")
                return
            }
            //Log.d("Upload[onViewCreated]", "content: $content")
            val escapedContent = JSONObject.quote(content)
            //Log.d("Upload[onViewCreated]", "escapedContent: $escapedContent")
            val jsString = "addContent(${escapedContent});"
            //Log.d("Upload[onViewCreated]", "jsString: $jsString")
            webView.apply {
                settings.javaScriptEnabled = true
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun notifyReady() {
                        webView.post {
                            Log.i("Upload[onViewCreated]", "evaluateJavascript")
                            webView.evaluateJavascript(jsString, null)
                        }
                    }
                }, "Android")
                Log.d("Upload[onViewCreated]", "loadUrl: $url")
                loadUrl(url)
            }

        } else {
            Log.d("Upload[onViewCreated]", "OTHER")
            binding.imageHolder.visibility = View.VISIBLE

            // Set Tint of Icon
            val typedValue = TypedValue()
            val theme = binding.imagePreview.context.theme
            theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            val tint = ContextCompat.getColor(binding.imagePreview.context, typedValue.resourceId)
            val dimmedTint = ColorUtils.setAlphaComponent(tint, (0.5f * 255).toInt())
            binding.imagePreview.setColorFilter(dimmedTint, PorterDuff.Mode.SRC_IN)
            // Set Mime Type Text
            binding.imageOverlayText.text = mimeType
            binding.imageOverlayText.visibility = View.VISIBLE
            // Set Icon Based on Type
            binding.imagePreview.setImageResource(getGenericIcon(mimeType.toString()))
        }

        binding.shareButton.setOnClickListener {
            Log.d("shareButton", "setOnClickListener")
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                this.type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(openIntent, null))
        }

        binding.uploadButton.setOnClickListener {
            val fileName = binding.fileName.text.toString().trim()
            Log.d("uploadButton", "fileName: $fileName")
            processUpload(uri, fileName)
        }
    }

    // TODO: DUPLICATION: ShortFragment.processShort
    private fun processUpload(fileUri: Uri, fileName: String?) {
        Log.d("processUpload", "fileUri: $fileUri")
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val savedUrl = preferences.getString("ziplineUrl", null)
        Log.d("processUpload", "savedUrl: $savedUrl")
        val authToken = preferences.getString("ziplineToken", null)
        Log.d("processUpload", "authToken: $authToken")
        if (savedUrl == null || authToken == null) {
            // TODO: Show settings dialog here...
            Log.w("processUpload", "Missing OR savedUrl/authToken/fileName")
            Toast.makeText(requireContext(), getString(R.string.tst_no_url), Toast.LENGTH_SHORT)
                .show()
            logFileUpload(false, "URL or Token is null")
            return
        }
        val fileName = fileName ?: getFileNameFromUri(requireContext(), fileUri)
        Log.d("processUpload", "fileName: $fileName")
        if (fileName == null) {
            Log.w("processUpload", "Unable to parse fileName from URI")
            Toast.makeText(requireContext(), "Unable to Parse File Name", Toast.LENGTH_SHORT)
                .show()
            logFileUpload(false, "File Name is null")
            return
        }
        //val contentType = URLConnection.guessContentTypeFromName(fileName)
        //Log.d("processUpload", "contentType: $contentType")
        val inputStream = requireContext().contentResolver.openInputStream(fileUri)
        if (inputStream == null) {
            Log.w("processUpload", "inputStream is null")
            val msg = getString(R.string.tst_upload_error)
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            logFileUpload(false, "Input Stream is null")
            return
        }
        Log.d("processUpload", "DEBUG 1")
        val api = ZiplineApi(requireContext())
        Log.d("processUpload", "DEBUG 2")
        Log.d("processUpload", "api: $api")
        Toast.makeText(requireContext(), getString(R.string.tst_uploading_file), Toast.LENGTH_SHORT)
            .show()
        lifecycleScope.launch {
            try {
                val response = api.upload(fileName, inputStream)
                Log.d("processUpload", "response: $response")
                if (response.isSuccessful) {
                    val uploadResponse = response.body()
                    Log.d("processUpload", "uploadResponse: $uploadResponse")
                    withContext(Dispatchers.Main) {
                        if (uploadResponse != null) {
                            logFileUpload()
                            copyToClipboard(requireContext(), uploadResponse.files.first().url)
                            navController.navigate(
                                R.id.nav_item_home,
                                bundleOf("url" to uploadResponse.files.first().url),
                                NavOptions.Builder()
                                    .setPopUpTo(R.id.nav_graph, inclusive = true)
                                    .build()
                            )
                        } else {
                            Log.w("processUpload", "uploadResponse is null")
                            val msg = "Unknown Response!"
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                            logFileUpload(false, "Upload Response is null")
                        }
                    }
                } else {
                    val msg = "Error: ${response.code()}: ${response.message()}"
                    Log.w("processUpload", "Error: $msg")
                    logFileUpload(false, "Error: $msg")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val msg = e.message ?: "Unknown Error!"
                Log.i("processUpload", "msg: $msg")
                logFileUpload(false, "Exception: $msg")
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onStop() {
        Log.d("Upload[onStop]", "1 - ON STOP")
        super.onStop()
        if (::player.isInitialized) {
            Log.d("Upload[onStop]", "player.isPlaying: ${player.isPlaying}")
            if (player.isPlaying) {
                Log.d("Upload[onStop]", "player.pause")
                player.pause()
            }
        }
    }
}

fun logFileUpload(status: Boolean = true, message: String? = null, multiple: Boolean = false) {
    val event = if (status) "upload_success" else "upload_failed"
    val params = Bundle().apply {
        message?.let { putString("message", it) }
        if (multiple == true) {
            putString("multiple", "true")
        }
    }
    Log.i("Firebase", "logEvent: $event - $params")
    Firebase.analytics.logEvent(event, params)
}

fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var fileName: String? = null
    context.contentResolver.query(uri, null, null, null, null).use { cursor ->
        if (cursor != null && cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                fileName = cursor.getString(nameIndex)
            }
        }
    }
    return fileName
}

//fun openUrl(context: Context, url: String) {
//    val openIntent = Intent(Intent.ACTION_VIEW).apply {
//        setDataAndType(url.toUri(), "text/plain")
//        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//    }
//    context.startActivity(Intent.createChooser(openIntent, null))
//}

//fun shareUrl(context: Context, url: String) {
//    val shareIntent = Intent(Intent.ACTION_SEND).apply {
//        type = "text/plain"
//        putExtra(Intent.EXTRA_TEXT, url)
//    }
//    context.startActivity(Intent.createChooser(shareIntent, null))
//}

fun isGlideMime(mimeType: String): Boolean {
    return when (mimeType.lowercase()) {
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp",
        "image/heif",
            -> true

        else -> false
    }
}

fun isCodeMime(mimeType: String): Boolean {
    if (mimeType.startsWith("text/x-script")) return true
    return when (mimeType.lowercase()) {
        "application/atom+xml",
        "application/javascript",
        "application/json",
        "application/ld+json",
        "application/rss+xml",
        "application/xml",
        "application/x-httpd-php",
        "application/x-python",
        "application/x-www-form-urlencoded",
        "application/yaml",
        "text/javascript",
        "text/python",
        "text/x-go",
        "text/x-ruby",
        "text/x-php",
        "text/x-python",
        "text/x-shellscript",
            -> true

        else -> false
    }
}

fun getGenericIcon(mimeType: String): Int = when {
    isCodeMime(mimeType) -> R.drawable.md_code_blocks_24px
    mimeType.startsWith("application/json") -> R.drawable.md_file_json_24px
    mimeType.startsWith("application/pdf") -> R.drawable.md_picture_as_pdf_24px
    mimeType.startsWith("image/gif") -> R.drawable.md_gif_box_24px
    mimeType.startsWith("image/png") -> R.drawable.md_file_png_24px
    mimeType.startsWith("text/csv") -> R.drawable.md_csv_24px
    mimeType.startsWith("audio/") -> R.drawable.md_music_note_24px
    mimeType.startsWith("image/") -> R.drawable.md_imagesmode_24px
    mimeType.startsWith("text/") -> R.drawable.md_docs_24px
    mimeType.startsWith("video/") -> R.drawable.md_videocam_24px
    else -> R.drawable.md_unknown_document_24px
}
