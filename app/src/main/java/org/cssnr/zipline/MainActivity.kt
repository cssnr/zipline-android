package org.cssnr.zipline

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.core.view.size
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
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
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.cssnr.zipline.databinding.ActivityMainBinding
import org.cssnr.zipline.db.UserDao
import org.cssnr.zipline.db.UserDatabase
import org.cssnr.zipline.log.AppLogs
import org.cssnr.zipline.ui.home.HomeViewModel
import org.cssnr.zipline.ui.showSnackbar
import org.cssnr.zipline.ui.user.updateAvatarActivity
import org.cssnr.zipline.ui.user.updateUserActivity
import org.cssnr.zipline.widget.WidgetProvider
import org.cssnr.zipline.work.enqueueWorkRequest
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    internal lateinit var binding: ActivityMainBinding

    private lateinit var navController: NavController
    private lateinit var navHostFragment: NavHostFragment
    private lateinit var filePickerLauncher: ActivityResultLauncher<Array<String>>

    // Whether the file picker result that is currently pending should open its workflow
    // as an external entry (home screen widget tap -> back exits to the launcher).
    // Recorded when the picker is launched, since the result comes back later through
    // the activity result API and the originating intent is no longer around.
    private var filePickerExternal = false

    // Workflow destinations are mutually-exclusive singletons: only one may be on the
    // back stack at a time. Opening one replaces any existing workflow, and a stale
    // workflow hidden under non-workflow pages (e.g. Settings opened from the text
    // page) is scrubbed so back never ends up walking through a leftover workflow.
    private val workflowDestinations = setOf(
        R.id.nav_item_upload,
        R.id.nav_item_upload_multi,
        R.id.nav_item_short,
        R.id.nav_item_text,
    )

    // True while the current workflow was opened from outside the app (share/open deep
    // link, or a home screen widget button). Back then exits the activity back to
    // whatever launched it (sharing app / launcher) instead of walking the app's
    // internal back stack. Set by navigateWorkflow and cleared by the destination
    // listener when leaving a workflow (or by any in-app navigateWorkflow entry, since
    // interacting in-app makes the workflow in-app).
    private var externalWorkflow = false

    // Captures back while an externally-opened workflow is showing, so back exits to
    // the app that shared/opened the content (or the launcher for a widget tap) rather
    // than popping the app stack. Enabled-state is refreshed by the destination listener
    // and by navigateWorkflow's no-op path (the listener only runs when navigation
    // actually happens).
    private val externalBackExit by lazy {
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                Log.i(LOG_TAG, "Back on external workflow - exiting to previous app")
                finish()
            }
        }
    }

    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    companion object {
        const val LOG_TAG = "MainActivity"
    }

    @OptIn(UnstableApi::class)
    @SuppressLint("SetJavaScriptEnabled", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(LOG_TAG, "savedInstanceState: ${savedInstanceState?.size()}")
        enableEdgeToEdge(
            //statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),

            //navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
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
            Log.d(LOG_TAG, "startPreference: $startPreference")
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
            R.id.nav_item_logs to R.id.nav_item_settings,
        )
        // Destination w/ No Parent
        val hiddenDestinations = setOf(
            R.id.nav_item_upload,
            R.id.nav_item_upload_multi,
            R.id.nav_item_short,
            R.id.nav_item_text,
        )
        // Implement Navigation Hacks Because.......Android?
        onBackPressedDispatcher.addCallback(this, externalBackExit)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Enable back-to-previous-app only while an externally-opened workflow shows
            externalBackExit.isEnabled = externalWorkflow && destination.id in workflowDestinations
            // Leaving the workflow resets the external flag so an in-app page never exits the app
            if (destination.id !in workflowDestinations) {
                externalWorkflow = false
            }
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
                    val destItem = binding.navView.menu.findItem(R.id.nav_item_home)
                    NavigationUI.onNavDestinationSelected(destItem, navController)
                }
                true
            } else if (menuItem.itemId == R.id.nav_item_upload) {
                Log.d("Drawer", "nav_item_upload")
                launchFilePicker()
                true
            } else if (menuItem.itemId == R.id.nav_item_text) {
                Log.d("Drawer", "nav_item_text")
                navigateWorkflow(R.id.nav_item_text)
                binding.drawerLayout.closeDrawers()
                true
            } else {
                val handled = NavigationUI.onNavDestinationSelected(menuItem, navController)
                Log.d("Drawer", "ELSE - handled: $handled")
                handled
            }
        }

        //// NOTE: This is only needed for default: false
        //// Set Debug Preferences
        //Log.d(LOG_TAG, "Set Debug Preferences")
        //if (BuildConfig.DEBUG) {
        //    Log.i(LOG_TAG, "DEBUG BUILD DETECTED!")
        //    if (!preferences.contains("enable_debug_logs")) {
        //        Log.i(LOG_TAG, "ENABLING DEBUG LOGGING...")
        //        preferences.edit {
        //            putBoolean("enable_debug_logs", true)
        //        }
        //    }
        //}

        // Set Default Preferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        PreferenceManager.setDefaultValues(this, R.xml.preferences_widget, false)

        // Update Status Bar
        //window.statusBarColor = Color.TRANSPARENT
        //binding.drawerLayout.setStatusBarBackgroundColor(Color.TRANSPARENT)

        // Set Global Left/Right System Insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.contentMain.contentMainLayout) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            Log.i("Main[ViewCompat]", "bars: $bars")
            v.updatePadding(left = bars.left, right = bars.right)
            insets
        }

        // Update Navigation Bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        // Update Header Padding
        val headerView = binding.navView.getHeaderView(0)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            Log.d("ViewCompat", "binding.root: top: ${bars.top}")
            if (bars.top > 0) {
                headerView.updatePadding(top = bars.top)
            }
            insets
        }

        // Update Header Text
        val savedUrl = preferences.getString("ziplineUrl", null)
        if (savedUrl != null) {
            val dao: UserDao = UserDatabase.getInstance(this).userDao()
            lifecycleScope.launch {
                val user = dao.getUserByUrl(savedUrl)
                Log.d(LOG_TAG, "user: $user")
                val headerUsername = headerView.findViewById<TextView>(R.id.header_username)
                headerUsername?.text = user?.username ?: getString(R.string.app_name)
            }
        }
        val headerUrl = headerView.findViewById<TextView>(R.id.header_url)
        headerUrl.text = savedUrl?.toUri()?.host ?: getString(R.string.app_name)

        // Update Header Image
        val headerImage = headerView.findViewById<ShapeableImageView>(R.id.header_image)
        val radius = resources.getDimension(R.dimen.avatar_radius)
        headerImage.shapeAppearanceModel = headerImage.shapeAppearanceModel.toBuilder()
            .setAllCorners(CornerFamily.ROUNDED, radius).build()
        val file = File(filesDir, "avatar.png")
        if (file.exists()) {
            Log.i(LOG_TAG, "GLIDE LOAD - MainActivity - file.name: ${file.name}")
            Glide.with(headerImage).load(file).signature(ObjectKey(file.lastModified()))
                .into(headerImage)
        }

        // Work Manager
        val authToken = preferences.getString("ziplineToken", null)
        Log.d(LOG_TAG, "authToken: ${authToken?.take(24)}...")
        val workInterval = preferences.getString("work_interval", null) ?: "0"
        Log.d(LOG_TAG, "workInterval: $workInterval")
        // NOTE: This just ensures work manager is enabled or disabled based on preference
        if (workInterval != "0" && authToken != null) {
            enqueueWorkRequest(workInterval, ExistingPeriodicWorkPolicy.KEEP)
        } else {
            // TODO: Confirm this is necessary...
            Log.i(LOG_TAG, "Ensuring Work is Disabled")
            WorkManager.getInstance(this).cancelUniqueWork("app_worker")
        }

        // File Picker for UPLOAD_FILE Intent and Shortcut
        filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                val external = filePickerExternal
                filePickerExternal = false
                Log.d("filePickerLauncher", "uris: $uris - external: $external")
                if (uris.size > 1) {
                    Log.i("filePickerLauncher", "MULTI!")
                    showMultiPreview(uris as ArrayList<Uri>, external)
                } else if (uris.size == 1) {
                    Log.i("filePickerLauncher", "SINGLE!")
                    showPreview(uris[0], external)
                } else {
                    Log.w("filePickerLauncher", "No Files Selected!")
                    // Cancelling the picker (back) is the only way to get an empty result.
                    // On an external entry the app is only a pass-through to the picker,
                    // so cancelling must leave the app entirely - otherwise back reveals
                    // MainActivity on the start destination instead of the launcher the
                    // widget lives on. externalBackExit only covers a showing workflow,
                    // and there is none here.
                    if (external) {
                        Log.i(LOG_TAG, "Picker cancelled on external entry - exiting to previous app")
                        finish()
                    }
                    //Toast.makeText(this, "No Files Selected!", Toast.LENGTH_SHORT).show()
                }
            }

        MediaCache.initialize(this)

        // Version Tracking
        @Suppress("DEPRECATION")
        val packageInfo = packageManager.getPackageInfo(packageName, 0)

        val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        Log.d(LOG_TAG, "currentVersionCode: $currentVersionCode")

        val isFirstRun = !preferences.contains("first_run_shown")
        Log.d(LOG_TAG, "isFirstRun: $isFirstRun")

        val previousVersionCode = preferences.getLong("previous_version_code", -1L)
        Log.d(LOG_TAG, "previousVersion: $previousVersionCode")

        when {
            isFirstRun -> {
                Log.i(LOG_TAG, "FIRST RUN DETECTED")
                // NOTE: First-run is handled by the Login/Setup flow in this app.
            }

            currentVersionCode > previousVersionCode -> {
                Log.i(LOG_TAG, "APP UPGRADE: $previousVersionCode -> $currentVersionCode")

                // Old Migration - before the current Version Tracking logic was added
                val previousVersion = preferences.getInt("previousVersion", 0)
                Log.d(LOG_TAG, "previousVersion: $previousVersion")
                if (previousVersion == 0 && !authToken.isNullOrEmpty()) {
                    Log.i(LOG_TAG, "LEGACY UPGRADE DETECTED - Updating User and Avatar")
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                updateAvatarActivity()
                            } catch (e: IOException) {
                                Log.e(LOG_TAG, "onCreate Avatar User IOException: ${e.message}")
                                AppLogs.d(this@MainActivity, "updateAvatarActivity: ${e.message}")
                            }
                            try {
                                updateUserActivity()
                            } catch (e: IOException) {
                                Log.e(LOG_TAG, "onCreate Update User IOException: ${e.message}")
                                AppLogs.d(this@MainActivity, "updateUserActivity: ${e.message}")
                            }
                        }
                    }
                }

                // Delete Old Text Log
                if (111L in (previousVersionCode + 1)..currentVersionCode) {
                    Log.i(LOG_TAG, "DELETING OLD LOG FILE - debug_log.txt")
                    File(filesDir, "debug_log.txt").delete()
                }
            }

            currentVersionCode < previousVersionCode -> {
                Log.w(LOG_TAG, "APP DOWNGRADE: $previousVersionCode -> $currentVersionCode")
                // TODO: Downgrade - this will never actually happen and should probably be removed
            }
        }

        if (previousVersionCode != currentVersionCode) {
            Log.d(LOG_TAG, "preferences.edit - previous_version_code: $currentVersionCode")
            preferences.edit { putLong("previous_version_code", currentVersionCode) }
        }

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
        Log.d("onNewIntent", "authToken: ${authToken?.take(24)}...")

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
        Log.d("onNewIntent", "isCalendarUri: $isCalendarUri")
        if (isCalendarUri) {
            Log.i("onNewIntent", "Calendar Links Not Supported!")
            showSnackbar("Calendar Links Not Supported!")
            return
        }

        if (action == Intent.ACTION_MAIN) {
            Log.d("onNewIntent", "ACTION_MAIN")

            binding.drawerLayout.closeDrawers()

            // TODO: Cleanup the logic for handling MAIN intent...
            val fromShortcut = intent.getStringExtra("fromShortcut")
            Log.d("onNewIntent", "fromShortcut: $fromShortcut")

            if (fromShortcut == "upload") {
                Log.d("onNewIntent", "launchFilePicker")
                // Launcher shortcut: same as a widget tap, back returns to the launcher
                launchFilePicker(external = true)
            } else if (fromShortcut == "text") {
                Log.d("onNewIntent", "navigateWorkflow: nav_item_text")
                // Launcher shortcut: same as a widget tap, back returns to the launcher
                navigateWorkflow(R.id.nav_item_text, external = true)
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
                Log.i("onNewIntent", "SEND TEXT DETECTED: ${extraText.take(100)}")
                //if (extraText.lowercase().startsWith("http")) {
                //if (Patterns.WEB_URL.matcher(extraText).matches()) {
                if (isTextUrl(extraText)) {
                    Log.d("onNewIntent", "URL DETECTED: $extraText")
                    binding.drawerLayout.closeDrawers()
                    val bundle = Bundle().apply { putString("url", extraText) }
                    navigateWorkflow(R.id.nav_item_short, bundle, replaceCurrent = true, external = true)
                } else {
                    Log.d("onNewIntent", "PLAIN TEXT DETECTED")
                    val bundle = Bundle().apply { putString("text", extraText) }
                    navigateWorkflow(R.id.nav_item_text, bundle, replaceCurrent = true, external = true)
                }
            } else {
                showPreview(fileUri, external = true)
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
                showSnackbar("Error Parsing URI!", true)
                Log.w("onNewIntent", "fileUris is null")
                return
            }
            showMultiPreview(fileUris, external = true)

        } else if (action == Intent.ACTION_VIEW) {
            Log.d("onNewIntent", "ACTION_VIEW")

            showPreview(data, external = true)

        } else if (action == "UPLOAD_FILE") {
            Log.d("onNewIntent", "UPLOAD_FILE")

            // Widget tap: the launcher screen is what back should return to
            launchFilePicker(external = true)

        } else if (action == "UPLOAD_TEXT") {
            Log.d("onNewIntent", "UPLOAD_TEXT")

            // Widget tap: the launcher screen is what back should return to
            navigateWorkflow(R.id.nav_item_text, external = true)

        } else {
            showSnackbar("Unknown Link!", true)
            Log.w("onNewIntent", "UNKNOWN INTENT - action: $action")

        }
    }

    // Unified entry point for workflow destinations (upload / multi / short / text).
    // Workflows are mutually-exclusive singletons: only one may exist on the back
    // stack, and it is either placed above the current page (in-app push) or replaces
    // an existing / stale workflow so back never walks through leftover workflow
    // screens. Workflows opened from outside the app (external = true: share/open deep
    // link, or a home screen widget button) track externalWorkflow so the back callback
    // exits to the previous app instead of walking the back stack. replaceCurrent
    // forces a fresh instance (e.g. a new file picker result); opening the same workflow
    // without replaceCurrent keeps the current instance.
    private fun navigateWorkflow(
        destinationId: Int,
        args: Bundle? = null,
        replaceCurrent: Boolean = false,
        external: Boolean = false,
    ) {
        val currentDestinationId = navController.currentDestination?.id
        Log.d("navigateWorkflow", "destinationId: $destinationId - args: $args - replaceCurrent: $replaceCurrent")
        Log.d("navigateWorkflow", "currentDestinationId: $currentDestinationId")
        // Already on this workflow and not refreshing it: keep the in-progress
        // fragment untouched (launchSingleTop would recreate it and lose state).
        // An in-app entry still clears the external flag, and the destination listener
        // won't fire because no navigation happens, so refresh the callback here too.
        if (currentDestinationId == destinationId && !replaceCurrent) {
            externalWorkflow = external
            externalBackExit.isEnabled =
                externalWorkflow && currentDestinationId in workflowDestinations
            Log.d("navigateWorkflow", "Already on destination $destinationId - no-op")
            return
        }

        externalWorkflow = external

        // Any workflow currently on the back stack (the current one, or a stale one
        // buried under non-workflow pages like Settings opened from the text page) is
        // replaced so the workflows stay mutually-exclusive singletons.
        val hasWorkflow = workflowDestinations.any { id ->
            runCatching { navController.getBackStackEntry(id) }.isSuccess
        }
        val navOptions = when {
            // Pop anything above the root (including any current/stale workflow), then
            // open the workflow fresh directly above the root.
            hasWorkflow ->
                NavOptions.Builder()
                    .setPopUpTo(navController.graph.startDestinationId, false)
                    .setLaunchSingleTop(true)
                    .build()
            // Open the workflow above the current destination.
            else ->
                NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .build()
        }
        navController.navigate(destinationId, args, navOptions)
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

    private fun showPreview(uri: Uri?, external: Boolean = false) {
        Log.d("Main[showPreview]", "uri: $uri")
        binding.drawerLayout.closeDrawers()
        if (uri == null) {
            Log.w("Main[showPreview]", "uri is null - nothing to preview")
            showSnackbar("Nothing to Process!", true)
            return
        }
        val bundle = Bundle().apply { putString("uri", uri.toString()) }
        navigateWorkflow(R.id.nav_item_upload, bundle, replaceCurrent = true, external = external)
    }

    private fun showMultiPreview(fileUris: ArrayList<Uri>, external: Boolean = false) {
        Log.d("Main[showMultiPreview]", "fileUris: $fileUris")
        //fileUris.sort()
        binding.drawerLayout.closeDrawers()
        if (fileUris.isEmpty()) {
            Log.w("Main[showMultiPreview]", "fileUris is empty - nothing to preview")
            showSnackbar("Nothing to Process!", true)
            return
        }
        val bundle = Bundle().apply { putParcelableArrayList("fileUris", fileUris) }
        navigateWorkflow(R.id.nav_item_upload_multi, bundle, replaceCurrent = true, external = external)
    }

    private fun isTextUrl(input: String): Boolean {
        val url = input.toHttpUrlOrNull() ?: return false
        if (input != url.toString()) return false
        if (url.scheme !in listOf("http", "https")) return false
        if (url.host.isBlank()) return false
        if (url.toString().length > 2048) return false
        return true
    }

    // NOTE: This is used by SetupTapTargets showTapTargets
    fun toggleDrawer(open: Boolean = true) {
        if (open) {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        } else {
            binding.drawerLayout.closeDrawers()
        }
    }

    // Single entry point for the file picker so every caller records whether the
    // resulting workflow is external (home screen widget tap) before the picker
    // activity takes over - the result arrives long after the originating intent.
    fun launchFilePicker(external: Boolean = false) {
        filePickerExternal = external
        Log.d(LOG_TAG, "launchFilePicker - external: $external")
        filePickerLauncher.launch(arrayOf("*/*"))
    }

    fun openTextUpload() {
        navigateWorkflow(R.id.nav_item_text)
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
    private lateinit var cacheDataSourceFactory: CacheDataSource.Factory

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
