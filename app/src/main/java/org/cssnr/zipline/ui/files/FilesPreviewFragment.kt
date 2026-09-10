package org.cssnr.zipline.ui.files

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.OptIn
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.transition.TransitionInflater
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.cssnr.zipline.MediaCache
import org.cssnr.zipline.R
import org.cssnr.zipline.api.ServerApi
import org.cssnr.zipline.api.ServerApi.FileEditRequest
import org.cssnr.zipline.api.ServerApi.FileResponse
import org.cssnr.zipline.databinding.FragmentFilesPreviewBinding
import org.cssnr.zipline.ui.showSnackbar
import org.cssnr.zipline.ui.upload.copyToClipboard
import org.json.JSONObject
import java.io.File

class FilesPreviewFragment : Fragment() {

    private var _binding: FragmentFilesPreviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FilesViewModel by activityViewModels()

    private var isPlaying: Boolean? = null
    private var currentPosition: Long = 0

    private lateinit var player: ExoPlayer
    private var webView: WebView? = null

    private val navController by lazy { findNavController() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("FilesPre[onCreate]", "savedInstanceState: ${savedInstanceState?.size()}")
        sharedElementEnterTransition =
            TransitionInflater.from(requireContext()).inflateTransition(android.R.transition.move)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("FilesPreviewFragment", "onCreateView: ${savedInstanceState?.size()}")
        _binding = FragmentFilesPreviewBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }

    override fun onDestroyView() {
        Log.d("FilesPreviewFragment", "onDestroyView")
        if (::player.isInitialized) {
            Log.d("FilesPreviewFragment", "player.release")
            player.release()
        }
        if (webView != null) {
            Log.d("FilesPreviewFragment", "webView?.destroy")
            webView?.destroy()
            webView = null
        }
        super.onDestroyView()
        _binding = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    @OptIn(UnstableApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("FilesPreviewFragment", "onViewCreated: ${savedInstanceState?.size()}")

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            Log.d("ViewCompat", "top: ${bars.top}")
            v.updatePadding(top = bars.top)
            insets
        }

        val ctx = requireContext()

        val preferences = PreferenceManager.getDefaultSharedPreferences(ctx)
        val autoPlay = preferences.getBoolean("file_preview_autoplay", true)
        Log.d("FilesPreviewFragment", "autoPlay: $autoPlay")
        val savedUrl = preferences.getString("ziplineUrl", null)
        Log.d("FilesPreviewFragment", "savedUrl: $savedUrl")

        // ViewModel Data
        viewModel.activeFile.observe(viewLifecycleOwner) { file ->
            Log.d("activeFile.observe", "file: $file")
            if (file == null) return@observe
            binding.fileName.text = file.originalName ?: file.name
        }

        // Static Data
        // TODO: Currently this uses a mix of these val's and viewModel.activeFile.value data...
        val activeFile = viewModel.activeFile.value
        Log.d("FilesPreviewFragment", "viewModel.activeFile.value: $activeFile")
        if (activeFile == null) {
            Log.w("FilesPreviewFragment", "activeFile is null, navigating up")
            navController.navigateUp()
            return
        }
        val mimeType = activeFile.type
        val rawUrl = viewModel.getRawUrl(activeFile)

        binding.goBack.setOnClickListener {
            Log.d("FilesPreviewFragment", "GO BACK")
            navController.navigateUp()
        }

        binding.menuButton.setOnClickListener { anchor ->
            Log.d("FilesPreviewFragment", "MENU BUTTON")
            val file = viewModel.activeFile.value ?: return@setOnClickListener
            val fileViewUrl = viewModel.getViewUrl(file)
            val popupMenu = PopupMenu(requireContext(), anchor)
            popupMenu.menuInflater.inflate(R.menu.preview_menu, popupMenu.menu)
            popupMenu.menu.findItem(R.id.preview_favorite).isChecked = file.favorite
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.preview_share -> {
                        ctx.shareUrl(fileViewUrl)
                        true
                    }

                    R.id.preview_copy_url -> {
                        ctx.copyToClipboard(fileViewUrl)
                        ctx.showSnackbar("Copied URL to Clipboard.")
                        true
                    }

                    R.id.preview_download -> {
                        val savedUrl = viewModel.savedUrl ?: return@setOnMenuItemClickListener true
                        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        dm.enqueue(getDownloadRequest(savedUrl, file))
                        ctx.showSnackbar("Download Started")
                        true
                    }

                    R.id.preview_delete -> {
                        previewDelete(file)
                        true
                    }

                    R.id.preview_favorite -> {
                        lifecycleScope.launch {
                            val api = ServerApi(ctx)
                            val editRequest =
                                FileEditRequest(id = file.id, favorite = !file.favorite)
                            val result = api.editSingle(file.id, editRequest)
                            if (result != null) {
                                file.favorite = editRequest.favorite ?: false
                                viewModel.editRequest.value = editRequest
                                val text =
                                    if (result.favorite == true) "Added to" else "Removed from"
                                ctx.showSnackbar("File $text Favorites.")
                            } else {
                                ctx.showSnackbar("Error Changing File Favorite.", true)
                            }
                        }
                        true
                    }

                    R.id.preview_open -> {
                        ctx.openUrl(fileViewUrl)
                        true
                    }

                    else -> false
                }
            }
            popupMenu.show()
        }

        binding.playerView.transitionName = viewModel.activeFile.value?.id

        // Preview Data
        if (viewModel.activeFile.value?.password == true) {
            Log.d("FilesPreviewFragment", "PASSWORD PROTECTED")
            binding.previewImageView.visibility = View.VISIBLE
            binding.previewImageView.setImageResource(R.drawable.md_encrypted_24px)
            binding.previewImageView.setOnClickListener {
                //navController.popBackStack()
                navController.navigateUp()
            }
        } else if (mimeType.startsWith("video/") || mimeType.startsWith("audio/")) {
            Log.d("FilesPreviewFragment", "EXOPLAYER")
            binding.playerView.visibility = View.VISIBLE

            player = ExoPlayer.Builder(requireContext()).build()
            binding.playerView.player = player
            binding.playerView.controllerShowTimeoutMs = 1000
            binding.playerView.setShowNextButton(false)
            binding.playerView.setShowPreviousButton(false)

            //player.addListener(object : Player.Listener {
            //    override fun onIsPlayingChanged(isPlaying: Boolean) {
            //        if (isPlaying) {
            //            binding.playerView.hideController()
            //        } else {
            //            binding.playerView.showController()
            //        }
            //    }
            //})
            if (savedInstanceState != null) {
                isPlaying = savedInstanceState.getBoolean("is_playing", false)
                currentPosition = savedInstanceState.getLong("current_position", 0L)
            }
            Log.d("FilesPreviewFragment", "isPlaying: $isPlaying")
            Log.d("FilesPreviewFragment", "currentPosition: $currentPosition")

            //val mediaSource = ProgressiveMediaSource.Factory(MediaCache.cacheDataSourceFactory)
            //    .createMediaSource(MediaItem.fromUri(rawUrl!!))
            val cookie = CookieManager.getInstance().getCookie(savedUrl)
            Log.d("FilesPreviewFragment", "cookie: $cookie")
            val baseDataSourceFactory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("Cookie" to cookie))
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(MediaCache.simpleCache)
                .setUpstreamDataSourceFactory(baseDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            val mediaSource = ProgressiveMediaSource.Factory(cacheDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(rawUrl))

            player.setMediaSource(mediaSource)
            player.prepare()
            player.seekTo(currentPosition)
            if (isPlaying ?: autoPlay) {
                Log.d("FilesPreviewFragment", "player.play")
                player.play()
            }

            //player.addListener(
            //    object : Player.Listener {
            //        override fun onIsPlayingChanged(isPlaying: Boolean) {
            //            if (isPlaying) {
            //                // Active playback.
            //            } else {
            //                // Not playing because playback is paused, ended, suppressed, or the player
            //                // is buffering, stopped or failed. Check player.playWhenReady,
            //                // player.playbackState, player.playbackSuppressionReason and
            //                // player.playerError for details.
            //            }
            //        }
            //    }
            //)

        } else if (isGlideMime(mimeType)) {
            Log.d("FilesPreviewFragment", "GLIDE")
            binding.previewImageView.visibility = View.VISIBLE

            //Glide.with(this)
            //    .load(rawUrl)
            //    .into(imageView)

            Log.i("FilesPreviewFragment", "rawUrl: $rawUrl")
            postponeEnterTransition()
            Glide.with(this)
                .load(rawUrl)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        startPostponedEnterTransition()
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        startPostponedEnterTransition()
                        return false
                    }
                })
                .into(binding.previewImageView)
            //binding.previewImageView.setOnClickListener {
            //    Log.d("FilesPreviewFragment", "IMAGE BACK")
            //    //navController.popBackStack()
            //    navController.navigateUp()
            //}

        } else if (mimeType.startsWith("text/") || isCodeMime(mimeType)) {
            Log.d("FilesPreviewFragment", "WEB VIEW TIME")
            binding.copyText.visibility = View.VISIBLE
            binding.previewProgress.visibility = View.VISIBLE

            val url = "file:///android_asset/preview/preview.html"
            Log.d("FilesPreviewFragment", "url: $url")

            //val cookieManager = CookieManager.getInstance()
            //cookieManager.setAcceptCookie(true)
            //cookieManager.setAcceptThirdPartyCookies(webView, true)

            var contentJs: String? = null
            var pageReady = false
            var injected = false

            fun injectContent() {
                val wv = webView ?: return
                if (!pageReady || injected) return
                val js = contentJs ?: return
                injected = true
                Log.d("FilesPreviewFragment", "injecting content")
                wv.evaluateJavascript(js, null)
                binding.previewProgress.visibility = View.GONE
            }

            webView = WebView(ctx).also { wv ->
                binding.previewContainer.addView(wv)
                wv.setBackgroundColor(0)
                binding.previewProgress.bringToFront()
                wv.settings.javaScriptEnabled = true
                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        Log.d("FilesPreviewFragment", "onPageFinished: $url")
                        pageReady = true
                        injectContent()
                        super.onPageFinished(view, url)
                    }

                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: RenderProcessGoneDetail
                    ): Boolean {
                        Log.e(
                            "FilesPreviewFragment",
                            "onRenderProcessGone: didCrash=${detail.didCrash()}"
                        )
                        navController.navigateUp()
                        return true
                    }
                }
                wv.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        Log.d(
                            "FilesPreviewFragment",
                            "${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
                        )
                        return true
                    }
                }
                Log.d("FilesPreviewFragment", "loading: $url")
                wv.loadUrl(url)
            }

            viewLifecycleOwner.lifecycleScope.launch {
                val content = withContext(Dispatchers.IO) { getContent(ctx, rawUrl) }
                if (content == null) {
                    Log.w("FilesPreviewFragment", "content is null")
                    ctx.showSnackbar("Error Loading Content!", true)
                    binding.previewProgress.visibility = View.GONE
                    webView?.visibility = View.GONE
                    binding.previewImageView.visibility = View.VISIBLE
                    binding.previewImageView.setImageResource(getGenericIcon(mimeType))
                    return@launch
                }
                binding.copyText.setOnClickListener {
                    ctx.copyToClipboard(content)
                    ctx.showSnackbar("Copied Text to Clipboard.")
                }
                //Log.d("FilesPreviewFragment", "content: $content")
                val escapedContent = JSONObject.quote(content)
                //Log.d("FilesPreviewFragment", "escapedContent: $escapedContent")
                val jsString = "addContent(${escapedContent});"
                //Log.d("FilesPreviewFragment", "jsString: $jsString")
                contentJs = jsString
                injectContent()
            }
        } else {
            Log.d("FilesPreviewFragment", "OTHER - NO PREVIEW")

            binding.previewImageView.visibility = View.VISIBLE
            binding.previewImageView.setImageResource(getGenericIcon(mimeType))
            binding.previewImageView.setOnClickListener {
                //navController.popBackStack()
                navController.navigateUp()
            }
        }
    }

    private fun previewDelete(data: FileResponse) {
        Log.d("FilesPreviewFragment", "previewDelete: ${data.id}")
        MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogTheme)
            .setTitle("Delete File?")
            .setIcon(R.drawable.md_delete_24px)
            .setMessage("Name: ${data.name}\nOriginal: ${data.originalName}\nID: ${data.id}")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val result = ServerApi(requireContext()).deleteSingle(data.id)
                    viewModel.deleteId.value = data.id
                    val msg = if (result != null) "File Deleted" else "Delete Failed"
                    requireContext().showSnackbar(msg, result == null)
                    navController.navigateUp()
                }
            }
            .show()
    }

    private fun getContent(ctx: Context, rawUrl: String): String? {
        Log.d("getContent", "rawUrl: $rawUrl")
        val cookies = CookieManager.getInstance().getCookie(rawUrl)
        Log.d("getContent", "cookies: $cookies")

        val request = Request.Builder()
            .url(rawUrl)
            .header("Cookie", cookies ?: "")
            .build()
        return try {
            getHttpClient(ctx).newCall(request).execute().use { response ->
                Log.d("getContent", "response.code: ${response.code}")
                if (response.isSuccessful) {
                    return response.body.string()
                }
                null
            }
        } catch (e: Exception) {
            Log.e("getContent", "Exception: ${e.message}")
            null
        }
    }

    //override fun onPause() {
    //    Log.d("Files[onPause]", "0 - ON PAUSE")
    //    super.onPause()
    //    webView.onPause()
    //    webView.pauseTimers()
    //}

    //override fun onResume() {
    //    Log.d("Home[onResume]", "ON RESUME")
    //    super.onResume()
    //    webView.onResume()
    //    webView.resumeTimers()
    //}

    override fun onStop() {
        Log.d("Files[onStop]", "1 - ON STOP")
        if (::player.isInitialized) {
            Log.d("Files[onStop]", "player.isPlaying: ${player.isPlaying}")
            isPlaying = player.isPlaying
            player.pause()
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        Log.d("Files[onSave]", "2 - ON SAVE: outState: ${outState.size()}")
        super.onSaveInstanceState(outState)
        if (::player.isInitialized) {
            Log.d("Files[onSave]", "isPlaying: $isPlaying")
            if (isPlaying != null) {
                outState.putBoolean("is_playing", isPlaying!!)
            }
            Log.d("Files[onSave]", "player.currentPosition: ${player.currentPosition}")
            outState.putLong("current_position", player.currentPosition)
        }
    }

    //override fun onStart() {
    //    Log.d("FilesPreviewFragment", "ON START")
    //    super.onStart()
    //}

    //override fun onResume() {
    //    Log.d("FilesPreviewFragment", "ON RESUME")
    //    super.onResume()
    //}

    companion object {
        private val httpClientLock = Any()
        private var httpClient: OkHttpClient? = null

        private fun getHttpClient(ctx: Context): OkHttpClient {
            synchronized(httpClientLock) {
                if (httpClient == null) {
                    val forceCacheInterceptor = Interceptor { chain ->
                        val response = chain.proceed(chain.request())
                        if (response.isSuccessful) {
                            response.newBuilder()
                                .header("Cache-Control", "public, max-age=604800")
                                .build()
                        } else {
                            response
                        }
                    }
                    // TODO: Make Cache Size User Configurable: 100 MB
                    httpClient = OkHttpClient.Builder()
                        .addNetworkInterceptor(forceCacheInterceptor)
                        .cache(
                            Cache(
                                File(ctx.applicationContext.cacheDir, "http_cache"),
                                100 * 1024 * 1024
                            )
                        )
                        .build()
                }
            }
            return httpClient ?: error("httpClient not initialized")
        }
    }
}
