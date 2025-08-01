package org.cssnr.zipline

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.core.view.size
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.cssnr.zipline.databinding.ActivityMainBinding
import org.cssnr.zipline.ui.home.HomeViewModel
import org.cssnr.zipline.widget.WidgetProvider
import org.cssnr.zipline.work.APP_WORKER_CONSTRAINTS
import org.cssnr.zipline.work.AppWorker
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    internal lateinit var binding: ActivityMainBinding

    private lateinit var navController: NavController
    private lateinit var navHostFragment: NavHostFragment
    private lateinit var filePickerLauncher: ActivityResultLauncher<Array<String>>

    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    companion object {
        const val LOG_TAG = "MainActivity"
    }

    @OptIn(UnstableApi::class)
    @SuppressLint("SetJavaScriptEnabled", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("Main[onCreate]", "savedInstanceState: ${savedInstanceState?.size()}")
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // NavHostFragment
        navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navController = navHostFragment.navController

        // Start Destination
        if (savedInstanceState == null) {
            val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
            val startPreference = preferences.getString("start_destination", null)
            Log.d("Main[onCreate]", "startPreference: $startPreference")
            val startDestination =
                if (startPreference == "files") R.id.nav_item_files else R.id.nav_item_home
            navGraph.setStartDestination(startDestination)
            navController.graph = navGraph
        }

        // Bottom Navigation
        val bottomNav = binding.contentMain.bottomNav
        bottomNav.setupWithNavController(navController)

        // Navigation Drawer
        binding.navView.setupWithNavController(navController)

        // Destinations w/ a Parent Item
        val destinationToBottomNavItem = mapOf(
            R.id.nav_item_file_preview to R.id.nav_item_files,
            R.id.nav_item_settings_widget to R.id.nav_item_settings,
            R.id.nav_item_settings_debug to R.id.nav_item_settings,
        )
        // Destination w/ No Parent
        val hiddenDestinations = setOf(
            R.id.nav_item_upload,
            R.id.nav_item_upload_multi,
            R.id.nav_item_short,
            R.id.nav_item_text,
        )
        // Implement Navigation Hacks Because.......Android?
        navController.addOnDestinationChangedListener { _, destination, _ ->
            Log.d("addOnDestinationChangedListener", "destination: ${destination.label}")
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            val destinationId = destination.id
            if (destinationId in hiddenDestinations) {
                Log.d("addOnDestinationChangedListener", "Set bottomNav to Hidden Item")
                bottomNav.menu.findItem(R.id.nav_wtf).isChecked = true
                return@addOnDestinationChangedListener
            }
            val matchedItem = destinationToBottomNavItem[destinationId]
            if (matchedItem != null) {
                Log.d("addOnDestinationChangedListener", "matched nav item: $matchedItem")
                bottomNav.menu.findItem(matchedItem).isChecked = true
                val menu = binding.navView.menu
                for (i in 0 until menu.size) {
                    val item = menu[i]
                    item.isChecked = item.itemId == matchedItem
                }
            }
        }

        // Handle Custom Navigation Items
        val itemPathMap = mapOf(
            R.id.nav_site_home to "dashboard",
            R.id.nav_site_files to "dashboard/files",
            R.id.nav_site_folders to "dashboard/folders",
            R.id.nav_site_urls to "dashboard/urls",
            R.id.nav_site_metrics to "dashboard/metrics",
            R.id.nav_site_settings to "dashboard/settings",
        )
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            Log.d("setNavigationItemSelectedListener", "menuItem: $menuItem")
            binding.drawerLayout.closeDrawers()
            val path = itemPathMap[menuItem.itemId]
            Log.d("setNavigationItemSelectedListener", "menuItem: $menuItem - path: $path")
            if (path != null) {
                val savedUrl = preferences.getString("ziplineUrl", null)
                Log.d("setNavigationItemSelectedListener", "ziplineUrl: $savedUrl")
                val url = "${savedUrl}/${path}"
                Log.d("setNavigationItemSelectedListener", "Click URL: $url")
                val viewModel: HomeViewModel by viewModels()
                val webViewUrl = viewModel.webViewUrl.value
                Log.d("setNavigationItemSelectedListener", "webViewUrl: $webViewUrl")
                if (webViewUrl != url) {
                    Log.i("Drawer", "WEB VIEW - viewModel.navigateTo: $url")
                    viewModel.navigateTo(url)
                }
                if (navController.currentDestination?.id != R.id.nav_item_home) {
                    Log.d("Drawer", "NAVIGATE: nav_item_home")
                    // NOTE: This is the correct navigation call...
                    val menuItem = binding.navView.menu.findItem(R.id.nav_item_home)
                    NavigationUI.onNavDestinationSelected(menuItem, navController)
                }
                true
            } else if (menuItem.itemId == R.id.nav_item_upload) {
                Log.d("Drawer", "nav_item_upload")
                filePickerLauncher.launch(arrayOf("*/*"))
                true
            } else {
                val handled = NavigationUI.onNavDestinationSelected(menuItem, navController)
                Log.d("Drawer", "ELSE - handled: $handled")
                handled
            }
        }

        // Set Debug Preferences
        Log.d(LOG_TAG, "Set Debug Preferences")
        if (BuildConfig.DEBUG) {
            Log.i(LOG_TAG, "DEBUG BUILD DETECTED!")
            if (!preferences.contains("enable_debug_logs")) {
                Log.i(LOG_TAG, "ENABLING DEBUG LOGGING...")
                preferences.edit {
                    putBoolean("enable_debug_logs", true)
                }
            }
        }

        // Set Default Preferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        PreferenceManager.setDefaultValues(this, R.xml.preferences_widget, false)

        // Update Status Bar
        //window.statusBarColor = Color.TRANSPARENT
        //binding.drawerLayout.setStatusBarBackgroundColor(Color.TRANSPARENT)

        // Set Nav Header Top Padding
        val headerView = binding.navView.getHeaderView(0)
        ViewCompat.setOnApplyWindowInsetsListener(headerView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (bars.top > 0) {
                Log.d("ViewCompat", "getHeaderView: top: ${bars.top}")
                v.updatePadding(top = bars.top)
            }
            insets
        }

        // Update Header Text
        val packageInfo = packageManager.getPackageInfo(this.packageName, 0)
        val versionName = packageInfo.versionName
        Log.d("Main[onCreate]", "versionName: $versionName")
        val versionTextView = headerView.findViewById<TextView>(R.id.header_version)
        versionTextView.text = "v${versionName}"

        // TODO: Improve initialization of the WorkRequest
        val workInterval = preferences.getString("work_interval", null) ?: "0"
        Log.i("Main[onCreate]", "workInterval: $workInterval")
        if (workInterval != "0") {
            val workRequest =
                PeriodicWorkRequestBuilder<AppWorker>(workInterval.toLong(), TimeUnit.MINUTES)
                    .setConstraints(APP_WORKER_CONSTRAINTS)
                    .build()
            Log.i("Main[onCreate]", "workRequest: $workRequest")
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "app_worker",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest,
            )
        } else {
            // TODO: Confirm this is necessary...
            Log.i("Main[onCreate]", "Ensuring Work is Disabled")
            WorkManager.getInstance(this).cancelUniqueWork("app_worker")
        }

        // File Picker for UPLOAD_FILE Intent and Shortcut
        filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                Log.d("filePickerLauncher", "uris: $uris")
                if (uris.size > 1) {
                    Log.i("filePickerLauncher", "MULTI!")
                    showMultiPreview(uris as ArrayList<Uri>)
                } else if (uris.size == 1) {
                    Log.i("filePickerLauncher", "SINGLE!")
                    showPreview(uris[0])
                } else {
                    Log.w("filePickerLauncher", "No Files Selected!")
                    //Toast.makeText(this, "No Files Selected!", Toast.LENGTH_SHORT).show()
                }
            }

        MediaCache.initialize(this)

        // Only Handel Intent Once Here after App Start
        if (savedInstanceState?.getBoolean("intentHandled") != true) {
            onNewIntent(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("intentHandled", true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val action = intent.action
        val data = intent.data
        Log.d("onNewIntent", "${action}: $data")

        // Check Auth First
        val savedUrl = preferences.getString("ziplineUrl", null)
        val authToken = preferences.getString("ziplineToken", null)
        Log.d("onNewIntent", "savedUrl: $savedUrl")
        Log.d("onNewIntent", "authToken: $authToken")

        if (savedUrl.isNullOrEmpty() || authToken.isNullOrEmpty()) {
            Log.w("onNewIntent", "Missing Zipline URL or Token...")
            //val dst = navController.currentDestination?.id ?: navController.graph.startDestinationId
            //Log.w("onNewIntent", "navigate: nav_item_login - desPopUpTo: $dst")
            navController.navigate(
                R.id.nav_item_login, null, NavOptions.Builder()
                    .setPopUpTo(navController.graph.id, true)
                    .build()
            )
            return
        }

        // Reject Calendar URI due to permissions
        val isCalendarUri = data != null &&
                data.authority?.contains("calendar") == true &&
                listOf("/events", "/calendars", "/time").any { data.path?.contains(it) == true }
        Log.d("handleIntent", "isCalendarUri: $isCalendarUri")
        if (isCalendarUri) {
            Log.i("handleIntent", "Calendar Links Not Supported!")
            Toast.makeText(this, "Calendar Links Not Supported!", Toast.LENGTH_LONG).show()
            return
        }

        if (action == Intent.ACTION_MAIN) {
            Log.d("onNewIntent", "ACTION_MAIN")

            binding.drawerLayout.closeDrawers()

            // TODO: Cleanup the logic for handling MAIN intent...
            val currentDestinationId = navController.currentDestination?.id
            Log.d("onNewIntent", "currentDestinationId: $currentDestinationId")
            val fromShortcut = intent.getStringExtra("fromShortcut")
            Log.d("onNewIntent", "fromShortcut: $fromShortcut")

            when (currentDestinationId) {
                R.id.nav_item_upload, R.id.nav_item_upload_multi, R.id.nav_item_short, R.id.nav_item_text -> {
                    Log.i("onNewIntent", "Navigating away from preview page...")
                    navController.navigate(
                        navController.graph.startDestinationId, null, NavOptions.Builder()
                            .setPopUpTo(navController.graph.id, true)
                            .build()
                    )
                }
            }

            // TODO: Determine if this needs to be in the above if/else
            if (fromShortcut == "upload") {
                Log.d("onNewIntent", "filePickerLauncher.launch")
                filePickerLauncher.launch(arrayOf("*/*"))
            }

        } else if (action == Intent.ACTION_SEND) {
            Log.d("onNewIntent", "ACTION_SEND")

            val fileUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            Log.d("onNewIntent", "File URI: $fileUri")

            val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
            Log.d("onNewIntent", "extraText: ${extraText?.take(100)}")

            if (fileUri == null && !extraText.isNullOrEmpty()) {
                Log.d("onNewIntent", "SEND TEXT DETECTED: ${extraText.take(100)}")
                //if (extraText.lowercase().startsWith("http")) {
                //if (Patterns.WEB_URL.matcher(extraText).matches()) {
                if (extraText.toHttpUrlOrNull() == null) {
                    Log.d("onNewIntent", "URL DETECTED: $extraText")
                    binding.drawerLayout.closeDrawers()
                    val bundle = Bundle().apply { putString("url", extraText) }
                    navController.navigate(
                        R.id.nav_item_short, bundle, NavOptions.Builder()
                            .setPopUpTo(navController.graph.id, true)
                            .setLaunchSingleTop(true)
                            .build()
                    )
                } else {
                    Log.i("handleIntent", "PLAIN TEXT DETECTED")
                    val bundle = Bundle().apply { putString("text", extraText) }
                    navController.navigate(
                        R.id.nav_item_text, bundle, NavOptions.Builder()
                            .setPopUpTo(navController.graph.id, true)
                            .setLaunchSingleTop(true)
                            .build()
                    )
                }
            } else {
                showPreview(fileUri)
            }

        } else if (action == Intent.ACTION_SEND_MULTIPLE) {
            Log.d("onNewIntent", "ACTION_SEND_MULTIPLE")

            val fileUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            Log.d("onNewIntent", "fileUris: $fileUris")
            if (fileUris == null) {
                Toast.makeText(this, "Error Parsing URI!", Toast.LENGTH_LONG).show()
                Log.w("onNewIntent", "fileUris is null")
                return
            }
            showMultiPreview(fileUris)

        } else if (action == Intent.ACTION_VIEW) {
            Log.d("onNewIntent", "ACTION_VIEW")

            showPreview(data)

        } else if (action == "UPLOAD_FILE") {
            Log.d("handleIntent", "UPLOAD_FILE")

            filePickerLauncher.launch(arrayOf("*/*"))

        } else {
            Toast.makeText(this, "Unknown Link!", Toast.LENGTH_LONG).show()
            Log.w("onNewIntent", "UNKNOWN INTENT - action: $action")

        }
        //} else if (action == "RECENT_FILE") {
        //    Log.d("handleIntent", "RECENT_FILE")
    }

    override fun onStop() {
        Log.d("Main[onStop]", "MainActivity - onStop")
        // Update Widget
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, WidgetProvider::class.java)
        val ids = appWidgetManager.getAppWidgetIds(componentName)
        WidgetProvider().onUpdate(this, appWidgetManager, ids)
        super.onStop()
    }

    private fun showPreview(uri: Uri?) {
        Log.d("Main[showPreview]", "uri: $uri")
        binding.drawerLayout.closeDrawers()
        val bundle = Bundle().apply { putString("uri", uri.toString()) }
        navController.navigate(
            R.id.nav_item_upload, bundle, NavOptions.Builder()
                .setPopUpTo(navController.graph.id, true)
                .setLaunchSingleTop(true)
                .build()
        )
    }

    private fun showMultiPreview(fileUris: ArrayList<Uri>) {
        Log.d("Main[showMultiPreview]", "fileUris: $fileUris")
        //fileUris.sort()
        binding.drawerLayout.closeDrawers()
        val bundle = Bundle().apply { putParcelableArrayList("fileUris", fileUris) }
        navController.navigate(
            R.id.nav_item_upload_multi, bundle, NavOptions.Builder()
                .setPopUpTo(navController.graph.id, true)
                .setLaunchSingleTop(true)
                .build()
        )
    }

    // NOTE: This is used by SetupTapTargets showTapTargets
    fun toggleDrawer(open: Boolean = true) {
        if (open) {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        } else {
            binding.drawerLayout.closeDrawers()
        }
    }

    fun launchFilePicker() {
        filePickerLauncher.launch(arrayOf("*/*"))
    }

    fun setDrawerLockMode(enabled: Boolean) {
        Log.d("setDrawerLockMode", "enabled: $enabled")
        val lockMode =
            if (enabled) DrawerLayout.LOCK_MODE_UNLOCKED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED
        Log.d("setDrawerLockMode", "setDrawerLockMode: $lockMode")
        binding.drawerLayout.setDrawerLockMode(lockMode)
    }
}


@UnstableApi
object MediaCache {
    lateinit var simpleCache: SimpleCache
    lateinit var cacheDataSourceFactory: CacheDataSource.Factory

    // TODO: Make Cache Size User Configurable: 350 MB
    fun initialize(context: Context) {
        if (!::simpleCache.isInitialized) {
            simpleCache = SimpleCache(
                File(context.cacheDir, "exoCache"),
                LeastRecentlyUsedCacheEvictor(350 * 1024 * 1024),
                StandaloneDatabaseProvider(context)
            )
            cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(simpleCache)
                .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
    }
}
