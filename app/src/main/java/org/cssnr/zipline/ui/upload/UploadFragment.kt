package org.cssnr.zipline.ui.upload

import UploadOptions
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
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
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cssnr.zipline.R
import org.cssnr.zipline.api.ServerApi
import org.cssnr.zipline.api.parseErrorBody
import org.cssnr.zipline.databinding.FragmentUploadBinding
import org.cssnr.zipline.ui.dialogs.FolderFragment
import org.cssnr.zipline.ui.dialogs.UploadOptionsDialog
import org.json.JSONObject

class UploadFragment : Fragment() {

    private var _binding: FragmentUploadBinding? = null
    private val binding get() = _binding!!

    // TODO: This does not survive device rotation...
    private val viewModel: UploadViewModel by activityViewModels()

    private val navController by lazy { findNavController() }
    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }

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

    override fun onStart() {
        super.onStart()
        Log.d("Upload[onStart]", "onStart - Hide UI")
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav).visibility = View.GONE
    }

    override fun onStop() {
        Log.d("Upload[onStop]", "1 - ON STOP")
        if (::player.isInitialized) {
            Log.d("Upload[onStop]", "player.isPlaying: ${player.isPlaying}")
            if (player.isPlaying) {
                Log.d("Upload[onStop]", "player.pause")
                player.pause()
            }
        }
        Log.d("Upload[onStop]", "onStop - Show UI")
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav).visibility =
            View.VISIBLE
        super.onStop()
    }

    @SuppressLint("SetJavaScriptEnabled")
    @OptIn(UnstableApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("Upload[onViewCreated]", "savedInstanceState: $savedInstanceState")
        Log.d("Upload[onViewCreated]", "arguments: $arguments")

        if (arguments?.getBoolean("optionsCleared") != true) {
            Log.i("Upload[onViewCreated]", "New Upload - null viewModel.uploadOptions")
            viewModel.uploadOptions.value = null
            arguments?.putBoolean("optionsCleared", true)
        }

        val ctx = requireContext()

        val savedUrl = preferences.getString("ziplineUrl", null)
        Log.d("Upload[onViewCreated]", "savedUrl: $savedUrl")
        val authToken = preferences.getString("ziplineToken", null)
        Log.d("Upload[onViewCreated]", "authToken: ${authToken?.take(24)}...")
        if (savedUrl.isNullOrEmpty() || authToken.isNullOrEmpty()) {
            Log.e("Upload[onViewCreated]", "savedUrl is null")
            Toast.makeText(ctx, "Missing URL!", Toast.LENGTH_LONG).show()
            navController.navigate(
                R.id.nav_item_login, null, NavOptions.Builder()
                    .setPopUpTo(navController.graph.id, true)
                    .build()
            )
            return
        }

        val uri = requireArguments().getString("uri")?.toUri()
        Log.d("Upload[onViewCreated]", "uri: $uri")

        if (uri == null) {
            // TODO: Better Handle this Error
            Log.e("Upload[onViewCreated]", "URI is null")
            Toast.makeText(ctx, "No URI to Process!", Toast.LENGTH_LONG).show()
            return
        }

        if (viewModel.uploadOptions.value == null) {
            viewModel.uploadOptions.value = UploadOptions()
            val fileFolderId = preferences.getString("file_folder_id", null)
            viewModel.uploadOptions.value?.folderId = fileFolderId
            Log.i("Upload[onViewCreated]", "uploadOptions: ${viewModel.uploadOptions.value}")
        }

        val mimeType = ctx.contentResolver.getType(uri)
        Log.d("Upload[onViewCreated]", "mimeType: $mimeType")

        val fileName = getFileNameFromUri(ctx, uri)
        Log.d("Upload[onViewCreated]", "fileName: $fileName")
        binding.fileName.setText(fileName)

        if (mimeType?.startsWith("video/") == true || mimeType?.startsWith("audio/") == true) {
            Log.d("Upload[onViewCreated]", "EXOPLAYER")
            binding.playerView.visibility = View.VISIBLE

            player = ExoPlayer.Builder(ctx).build()
            binding.playerView.player = player
            binding.playerView.controllerShowTimeoutMs = 1000
            binding.playerView.setShowNextButton(false)
            binding.playerView.setShowPreviousButton(false)
            val dataSourceFactory = DefaultDataSource.Factory(ctx)
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri))
            player.setMediaSource(mediaSource)
            player.prepare()

        } else if (isGlideMime(mimeType.toString())) {
            Log.d("Upload[onViewCreated]", "GLIDE")
            binding.imageHolder.visibility = View.VISIBLE

            // TODO: Determine how to deal with caching...
            // .diskCacheStrategy(DiskCacheStrategy.NONE)
            Glide.with(binding.imagePreview).load(uri).into(binding.imagePreview)

        } else if (mimeType?.startsWith("text/") == true || isCodeMime(mimeType!!)) {
            Log.d("Upload[onViewCreated]", "WEBVIEW")
            webView = WebView(ctx)
            binding.contentLayout.addView(webView)

            val url = "file:///android_asset/preview/preview.html"
            Log.d("Upload[onViewCreated]", "url: $url")

            val content = ctx.contentResolver.openInputStream(uri)?.bufferedReader()
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
                    @Suppress("unused")
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
            binding.imagePreview.setImageResource(getGenericIcon(mimeType))
        }

        // Upload Options Button
        binding.uploadOptions.setOnClickListener {
            setFragmentResultListener("upload_options_result") { _, bundle ->
                Log.i("folderButton", "bundle: $bundle")
                val filePassword = bundle.getString("filePassword")
                val deletesAt = bundle.getString("deletesAt")
                val maxViews = bundle.getInt("maxViews")
                Log.d("folderButton", "filePassword: $filePassword")
                Log.d("folderButton", "deletesAt: $deletesAt")
                Log.d("folderButton", "maxViews: $maxViews")
                viewModel.uploadOptions.value?.password = filePassword
                viewModel.uploadOptions.value?.deletesAt = deletesAt
                viewModel.uploadOptions.value?.maxViews = if (maxViews == 0) null else maxViews
            }
//            val uploadOptionsDialog = UploadOptionsDialog()
//            uploadOptionsDialog.setData(uploadOptions)
//            uploadOptionsDialog.show(parentFragmentManager, "UploadOptions")
            val uploadOptionsDialog =
                UploadOptionsDialog.newInstance(viewModel.uploadOptions.value!!)
            uploadOptionsDialog.show(parentFragmentManager, "UploadOptions")
        }

        // Options Button
        binding.optionsButton.setOnClickListener {
            Log.d("optionsButton", "setOnClickListener")
            navController.navigate(R.id.nav_item_settings, bundleOf("hide_bottom_nav" to true))
        }

        // Folder Button
        binding.folderButton.setOnClickListener {
            Log.d("folderButton", "setOnClickListener")
            setFragmentResultListener("folder_fragment_result") { _, bundle ->
                val folderId = bundle.getString("folderId")
                val folderName = bundle.getString("folderName")
                Log.d("folderButton", "folderId: $folderId")
                Log.d("folderButton", "folderName: $folderName")
                viewModel.uploadOptions.value?.folderId = folderId ?: ""
            }

            Log.i("folderButton", "fileFolderId: ${viewModel.uploadOptions.value?.folderId}")

            lifecycleScope.launch {
                val folderFragment = FolderFragment()
                // NOTE: Not setting uploadOptions here. DUPLICATION: upload, uploadMulti, text
                folderFragment.setFolderData(ctx, viewModel.uploadOptions.value?.folderId)
                folderFragment.show(parentFragmentManager, "FolderFragment")
            }
        }

        //// Open Button
        //binding.openButton.setOnClickListener {
        //    Log.d("openButton", "setOnClickListener")
        //    val openIntent = Intent(Intent.ACTION_VIEW).apply {
        //        setDataAndType(uri, mimeType)
        //        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        //    }
        //    startActivity(Intent.createChooser(openIntent, null))
        //}

        // Upload Button
        binding.uploadButton.setOnClickListener {
            val uploadFileName = binding.fileName.text.toString().trim()
            Log.d("uploadButton", "uploadFileName: $uploadFileName")
            ctx.processUpload(uri, uploadFileName) // NOTE: This is only called here...
        }
    }

    // TODO: NOTE: This was duplicated but is being cleaned up...
    private fun Context.processUpload(fileUri: Uri, inputFileName: String?) {
        Log.d("processUpload", "fileUri: $fileUri")
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val savedUrl = preferences.getString("ziplineUrl", null)
        Log.d("processUpload", "savedUrl: $savedUrl")
        val authToken = preferences.getString("ziplineToken", null)
        Log.d("processUpload", "authToken: ${authToken?.take(24)}...")
        val shareUrl = preferences.getBoolean("share_after_upload", true)
        Log.d("processUpload", "shareUrl: $shareUrl")

        if (savedUrl == null || authToken == null) {
            // TODO: Show settings dialog here...
            Log.w("processUpload", "Missing OR savedUrl/authToken")
            Toast.makeText(this, getString(R.string.tst_no_url), Toast.LENGTH_SHORT).show()
            logFileUpload(false, "URL or Token is null")
            return
        }
        val fileName = inputFileName ?: getFileNameFromUri(this, fileUri)
        Log.d("processUpload", "fileName: $fileName")
        if (fileName == null) {
            Log.w("processUpload", "Unable to parse fileName from URI")
            Toast.makeText(this, "Unable to Parse File Name", Toast.LENGTH_SHORT).show()
            logFileUpload(false, "File Name is null")
            return
        }
        //val contentType = URLConnection.guessContentTypeFromName(fileName)
        //Log.d("processUpload", "contentType: $contentType")
        val inputStream = contentResolver.openInputStream(fileUri)
        if (inputStream == null) {
            Log.w("processUpload", "inputStream is null")
            val msg = getString(R.string.tst_upload_error)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            logFileUpload(false, "Input Stream is null")
            return
        }
        val api = ServerApi(this)
        Log.d("processUpload", "api: $api")
        Toast.makeText(this, getString(R.string.tst_uploading_file), Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val response = api.upload(fileName, inputStream, viewModel.uploadOptions.value!!)
                Log.d("processUpload", "response: $response")
                if (response.isSuccessful) {
                    val uploadResponse = response.body() ?: throw Error("Empty Server Response")
                    Log.d("processUpload", "uploadResponse: $uploadResponse")
                    logFileUpload()
                    val url = uploadResponse.files.first().url
                    Log.d("processUpload", "url: $url")
                    withContext(Dispatchers.Main) {
                        copyToClipboard(url)
                        if (shareUrl) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            }
                            startActivity(Intent.createChooser(shareIntent, null))
                        }
                        val bundle = bundleOf("url" to "${savedUrl}/dashboard/files/")
                        navController.navigate(
                            R.id.nav_item_home, bundle, NavOptions.Builder()
                                .setPopUpTo(navController.graph.id, true)
                                .build()
                        )
                    }
                } else {
                    val errorResponse = response.parseErrorBody(this@processUpload)
                    Log.i("processCode", "errorResponse - $errorResponse")
                    val message = errorResponse ?: "Unknown Error: ${response.code()}"
                    Log.i("processCode", "message - $message")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@processUpload, message, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                val msg = e.message ?: "Unknown Error"
                Log.i("processUpload", "msg: $msg")
                logFileUpload(false, "Exception: $msg")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@processUpload, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

fun logFileUpload(status: Boolean = true, message: String? = null, multiple: Boolean = false) {
    val event = if (status) "upload_success" else "upload_failed"
    val params = Bundle().apply {
        message?.let { putString("message", it) }
        if (multiple) {
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

fun Context.copyToClipboard(url: String) {
    // TODO: Refactor this function and use Snackbar instead of Toast
    Log.d("copyToClipboard", "url: $url")
    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("URL", url)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(this, "Copied URL to Clipboard.", Toast.LENGTH_SHORT).show()
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
