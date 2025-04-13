package org.cssnr.zipline

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.cssnr.zipline.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @SuppressLint("SetJavaScriptEnabled", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("onCreate", "savedInstanceState: $savedInstanceState")
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val packageInfo = packageManager.getPackageInfo(this.packageName, 0)
        val versionName = packageInfo.versionName
        Log.d("onCreate", "versionName: $versionName")

        val headerView = binding.navigationView.getHeaderView(0)
        val versionTextView = headerView.findViewById<TextView>(R.id.header_version)
        versionTextView.text = "v${versionName}"

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.webView.apply {
            webViewClient = MyWebViewClient()
            settings.domStorageEnabled = true
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.loadWithOverviewMode = true // prevent loading images zoomed in
            settings.useWideViewPort = true // prevent loading images zoomed in
        }

        // Handle Navigation Item Clicks
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            Log.d("Drawer", "menuItem: $menuItem")
            Log.d("Drawer", "itemId: ${menuItem.itemId}")
            if (menuItem.itemId == R.id.nav_item_home) {
                Log.d("Drawer", "GO HOME")
                Toast.makeText(this, "Not Yet Implemented!", Toast.LENGTH_LONG).show()
            } else if (menuItem.itemId == R.id.nav_item_upload) {
                Log.d("Drawer", "UP LOAD")
                Toast.makeText(this, "Not Yet Implemented!", Toast.LENGTH_LONG).show()
            } else if (menuItem.itemId == R.id.nav_item_settings) {
                Log.d("Drawer", "SETTINGS")
                supportFragmentManager.beginTransaction()
                    .replace(R.id.main, SettingsFragment())
                    .addToBackStack(null)
                    .commit()
                binding.drawerLayout.closeDrawers()
                true
            }
            false
        }
        handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        Log.d("onSaveInstanceState", "outState: $outState")
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        Log.d("onRestoreInstanceState", "savedInstanceState: $savedInstanceState")
        super.onRestoreInstanceState(savedInstanceState)
        binding.webView.restoreState(savedInstanceState)
    }

    override fun onPause() {
        Log.d("onPause", "ON PAUSE")
        super.onPause()
        binding.webView.onPause()
        binding.webView.pauseTimers()
    }

    override fun onResume() {
        Log.d("onResume", "ON RESUME")
        super.onResume()
        binding.webView.onResume()
        binding.webView.resumeTimers()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("onNewIntent", "intent: $intent")
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        Log.d("handleIntent", "intent: $intent")

        Log.d("handleIntent", "intent.data: ${intent.data}")
        Log.d("handleIntent", "intent.type: ${intent.type}")
        Log.d("handleIntent", "intent.action: ${intent.action}")

        val sharedPreferences = getSharedPreferences("default_preferences", MODE_PRIVATE)
        val ziplineUrl = sharedPreferences.getString("ziplineUrl", null)
        val ziplineToken = sharedPreferences.getString("ziplineToken", null)
        Log.d("handleIntent", "ziplineUrl: $ziplineUrl")
        Log.d("handleIntent", "ziplineToken: $ziplineToken")

        if (ziplineUrl.isNullOrEmpty() || ziplineToken.isNullOrEmpty()) {
            Log.w("handleIntent", "Missing Zipline URL or Token...")

            //val fragmentTransaction = supportFragmentManager.beginTransaction()
            //val fragment = YourFragment()
            //fragmentTransaction.replace(R.id.fragmentContainer, fragment)
            //fragmentTransaction.commit()

            //binding.webView.loadUrl("about:blank")
            //binding.webView.visibility = View.INVISIBLE

            supportFragmentManager.beginTransaction()
                .replace(R.id.main, SetupFragment())
                .commit()

        } else if (Intent.ACTION_MAIN == intent.action) {
            Log.d("handleIntent", "ACTION_MAIN")

            binding.webView.loadUrl(ziplineUrl)

        } else if (Intent.ACTION_SEND == intent.action) {
            Log.d("handleIntent", "ACTION_SEND")

            val fileUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }

            Log.d("handleIntent", "File URI: $fileUri")
            showPreview(fileUri, intent.type)
        } else if (Intent.ACTION_SEND_MULTIPLE == intent.action) {
            Log.d("handleIntent", "ACTION_SEND_MULTIPLE")

            val fileUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            Log.d("handleIntent", "fileUris: $fileUris")
            if (fileUris == null) {
                Toast.makeText(this, "Error Parsing URI!", Toast.LENGTH_SHORT).show()
                Log.w("handleIntent", "fileUris is null")
                return
            }
            for (fileUri in fileUris) {
                Log.d("handleIntent", "MULTI: fileUri: $fileUri")
            }
            Toast.makeText(this, "Not Yet Implemented!", Toast.LENGTH_SHORT).show()
            Log.w("handleIntent", "NOT IMPLEMENTED")

        } else if (Intent.ACTION_VIEW == intent.action) {
            Log.d("handleIntent", "ACTION_VIEW")

            Log.d("handleIntent", "File URI: ${intent.data}")
            showPreview(intent.data, intent.type)

        } else {
            Toast.makeText(this, "That's a Bug!", Toast.LENGTH_SHORT).show()
            Log.w("handleIntent", "BUG: UNKNOWN intent.action: ${intent.action}")
        }
    }

    private fun showPreview(uri: Uri?, type: String?) {
        Log.d("processUpload", "File URI: $uri")
        val fragment = PreviewFragment()
        val bundle = Bundle().apply {
            putString("uri", uri.toString())
            putString("type", type)
        }
        fragment.arguments = bundle
        supportFragmentManager.beginTransaction()
            .replace(R.id.main, fragment)
            .commit()
    }

    fun loadUrl(url: String) {
        Log.d("loadUrl", "binding.webView.loadUrl: $url")
        binding.webView.loadUrl(url)
    }

    inner class MyWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            Log.d("shouldOverrideUrlLoading", "url: $url")

            val preferences = getSharedPreferences("default_preferences", MODE_PRIVATE)
            val ziplineUrl = preferences.getString("ziplineUrl", null)
            Log.d("shouldOverrideUrlLoading", "ziplineUrl: $ziplineUrl")

            if (ziplineUrl.isNullOrEmpty()) {
                Log.w("shouldOverrideUrlLoading", "ziplineUrl.isNullOrEmpty()")
                Log.d("shouldOverrideUrlLoading", "TRUE - in browser")
                return true
            }

            if (url.startsWith(ziplineUrl)) {
                Log.d("shouldOverrideUrlLoading", "FALSE - in app")
                return false
            }

            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            view.context.startActivity(intent)
            Log.d("shouldOverrideUrlLoading", "TRUE - in browser")
            return true
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

                supportFragmentManager.beginTransaction()
                    .replace(R.id.main, SetupFragment())
                    .commit()
            }
        }

        //override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        //    Log.d("onPageStarted", "url: $url")
        //}

        //override fun onLoadResource(view: WebView?, url: String?) {
        //    //Log.d("onLoadResource", "url: $url")
        //    if (url?.endsWith("/api/auth/logout") == true) {
        //        Log.d("onLoadResource", "LOGOUT: url: $url")
        //    }
        //}

        //override fun onPageFinished(view: WebView?, url: String?) {
        //    Log.d("onPageFinished", "url: $url")
        //    Log.d("onPageFinished", "view?.url: ${view?.url}")
        //    //view?.loadUrl("")
        //}

    }
}
