package org.cssnr.zipline.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import org.cssnr.zipline.R
import org.cssnr.zipline.databinding.FragmentHomeBinding
import org.cssnr.zipline.ui.setup.showTapTargets

class HomeFragment : Fragment() {

    //var currentUrl: String = ""

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var webViewState: Bundle = Bundle()
    private lateinit var ziplineUrl: String

    private val viewModel: HomeViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("Home[onCreateView]", "savedInstanceState: ${savedInstanceState?.size()}")
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }

    override fun onDestroyView() {
        Log.d("Home[onDestroyView]", "webView.destroy()")
        binding.webView.apply {
            loadUrl("about:blank")
            stopLoading()
            clearHistory()
            removeAllViews()
            destroy()
        }
        super.onDestroyView()
        _binding = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("Home[onViewCreated]", "savedInstanceState: ${savedInstanceState?.size()}")
        Log.d("Home[onViewCreated]", "webViewState: ${webViewState.size()}")
        // TODO: Not sure when this method is triggered...
        if (savedInstanceState != null) {
            Log.i("Home[onViewCreated]", "SETTING webViewState FROM savedInstanceState")
            webViewState =
                savedInstanceState.getBundle("webViewState") ?: Bundle()  // Ensure non-null
            Log.d("Home[onViewCreated]", "webViewState: ${webViewState.size()}")
        }

        val ctx = requireContext()

        //binding.toggleMenu.setOnClickListener {
        //    Log.i("Home[onViewCreated]", "toggleMenu.setOnClickListener")
        //    (requireActivity() as MainActivity).toggleDrawer()
        //}

        val preferences = PreferenceManager.getDefaultSharedPreferences(ctx)
        ziplineUrl = preferences.getString("ziplineUrl", "").toString()
        Log.d("Home[onViewCreated]", "ziplineUrl: $ziplineUrl")
        //val ziplineToken = preferences.getString("ziplineToken", null)
        //Log.d("Home[onViewCreated]", "ziplineToken: $ziplineToken")

        if (arguments?.getBoolean("isFirstRun", false) == true) {
            Log.i("onStart", "FIRST RUN ARGUMENT DETECTED")
            arguments?.remove("isFirstRun")
            requireActivity().showTapTargets(view)
        }

        val versionName = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        val userAgent =
            "${binding.webView.settings.userAgentString} ${ctx.getString(R.string.app_name)}/${versionName}"
        Log.d("Home[onViewCreated]", "UA: $userAgent")

        val headerPreferences =
            ctx.getSharedPreferences("org.cssnr.zipline_custom_headers", Context.MODE_PRIVATE)
        val customHeaders = headerPreferences.all.mapValues { it.value.toString() }
        Log.i("Home[webView]", "customHeaders: $customHeaders")

        val url = arguments?.getString("url")
        Log.d("Home[onViewCreated]", "arguments: url: $url")

        binding.webView.apply {
            Log.i("Home[webView]", "binding.webView.apply")
            webViewClient = MyWebViewClient()
            //webViewClient = MyWebViewClient(
            //    onPageLoaded = {
            //        if (!url.isNullOrEmpty()) {
            //            arguments?.remove("url")
            //            Log.i("Home[webView]", "Zipline Retarded")
            //            binding.webView.evaluateJavascript("console.log('Zipline Retarded');", null)
            //            val js = "document.querySelector('.mantine-Card-root').click();"
            //            binding.webView.evaluateJavascript(js, null)
            //        }
            //    }
            //)
            webChromeClient = MyWebChromeClient()
            settings.domStorageEnabled = true
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.loadWithOverviewMode = true // prevent loading images zoomed in
            settings.useWideViewPort = true // prevent loading images zoomed in

            settings.userAgentString = userAgent

            if (url != null) {
                Log.i("Home[webView]", "ARGUMENT URL: $url")
                arguments?.remove("url")
                loadUrl(url, customHeaders)
            } else if (webViewState.size() > 0) {
                Log.i("Home[webView]", "RESTORE STATE")
                restoreState(webViewState)
            } else if (ziplineUrl.isNotBlank()) {
                Log.i("Home[webView]", "LOAD ziplineUrl: $ziplineUrl")
                loadUrl(ziplineUrl, customHeaders)
            } else {
                Log.i("Home[webView]", "NO ZIPLINE URL - DOING NOTHING")
            }
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    Log.d("Home[OnBackPressedCallback]", "binding.webView.goBack")
                    binding.webView.goBack()
                } else {
                    Log.d("Home[OnBackPressedCallback]", "onBackPressedDispatcher.onBackPressed")
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()

                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        //binding.toggleMenu.apply { animate().alpha(1f).setDuration(1500).start() }
        viewModel.urlToLoad.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { url ->
                Log.i("Home[viewModel]", "TO THE MOON BABY: $url")
                binding.webView.loadUrl(url, customHeaders)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle("webViewState", webViewState)
        Log.d("Home[onSave]", "ON SAVE")
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        Log.d("Home[onPause]", "cookieManager.flush()")
        val cookieManager = CookieManager.getInstance()
        cookieManager.flush()

        Log.d("Home[onPause]", "webView. onPause() / pauseTimers()")
        Log.d("Home[onPause]", "webViewState: ${webViewState.size()}")
        _binding?.webView?.saveState(webViewState)
        _binding?.webView?.onPause()
        _binding?.webView?.pauseTimers()

        Log.d("Home[onPause]", "ON PAUSE")
        super.onPause()
    }

    override fun onResume() {
        Log.i("Home[onResume]", "ON RESUME")
        super.onResume()
        Log.d("Home[onResume]", "webView. onResume() / resumeTimers()")
        binding.webView.onResume()
        binding.webView.resumeTimers()
    }

    inner class MyWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            Log.d("shouldOverrideUrl", "url: $url")
            Log.d("shouldOverrideUrl", "ziplineUrl: $ziplineUrl")

            if (ziplineUrl.isNotEmpty() && url.startsWith(ziplineUrl)) {
                Log.d("shouldOverrideUrl", "FALSE - in app")
                return false
            }

            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            view.context.startActivity(intent)
            Log.d("shouldOverrideUrl", "TRUE - in browser")
            return true
        }

        //override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        //    Log.d("doUpdateVisitedHistory", "url: $url")
        //    //currentUrl = url
        //    if (url.endsWith("/auth/login")) {
        //        Log.d("doUpdateVisitedHistory", "LOGOUT: $url")
        //
        //        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        //        Log.d("doUpdateVisitedHistory", "REMOVE: ziplineToken")
        //        //preferences.edit { putString("ziplineToken", "") }
        //        preferences.edit { remove("ziplineToken") }
        //
        //        Log.d("doUpdateVisitedHistory", "view.loadUrl: about:blank")
        //        view.loadUrl("about:blank")
        //        //view.destroy()
        //        val bundle = bundleOf("url" to ziplineUrl)
        //        Log.d("doUpdateVisitedHistory", "bundle: $bundle")
        //        val navController = findNavController()
        //        navController.navigate(
        //            R.id.nav_item_login, bundle, NavOptions.Builder()
        //                .setPopUpTo(navController.graph.id, true)
        //                .build()
        //        )
        //    }
        //}

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceError
        ) {
            Log.d("onReceivedError", "ERROR: ${errorResponse.errorCode}")
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            Log.d("onReceivedHttpError", "HTTP ERROR: ${errorResponse.statusCode}")
        }

        //private val onPageLoaded: (() -> Unit)? = null
        override fun onPageFinished(view: WebView?, url: String?) {
            Log.d("onPageFinished", "Set: viewModel.webViewUrl.value: $url")
            viewModel.webViewUrl.value = url
            //onPageLoaded?.invoke()
        }
    }

    inner class MyWebChromeClient : WebChromeClient() {
        private var filePathCallback: ValueCallback<Array<Uri>>? = null

        private val fileChooserLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val clipData = result.data?.clipData
                val dataUri = result.data?.data
                val uris = when {
                    clipData != null -> Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                    dataUri != null -> arrayOf(dataUri)
                    else -> null
                }
                Log.d("fileChooserLauncher", "uris: ${uris?.contentToString()}")
                filePathCallback?.onReceiveValue(uris)
                filePathCallback = null
            }

        override fun onShowFileChooser(
            view: WebView,
            callback: ValueCallback<Array<Uri>>,
            params: FileChooserParams
        ): Boolean {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = callback
            return try {
                Log.d("onShowFileChooser", "fileChooserLauncher.launch")
                fileChooserLauncher.launch(params.createIntent())
                true
            } catch (e: Exception) {
                Log.e("onShowFileChooser", "Exception: $e")
                filePathCallback = null
                false
            }
        }
    }
}
