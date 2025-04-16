package org.cssnr.zipline

import android.annotation.SuppressLint
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import org.cssnr.zipline.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var webViewState: Bundle = Bundle()
    private lateinit var ziplineUrl: String

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

        val sharedPreferences = context?.getSharedPreferences("default_preferences", MODE_PRIVATE)
        ziplineUrl = sharedPreferences?.getString("ziplineUrl", "").toString()
        Log.d("Home[onViewCreated]", "ziplineUrl: $ziplineUrl")
        //val ziplineToken = sharedPreferences?.getString("ziplineToken", null)
        //Log.d("Home[onViewCreated]", "ziplineToken: $ziplineToken")

        val url = arguments?.getString("url")
        Log.d("Home[onViewCreated]", "arguments: url: $url")

        binding.webView.apply {
            webViewClient = MyWebViewClient()
            webChromeClient = MyWebChromeClient()
            settings.domStorageEnabled = true
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.loadWithOverviewMode = true // prevent loading images zoomed in
            settings.useWideViewPort = true // prevent loading images zoomed in

            if (url != null) {
                Log.d("Home[onViewCreated]", "ARGUMENT URL: $url")
                loadUrl(url)
            } else if (webViewState.size() > 0) {
                Log.d("Home[onViewCreated]", "RESTORE STATE")
                restoreState(webViewState)
            } else {
                Log.d("Home[onViewCreated]", "LOAD URL: $ziplineUrl")
                loadUrl(ziplineUrl)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        Log.d("Home[onSave]", "outState: ${outState.size()}")
        super.onSaveInstanceState(outState)
        Log.d("Home[onSave]", "webViewState: ${webViewState.size()}")
        _binding?.webView?.saveState(outState)
        outState.putBundle("webViewState", webViewState)
        Log.d("Home[onSave]", "outState: ${outState.size()}")
    }

    override fun onPause() {
        Log.d("Home[onPause]", "ON PAUSE")
        super.onPause()
        Log.d("Home[onPause]", "webView. onPause() / pauseTimers()")
        binding.webView.onPause()
        binding.webView.pauseTimers()

        Log.d("Home[onPause]", "webViewState: ${webViewState.size()}")
        binding.webView.saveState(webViewState)
        Log.d("Home[onPause]", "webViewState: ${webViewState.size()}")
    }

    override fun onResume() {
        Log.d("Home[onResume]", "ON RESUME")
        super.onResume()
        Log.d("Home[onPause]", "webView. onResume() / resumeTimers()")
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

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            Log.d("doUpdateVisitedHistory", "url: $url")
            if (url.endsWith("/auth/login") == true) {
                Log.d("doUpdateVisitedHistory", "LOGOUT: $url")

                val sharedPreferences =
                    view.context.getSharedPreferences("default_preferences", MODE_PRIVATE)
                Log.d("doUpdateVisitedHistory", "REMOVE: ziplineToken")
                //sharedPreferences.edit { putString("ziplineToken", "") }
                sharedPreferences.edit { remove("ziplineToken") }

                Log.d("doUpdateVisitedHistory", "view.loadUrl: about:blank")
                //view.destroy()
                view.loadUrl("about:blank")

                parentFragmentManager.beginTransaction()
                    .replace(R.id.main, SetupFragment())
                    .commit()
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceError
        ) {
            Log.d("onReceivedError", "ERROR: " + errorResponse.errorCode)
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            Log.d("onReceivedHttpError", "ERROR: " + errorResponse.statusCode)
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
                Log.w("onShowFileChooser", "Exception: $e")
                filePathCallback = null
                false
            }
        }
    }
}
