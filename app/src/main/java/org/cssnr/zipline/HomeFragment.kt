package org.cssnr.zipline

import android.annotation.SuppressLint
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import org.cssnr.zipline.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var webViewState: Bundle = Bundle()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("HomeFragment", "onCreateView: $savedInstanceState")
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }

    override fun onDestroyView() {
        Log.d("HomeFragment", "onDestroyView")
        super.onDestroyView()
        _binding = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("HomeFragment", "onViewCreated: $savedInstanceState")

        Log.d("onViewCreated", "webViewState.size: ${webViewState.size()}")
        // TODO: Not sure when this method is triggered...
        if (savedInstanceState != null) {
            Log.i("onViewCreated", "SETTING webViewState FROM savedInstanceState")
            webViewState =
                savedInstanceState.getBundle("webViewState") ?: Bundle()  // Ensure non-null
        }
        Log.d("onViewCreated", "webViewState.size: ${webViewState.size()}")

        val sharedPreferences = context?.getSharedPreferences("default_preferences", MODE_PRIVATE)
        val ziplineUrl = sharedPreferences?.getString("ziplineUrl", null)
        val ziplineToken = sharedPreferences?.getString("ziplineToken", null)
        Log.d("onViewCreated", "ziplineUrl: $ziplineUrl")
        Log.d("onViewCreated", "ziplineToken: $ziplineToken")

        binding.webView.apply {
            webViewClient = MyWebViewClient()
            settings.domStorageEnabled = true
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.loadWithOverviewMode = true // prevent loading images zoomed in
            settings.useWideViewPort = true // prevent loading images zoomed in

            if (webViewState.size() > 0) {
                Log.d("webView.apply", "RESTORE STATE")
                restoreState(webViewState)
            } else {
                Log.d("webView.apply", "LOAD URL RETARD")
                loadUrl(ziplineUrl.toString())
            }
        }

//        //val webView = view.findViewById<WebView>(R.id.web_view)
//        //webView.loadUrl(ziplineUrl.toString())
//        //binding.webView.loadUrl(ziplineUrl.toString())
//        if (savedInstanceState != null) {
//            Log.d("onViewCreated", "restoreState: $savedInstanceState")
//            binding.webView.restoreState(savedInstanceState)
//        } else {
//            Log.d("onViewCreated", "webView.loadUrl: $ziplineUrl")
//            binding.webView.loadUrl(ziplineUrl.toString())
//        }
    }

    fun loadUrl(url: String) {
        Log.d("HomeFragment", "binding.webView.loadUrl: $url")
        binding.webView.loadUrl(url)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        Log.d("HomeFragment", "onSaveInstanceState: $outState")
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)

        // TODO: Not sure when this method is triggered...
        webViewState.let {
            outState.putBundle("webViewState", it)
        }
    }

    override fun onPause() {
        Log.d("HomeFragment", "ON PAUSE")
        super.onPause()
        binding.webView.onPause()
        binding.webView.pauseTimers()

        Log.d("onPause", "webViewState.size: ${webViewState.size()}")
        binding.webView.saveState(webViewState)
        Log.d("onPause", "webViewState.size: ${webViewState.size()}")
    }

    override fun onResume() {
        Log.d("HomeFragment", "ON RESUME")
        super.onResume()
        binding.webView.onResume()
        binding.webView.resumeTimers()
    }

    inner class MyWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            Log.d("shouldOverrideUrlLoading", "url: $url")

            //val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            //val savedUrl = sharedPreferences.getString(URL_KEY, null)
            //Log.d("shouldOverrideUrlLoading", "savedUrl: $savedUrl")

            //if ((savedUrl != null &&
            //            url.startsWith(savedUrl) && !url.startsWith("$savedUrl/r/") && !url.startsWith(
            //        "$savedUrl/raw/"
            //    )) ||
            //    url.startsWith("https://discord.com/oauth2") ||
            //    url.startsWith("https://github.com/sessions/two-factor/") ||
            //    url.startsWith("https://github.com/login") ||
            //    url.startsWith("https://accounts.google.com/v3/signin") ||
            //    url.startsWith("https://accounts.google.com/o/oauth2/v2/auth")
            //) {
            //    Log.d("shouldOverrideUrlLoading", "FALSE - in app")
            //    return false
            //}

            //val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            //view.context.startActivity(intent)
            //Log.d("shouldOverrideUrlLoading", "TRUE - in browser")
            //return true

            return false
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            Log.d("doUpdateVisitedHistory", "url: $url")
            if (url.endsWith("/auth/login") == true) {
                Log.d("doUpdateVisitedHistory", "LOGOUT: url: $url")

                val sharedPreferences =
                    view.context.getSharedPreferences("default_preferences", MODE_PRIVATE)
                //sharedPreferences.edit { putString("ziplineToken", "") }
                sharedPreferences.edit { remove("ziplineToken") }
                Log.d("doUpdateVisitedHistory", "REMOVE: ziplineToken")

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
}
