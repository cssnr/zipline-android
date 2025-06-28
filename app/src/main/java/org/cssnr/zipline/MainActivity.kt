package org.cssnr.zipline

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.cssnr.zipline.databinding.ActivityMainBinding
import org.cssnr.zipline.widget.WidgetProvider
import org.cssnr.zipline.work.APP_WORKER_CONSTRAINTS
import org.cssnr.zipline.work.AppWorker
import java.net.URL
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    internal lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var filePickerLauncher: ActivityResultLauncher<Array<String>>

    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    @SuppressLint("SetJavaScriptEnabled", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("Main[onCreate]", "savedInstanceState: ${savedInstanceState?.size()}")
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // NOTE: This is used over findNavController to use androidx.fragment.app.FragmentContainerView
        navController =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment).navController
        NavigationUI.setupWithNavController(binding.navView, navController)

        val packageInfo = packageManager.getPackageInfo(this.packageName, 0)
        val versionName = packageInfo.versionName
        Log.d("Main[onCreate]", "versionName: $versionName")

        val headerView = binding.navView.getHeaderView(0)
        val versionTextView = headerView.findViewById<TextView>(R.id.header_version)
        versionTextView.text = "v${versionName}"

        binding.drawerLayout.setStatusBarBackgroundColor(Color.TRANSPARENT)

        // Set Default Preferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        PreferenceManager.setDefaultValues(this, R.xml.preferences_widget, false)

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
                workRequest
            )
        } else {
            // TODO: Confirm this is necessary...
            Log.i("Main[onCreate]", "Ensuring Work is Disabled")
            WorkManager.getInstance(this).cancelUniqueWork("app_worker")
        }

        // Handle Custom Navigation Items
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            Log.d("navigationView", "setNavigationItemSelectedListener: $menuItem")
            binding.drawerLayout.closeDrawers()
            if (menuItem.itemId == R.id.nav_item_upload) {
                Log.d("navigationView", "nav_item_upload")
                filePickerLauncher.launch(arrayOf("*/*"))
                true
            } else {
                NavigationUI.onNavDestinationSelected(menuItem, navController)
            }
        }

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
                    Toast.makeText(this, "No Files Selected!", Toast.LENGTH_SHORT).show()
                }
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

        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
        Log.d("onNewIntent", "extraText: ${extraText?.take(100)}")

        val savedUrl = preferences.getString("ziplineUrl", null)
        val authToken = preferences.getString("ziplineToken", null)
        Log.d("onNewIntent", "savedUrl: $savedUrl")
        Log.d("onNewIntent", "authToken: $authToken")

        if (savedUrl.isNullOrEmpty() || authToken.isNullOrEmpty()) {
            Log.w("onNewIntent", "Missing Zipline URL or Token...")

            navController.navigate(
                R.id.nav_item_login, null, NavOptions.Builder()
                    .setPopUpTo(R.id.nav_item_home, true)
                    .build()
            )
            return
        }

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
            //val launcherAction = preferences.getString("launcher_action", null)
            //Log.d("onNewIntent", "launcherAction: $launcherAction")
            val fromShortcut = intent.getStringExtra("fromShortcut")
            Log.d("onNewIntent", "fromShortcut: $fromShortcut")
            Log.d("onNewIntent", "nav_item_preview: ${R.id.nav_item_upload}")
            Log.d("onNewIntent", "nav_item_short: ${R.id.nav_item_short}")

            when (currentDestinationId) {
                R.id.nav_item_upload, R.id.nav_item_short, R.id.nav_item_text -> {
                    Log.i("onNewIntent", "Navigating away from preview page...")
                    // TODO: Determine the correct navigation call here...
                    //navController.navigate(R.id.nav_item_home)
                    navController.navigate(
                        R.id.nav_item_home, null, NavOptions.Builder()
                            .setPopUpTo(navController.graph.id, true)
                            .build()
                    )
                }
            }

            //} else if (currentDestinationId != R.id.nav_item_home && launcherAction != "previous") {
            //    Log.i("onNewIntent", "HOME SETTING SET - Navigating to HomeFragment")
            //    navController.navigate(R.id.nav_item_home)

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

            if (fileUri == null && !extraText.isNullOrEmpty()) {
                Log.d("onNewIntent", "SEND TEXT DETECTED: ${extraText.take(100)}")
                //if (extraText.lowercase().startsWith("http")) {
                //if (Patterns.WEB_URL.matcher(extraText).matches()) {
                if (isURL(extraText)) {
                    Log.d("onNewIntent", "URL DETECTED: $extraText")
                    binding.drawerLayout.closeDrawers()
                    val bundle = Bundle().apply {
                        putString("url", extraText)
                    }
                    // TODO: Determine how to better pop navigation history...
                    navController.popBackStack(R.id.nav_graph, true)
                    navController.navigate(
                        R.id.nav_item_short, bundle, NavOptions.Builder()
                            .setPopUpTo(R.id.nav_item_home, true)
                            .setLaunchSingleTop(true)
                            .build()
                    )
                } else {
                    Log.i("handleIntent", "PLAIN TEXT DETECTED")
                    val bundle = Bundle().apply {
                        putString("text", extraText)
                    }
                    // TODO: Determine how to properly navigate on new intent...
                    navController.popBackStack(R.id.nav_graph, true)
                    navController.navigate(
                        R.id.nav_item_text, bundle, NavOptions.Builder()
                            .setPopUpTo(R.id.nav_item_home, true)
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
        super.onStop()
        Log.d("Main[onStop]", "MainActivity - onStop")
        // Update Widget
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, WidgetProvider::class.java)
        val ids = appWidgetManager.getAppWidgetIds(componentName)
        WidgetProvider().onUpdate(this, appWidgetManager, ids)
    }

    private fun isURL(url: String): Boolean {
        return try {
            URL(url)
            Log.d("isURL", "TRUE")
            true
        } catch (_: Exception) {
            Log.d("isURL", "FALSE")
            false
        }
    }

    private fun showPreview(uri: Uri?) {
        Log.d("Main[showPreview]", "uri: $uri")
        val bundle = Bundle().apply {
            putString("uri", uri.toString())
        }
        binding.drawerLayout.closeDrawers()
        // TODO: This destroys the home fragment making restore from state impossible
        navController.popBackStack(R.id.nav_graph, true)
        navController.navigate(
            R.id.nav_item_upload, bundle, NavOptions.Builder()
                .setPopUpTo(R.id.nav_item_home, true)
                .setLaunchSingleTop(true)
                .build()
        )
    }

    private fun showMultiPreview(fileUris: ArrayList<Uri>) {
        Log.d("Main[showMultiPreview]", "fileUris: $fileUris")
        //fileUris.sort()
        binding.drawerLayout.closeDrawers()
        val bundle = Bundle().apply { putParcelableArrayList("fileUris", fileUris) }
        navController.popBackStack(R.id.nav_graph, true)
        navController.navigate(
            R.id.nav_item_upload_multi, bundle, NavOptions.Builder()
                .setPopUpTo(R.id.nav_item_home, true)
                .setLaunchSingleTop(true)
                .build()
        )
    }

    fun toggleDrawer(open: Boolean = true) {
        if (open) {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        } else {

            binding.drawerLayout.closeDrawers()
        }
    }

    fun setDrawerLockMode(enabled: Boolean) {
        val lockMode =
            if (enabled) DrawerLayout.LOCK_MODE_UNLOCKED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED
        binding.drawerLayout.setDrawerLockMode(lockMode)
    }
}

fun copyToClipboard(context: Context, url: String) {
    Log.d("copyToClipboard", "url: $url")
    val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("URL", url)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied URL to Clipboard.", Toast.LENGTH_SHORT).show()
}
