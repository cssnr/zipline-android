package org.cssnr.zipline.ui.files

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.cssnr.zipline.MediaCache
import org.cssnr.zipline.copyToClipboard
import org.cssnr.zipline.databinding.FragmentFilesPreviewBinding
import org.json.JSONObject
import java.io.File


class FilesPreviewFragment : Fragment() {

    private var _binding: FragmentFilesPreviewBinding? = null
    private val binding get() = _binding!!

    private var isPlaying: Boolean? = null
    private var currentPosition: Long = 0

    private lateinit var player: ExoPlayer
    private lateinit var webView: WebView

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
        super.onDestroyView()
        if (::player.isInitialized) {
            Log.d("FilesPreviewFragment", "player.release")
            player.release()
        }
        if (::webView.isInitialized) {
            Log.d("FilesPreviewFragment", "webView.destroy")
            webView.destroy()
        }
        _binding = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    @OptIn(UnstableApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("FilesPreviewFragment", "onViewCreated: ${savedInstanceState?.size()}")

        binding.goBack.setOnClickListener {
            Log.d("FilesPreviewFragment", "GO BACK")
            //findNavController().popBackStack()
            findNavController().navigateUp()
        }

        val fileId = arguments?.getString("fileId")
        Log.d("FilesPreviewFragment", "fileId: $fileId")
        val fileName = arguments?.getString("fileName")
        Log.d("FilesPreviewFragment", "fileName: $fileName")
        val mimeType = arguments?.getString("mimeType")
        Log.d("FilesPreviewFragment", "mimeType: $mimeType")
        val rawUrl = arguments?.getString("rawUrl")
        Log.d("FilesPreviewFragment", "rawUrl: $rawUrl")
        val viewUrl = arguments?.getString("viewUrl")
        Log.d("FilesPreviewFragment", "viewUrl: $viewUrl")

        binding.fileName.text = fileName

        val ctx = requireContext()

        val preferences = PreferenceManager.getDefaultSharedPreferences(ctx)
        val autoPlay = preferences.getBoolean("file_preview_autoplay", true)
        Log.d("FilesPreviewFragment", "autoPlay: $autoPlay")
        val savedUrl = preferences.getString("ziplineUrl", null)
        Log.d("FilesPreviewFragment", "savedUrl: $savedUrl")

        binding.playerView.transitionName = fileId
        //Log.d("FilesPreviewFragment", "transitionName: ${imageView.transitionName}")

        if (mimeType?.startsWith("video/") == true || mimeType?.startsWith("audio/") == true) {
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
                .createMediaSource(MediaItem.fromUri(rawUrl!!))

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

        } else if (isGlideMime(mimeType.toString())) {
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
            binding.previewImageView.setOnClickListener {
                Log.d("FilesPreviewFragment", "IMAGE BACK")
                //findNavController().popBackStack()
                findNavController().navigateUp()
            }

        } else if (mimeType?.startsWith("text/") == true || isCodeMime(mimeType!!)) {
            Log.d("FilesPreviewFragment", "WEB VIEW TIME")
            binding.copyText.visibility = View.VISIBLE
            webView = WebView(ctx)
            binding.previewContainer.addView(webView)

            val url = "file:///android_asset/preview/preview.html"
            Log.d("FilesPreviewFragment", "url: $url")

            //val cookieManager = CookieManager.getInstance()
            //cookieManager.setAcceptCookie(true)
            //cookieManager.setAcceptThirdPartyCookies(webView, true)

            lifecycleScope.launch {
                val content = withContext(Dispatchers.IO) { getContent(rawUrl!!) }
                if (content == null) {
                    Log.w("FilesPreviewFragment", "content is null")
                    withContext(Dispatchers.Main) {
                        val msg = "Error Loading Content!"
                        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                binding.copyText.setOnClickListener {
                    copyToClipboard(ctx, content)
                }
                //Log.d("FilesPreviewFragment", "content: $content")
                val escapedContent = JSONObject.quote(content)
                //Log.d("FilesPreviewFragment", "escapedContent: $escapedContent")
                val jsString = "addContent(${escapedContent});"
                //Log.d("FilesPreviewFragment", "jsString: $jsString")
                withContext(Dispatchers.Main) {
                    webView.apply {
                        settings.javaScriptEnabled = true
                        loadUrl(url)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                evaluateJavascript(jsString, null)
                            }
                        }
                    }
                }
            }

        } else {
            Log.d("FilesPreviewFragment", "OTHER - NO PREVIEW")

            binding.previewImageView.visibility = View.VISIBLE
            binding.previewImageView.setImageResource(getGenericIcon(mimeType.toString()))
            binding.previewImageView.setOnClickListener {
                //findNavController().popBackStack()
                findNavController().navigateUp()
            }
        }
    }

    fun getContent(rawUrl: String): String? {
        Log.d("getContent", "rawUrl: $rawUrl")
        val forceCacheInterceptor = Interceptor { chain ->
            val response = chain.proceed(chain.request())
            response.newBuilder()
                .header("Cache-Control", "public, max-age=31536000")
                .build()
        }

        val cookies = CookieManager.getInstance().getCookie(rawUrl)
        Log.d("getContent", "cookies: $cookies")

        val cacheDirectory = File(requireContext().cacheDir, "http_cache")
        val cache = Cache(cacheDirectory, 100 * 1024 * 1024)

        val client = OkHttpClient.Builder()
            .addNetworkInterceptor(forceCacheInterceptor)
            .cache(cache)
            .build()

        val request = Request.Builder().url(rawUrl).header("Cookie", cookies ?: "").build()
        return try {
            client.newCall(request).execute().use { response ->
                Log.d("getContent", "response.code: ${response.code}")
                if (response.isSuccessful) {
                    return response.body?.string()
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
        super.onStop()
        if (::player.isInitialized) {
            Log.d("Files[onStop]", "player.isPlaying: ${player.isPlaying}")
            isPlaying = player.isPlaying
            player.pause()
        }
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
}
