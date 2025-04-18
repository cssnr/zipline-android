package org.cssnr.zipline

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.core.view.size
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentManager
import org.cssnr.zipline.databinding.ActivityMainBinding
import java.net.URL

class MainActivity : AppCompatActivity() {

    internal lateinit var binding: ActivityMainBinding

    private lateinit var filePickerLauncher: ActivityResultLauncher<Array<String>>


    @SuppressLint("SetJavaScriptEnabled", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("Main[onCreate]", "savedInstanceState: ${savedInstanceState?.size()}")
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val packageInfo = packageManager.getPackageInfo(this.packageName, 0)
        val versionName = packageInfo.versionName
        Log.d("Main[onCreate]", "versionName: $versionName")

        val headerView = binding.navigationView.getHeaderView(0)
        val versionTextView = headerView.findViewById<TextView>(R.id.header_version)
        versionTextView.text = "v${versionName}"

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        drawer.setStatusBarBackgroundColor(Color.TRANSPARENT)

        filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                Log.d("filePickerLauncher", "uri: $uri")
                if (uri != null) {
                    val mimeType = contentResolver.getType(uri)
                    Log.d("filePickerLauncher", "mimeType: $mimeType")
                    showPreview(uri, mimeType)
                } else {
                    Log.w("filePickerLauncher", "No File Selected!")
                    Toast.makeText(this, "No File Selected!", Toast.LENGTH_SHORT).show()
                }
            }

        // Navigation - On Click
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            Log.d("NavigationDrawer", "menuItem: $menuItem")
            val currentFragment = supportFragmentManager.findFragmentById(R.id.main)
            Log.d("NavigationDrawer", "currentFragment: $currentFragment")

            when (menuItem.itemId) {
                R.id.nav_item_home -> {
                    Log.d("NavigationDrawer", "nav_item_home")
                    if (currentFragment !is HomeFragment) {
                        Log.d("NavigationDrawer", "NOT ON HomeFragment")
                        val fragmentManager = supportFragmentManager
                        val fragmentFound = fragmentManager.fragments.any { it is HomeFragment }
                        Log.d("NavigationDrawer", "fragmentFound: $fragmentFound")

                        val popped = supportFragmentManager.popBackStackImmediate("HomeFragment", 0)
                        Log.d("NavigationDrawer", "popped: $popped")
                        if (!popped) {
                            Log.d("NavigationDrawer", "beginTransaction: HomeFragment")
                            supportFragmentManager.beginTransaction()
                                .replace(R.id.main, HomeFragment())
                                .addToBackStack("HomeFragment")
                                .commit()
                        }
                    } else {
                        Log.d("NavigationDrawer", "ALREADY ON HomeFragment")
                        val url = currentFragment.currentUrl
                        Log.d("NavigationDrawer", "currentFragment.currentUrl: $url")
                        val ziplineUrl = getSharedPreferences("default_preferences", MODE_PRIVATE)
                            .getString("ziplineUrl", null)
                        Log.d("NavigationDrawer", "ziplineUrl: $ziplineUrl")
                        val path = url.removePrefix(ziplineUrl!!)
                        Log.d("NavigationDrawer", "path: $path")
                        if (path.startsWith("/u/") || path.startsWith("/view/")) {
                            Log.i("NavigationDrawer", "Reloading HomeFragment!")
                            val home = HomeFragment().apply {
                                arguments = bundleOf("url" to ziplineUrl)
                            }
                            Log.d("NavigationDrawer", "arguments.url: $ziplineUrl")
                            supportFragmentManager.beginTransaction()
                                .replace(R.id.main, home)
                                .addToBackStack("HomeFragment")
                                .commit()
                        }
                    }
                    Log.w("NavigationDrawer", "setCheckedItem: nav_item_home")
                    binding.navigationView.setCheckedItem(R.id.nav_item_home)
                    binding.drawerLayout.closeDrawers()
                    true
                }

                R.id.nav_item_upload -> {
                    Log.d("NavigationDrawer", "nav_item_upload")
                    filePickerLauncher.launch(arrayOf("*/*"))
                    binding.drawerLayout.closeDrawers()
                    false
                }

                R.id.nav_item_settings -> {
                    Log.d("NavigationDrawer", "nav_item_settings")
                    if (currentFragment !is SettingsFragment) {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.main, SettingsFragment())
                            .addToBackStack("SettingsFragment")
                            .commit()
                    }
                    binding.navigationView.setCheckedItem(R.id.nav_item_settings)
                    binding.drawerLayout.closeDrawers()
                    true
                }

                else -> {
                    Log.w("NavigationDrawer", "UNKNOWN ITEM")
                    Toast.makeText(this, "Unknown Menu Item!", Toast.LENGTH_LONG).show()
                    false
                }
            }
        }

        // Navigation - Back Button
        supportFragmentManager.addOnBackStackChangedListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.main)
            Log.d("BackStackChanged", "currentFragment: $currentFragment")
            val itemId = when (currentFragment) {
                is SettingsFragment -> R.id.nav_item_settings
                is HomeFragment -> R.id.nav_item_home
                is PreviewFragment -> View.NO_ID
                else -> View.NO_ID
            }
            Log.d("BackStackChanged", "itemId: $itemId")
            if (itemId != View.NO_ID) {
                Log.d("BackStackChanged", "SET isChecked")
                binding.navigationView.menu.findItem(itemId)?.isChecked = true
            } else {
                Log.w("BackStackChanged", "NOT Checkable")
                //binding.navigationView.menu.setGroupCheckable(0, false, true)
                clearDrawer()
            }
        }

        handleIntent(intent, savedInstanceState)
    }

    fun setDrawerLockMode(enabled: Boolean) {
        val lockMode =
            if (enabled) DrawerLayout.LOCK_MODE_UNLOCKED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED
        binding.drawerLayout.setDrawerLockMode(lockMode)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("onNewIntent", "intent: $intent")
        handleIntent(intent, null)
    }

    private fun handleIntent(intent: Intent, savedInstanceState: Bundle?) {
        Log.d("handleIntent", "intent: $intent")

        Log.d("handleIntent", "intent.data: ${intent.data}")
        Log.d("handleIntent", "intent.type: ${intent.type}")
        Log.d("handleIntent", "intent.action: ${intent.action}")

        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
        Log.d("handleIntent", "extraText: $extraText")

        val sharedPreferences = getSharedPreferences("default_preferences", MODE_PRIVATE)
        val ziplineUrl = sharedPreferences.getString("ziplineUrl", null)
        val ziplineToken = sharedPreferences.getString("ziplineToken", null)
        Log.d("handleIntent", "ziplineUrl: $ziplineUrl")
        Log.d("handleIntent", "ziplineToken: $ziplineToken")

        if (ziplineUrl.isNullOrEmpty() || ziplineToken.isNullOrEmpty()) {
            Log.w("handleIntent", "Missing Zipline URL or Token...")

            supportFragmentManager.beginTransaction()
                .replace(R.id.main, SetupFragment())
                .commit()

        } else if (Intent.ACTION_MAIN == intent.action) {
            Log.d("handleIntent", "ACTION_MAIN: ${savedInstanceState?.size()}")

            // TODO: Verify this does not cause any issues
            if (savedInstanceState == null) {
                val existingFragment = supportFragmentManager.findFragmentById(R.id.main)
                Log.d("handleIntent", "existingFragment: $existingFragment")
                val launcherAction = sharedPreferences.getString("launcher_action", null)
                Log.d("handleIntent", "launcherAction: $launcherAction")

                if (launcherAction != "previous") {
                    Log.d("MyLogic", "Check for Home Fragment")
                    if (existingFragment !is HomeFragment) {
                        Log.d("MyLogic", "COLD START")
                    }
                } else {
                    Log.d("MyLogic", "Check for Any Fragment")
                    if (existingFragment == null) {
                        Log.d("MyLogic", "COLD START")
                    }
                }

                //if (existingFragment !is HomeFragment) {
                if ((launcherAction != "previous" && existingFragment !is HomeFragment) || (launcherAction == "previous" && existingFragment == null)) {
                    // TODO: Hack for ListPreference dialog open on intent start
                    val existingDialog =
                        supportFragmentManager.findFragmentByTag("androidx.preference.PreferenceFragment.DIALOG")
                    if (existingDialog != null) {
                        Log.d("handleIntent", "REMOVE: $existingDialog")
                        supportFragmentManager.beginTransaction().remove(existingDialog).commit()
                    }
                    Log.i("handleIntent", "COLD START: popBackStack and beginTransaction")
                    supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.main, HomeFragment())
                        .commit()
                    binding.navigationView.setCheckedItem(R.id.nav_item_home)
                }
            }
            val fromShortcut = intent.getStringExtra("fromShortcut")
            Log.d("handleIntent", "fromShortcut: $fromShortcut")
            if (fromShortcut == "upload") {
                Log.d("handleIntent", "filePickerLauncher.launch")
                filePickerLauncher.launch(arrayOf("*/*"))
            }

        } else if (Intent.ACTION_SEND == intent.action) {
            Log.d("handleIntent", "ACTION_SEND")

            val fileUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            Log.d("handleIntent", "File URI: $fileUri")

            if (fileUri == null && !extraText.isNullOrEmpty()) {
                Log.d("handleIntent", "SEND TEXT DETECTED: $extraText")
                //if (extraText.lowercase().startsWith("http")) {
                //if (Patterns.WEB_URL.matcher(extraText).matches()) {
                if (isURL(extraText)) {
                    Log.d("handleIntent", "URL DETECTED: $extraText")
                    val fragment = ShortFragment()
                    val bundle = Bundle().apply {
                        putString("url", extraText)
                    }
                    fragment.arguments = bundle
                    supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.main, fragment)
                        .commit()
                } else {
                    Toast.makeText(this, "Not Yet Implemented!", Toast.LENGTH_SHORT).show()
                    Log.w("handleIntent", "NOT IMPLEMENTED")
                }
            } else {
                showPreview(fileUri, intent.type)
            }

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

    private fun clearDrawer(close: Boolean = false) {
        Log.w("clearDrawer", "CLEAR DRAWER CLEAR DRAWER close: $close")
        binding.navigationView.menu.setGroupCheckable(0, true, true)
        for (i in 0 until binding.navigationView.menu.size) {
            binding.navigationView.menu[i].isChecked = false
        }
        if (close) {
            binding.drawerLayout.closeDrawers()
        }
    }

    private fun showPreview(uri: Uri?, type: String?) {
        Log.d("Main[showPreview]", "File URI: $uri")

        // TODO: Dummy item since I clearly don't know how navigation works...
        Log.d("Main[showPreview]", "checkedItem: ${binding.navigationView.checkedItem}")
        binding.navigationView.setCheckedItem(R.id.nav_item_none)
        binding.drawerLayout.closeDrawers()

        val fragment = PreviewFragment()
        val bundle = Bundle().apply {
            putString("uri", uri.toString())
            putString("type", type)
        }
        fragment.arguments = bundle
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.main, fragment)
            .commit()
    }
}
