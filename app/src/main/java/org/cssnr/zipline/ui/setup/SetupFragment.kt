package org.cssnr.zipline.ui.setup

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.shape.CornerFamily
import kotlinx.coroutines.launch
import nl.dionsegijn.konfetti.core.Angle
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.Spread
import nl.dionsegijn.konfetti.core.emitter.Emitter
import org.cssnr.zipline.MainActivity
import org.cssnr.zipline.R
import org.cssnr.zipline.databinding.FragmentSetupBinding
import org.cssnr.zipline.db.UserDao
import org.cssnr.zipline.db.UserDatabase
import org.cssnr.zipline.db.UserEntity
import org.cssnr.zipline.ui.user.UserFragment
import org.cssnr.zipline.work.enqueueWorkRequest
import java.io.File
import java.util.concurrent.TimeUnit

class SetupFragment : Fragment() {

    private var _binding: FragmentSetupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SetupViewModel by activityViewModels()

    private val navController by lazy { findNavController() }
    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }

    companion object {
        const val LOG_TAG = "SetupFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(LOG_TAG, "onDestroyView")
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        Log.d("Setup[onStart]", "onStart - Hide UI")
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav).visibility = View.GONE
        (activity as? MainActivity)?.setDrawerLockMode(false)
    }

    override fun onStop() {
        Log.d("Setup[onStop]", "onStop - Show UI")
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav).visibility =
            View.VISIBLE
        (activity as? MainActivity)?.setDrawerLockMode(true)
        super.onStop()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(LOG_TAG, "onViewCreated: ${savedInstanceState?.size()}")

        // Lock Navigation Drawer
        (requireActivity() as MainActivity).setDrawerLockMode(false)

        val ctx = requireContext()

        val savedUrl = preferences.getString("ziplineUrl", null) ?: ""
        Log.d(UserFragment.LOG_TAG, "savedUrl: $savedUrl")

        lifecycleScope.launch {
            val userDao: UserDao = UserDatabase.getInstance(ctx).userDao()
            val userEntity: UserEntity? = userDao.getUserByUrl(url = savedUrl)
            Log.d(UserFragment.LOG_TAG, "userEntity: $userEntity")
            if (userEntity != null) {
                _binding?.userUsername?.text =
                    getString(R.string.welcome_username, userEntity.username)
            }
        }

        // Version
        val packageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val versionName = packageInfo.versionName
        Log.d(LOG_TAG, "versionName: $versionName")
        binding.appVersion.text = getString(R.string.version_string, versionName)

        val radius = ctx.resources.getDimension(R.dimen.avatar_radius)
        binding.appIcon.setShapeAppearanceModel(
            binding.appIcon.shapeAppearanceModel.toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, radius).build()
        )

        val avatarFile = File(ctx.filesDir, "avatar.png")
        Log.d(UserFragment.LOG_TAG, "avatarFile: $avatarFile")
        if (avatarFile.exists()) {
            Log.i(UserFragment.LOG_TAG, "GLIDE LOAD - binding.appIcon: $avatarFile")
            Glide.with(binding.appIcon).load(avatarFile)
                .signature(ObjectKey(avatarFile.lastModified())).into(binding.appIcon)
        }

        // Share Upload
        binding.shareUpload.isChecked = preferences.getBoolean("share_after_upload", false)
        binding.shareUpload.setOnClickListener {
            //val newValue = binding.shareUpload.isChecked
            //binding.shareUpload.isChecked = !newValue
            Log.d(LOG_TAG, "shareUpload.setOnClickListener: ${binding.shareUpload.isChecked}")
            preferences.edit { putBoolean("share_after_upload", binding.shareUpload.isChecked) }
        }

        // Share Short
        binding.shareShort.isChecked = preferences.getBoolean("share_after_short", false)
        binding.shareShort.setOnClickListener {
            //val newValue = binding.shareShort.isChecked
            //binding.shareShort.isChecked = !newValue
            Log.d(LOG_TAG, "shareShort.setOnClickListener: ${binding.shareShort.isChecked}")
            preferences.edit { putBoolean("share_after_short", binding.shareShort.isChecked) }
        }

        //val notificationsEnabled = ctx.isChannelEnabled()
        //Log.i(LOG_TAG, "notificationsEnabled: $notificationsEnabled")
        //binding.notificationsSwitch.isChecked = notificationsEnabled

        // Start Destination Radio
        binding.startDestinationRadio.check(R.id.start_radio_home)
        binding.startDestinationRadio.setOnCheckedChangeListener { group, id ->
            Log.d(LOG_TAG, "id: $id")
            Log.d(LOG_TAG, "group: $group")
            val value = when (id) {
                R.id.start_radio_home -> "home"
                R.id.start_radio_files -> "files"
                else -> "home"
            }
            Log.d(LOG_TAG, "value: $value")
            preferences.edit { putString("start_destination", value) }
        }
        //// Start Destination Spinner
        //val launchEntries = resources.getStringArray(R.array.launcher_entries)
        //val launchValues = resources.getStringArray(R.array.launcher_values)
        //val launchAdapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, launchEntries)
        //launchAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        //binding.workIntervalSpinner.adapter = launchAdapter
        //val currentStartDestination = preferences.getString("start_destination", null) ?: "0"
        //binding.workIntervalSpinner.setSelection(launchValues.indexOf(currentStartDestination))
        //binding.workIntervalSpinner.onItemSelectedListener =
        //    object : AdapterView.OnItemSelectedListener {
        //        override fun onItemSelected(
        //            parent: AdapterView<*>,
        //            view: View?,
        //            position: Int,
        //            id: Long
        //        ) {
        //            val selectedValue = launchValues[position]
        //            Log.d(LOG_TAG, "workIntervalSpinner: value: $selectedValue")
        //            preferences.edit { putString("start_destination", selectedValue) }
        //        }
        //
        //        override fun onNothingSelected(parent: AdapterView<*>) {
        //            Log.w(LOG_TAG, "workIntervalSpinner: No Item Selected")
        //        }
        //    }

        // Update Interval Spinner
        val workEntries = resources.getStringArray(R.array.work_interval_entries)
        val workValues = resources.getStringArray(R.array.work_interval_values)
        val workAdapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, workEntries)
        workAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.workIntervalSpinner.adapter = workAdapter
        val currentWorkInterval = preferences.getString("work_interval", null) ?: "0"
        binding.workIntervalSpinner.setSelection(workValues.indexOf(currentWorkInterval))
        // TODO: CONSIDER NOT CHANGING THIS UNTIL SETUP COMPLETE
        binding.workIntervalSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val selectedValue = workValues[position]
                    Log.d(LOG_TAG, "workIntervalSpinner: selectedValue: $selectedValue")
                    preferences.edit { putString("work_interval", selectedValue) }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {
                    Log.w(LOG_TAG, "workIntervalSpinner: No Item Selected")
                }
            }

        // Start App Listener
        val startAppListener: (View) -> Unit = { v ->
            Log.d(LOG_TAG, "startAppListener: view: $v")

            binding.btnContinue.isEnabled = false
            binding.btnSkip.isEnabled = false

            val workInterval = preferences.getString("work_interval", null) ?: "0"
            Log.d(LOG_TAG, "startAppListener: workInterval: $workInterval")
            if (workInterval != "0") {
                ctx.enqueueWorkRequest(workInterval)
            }

            // Arguments
            val bundle = bundleOf()
            when (v.id) {
                R.id.btn_continue -> {
                    Log.i(LOG_TAG, "Continue Button Pressed. Showing First Run...")
                    bundle.putBoolean("isFirstRun", true)
                }
            }
            Log.d(LOG_TAG, "startAppListener: bundle: $bundle")

            // Unlock Drawer
            (requireActivity() as MainActivity).setDrawerLockMode(true)

            // Navigate Home
            // TODO: Allow configuring custom start destination in Setup...
            if (preferences.getString("start_destination", null) == "files") {
                Log.i(LOG_TAG, "startAppListener: setStartDestination: ${R.id.nav_item_files}")
                navController.graph.setStartDestination(R.id.nav_item_files)
            }
            navController.navigate(
                navController.graph.startDestinationId, bundle, NavOptions.Builder()
                    .setPopUpTo(navController.graph.id, true)
                    .build()
            )
        }
        binding.btnContinue.setOnClickListener(startAppListener)
        binding.btnSkip.setOnClickListener(startAppListener)

        if (viewModel.confettiShown.value != true) {
            viewModel.confettiShown.value = true
            hitEmWithConfetti()
        }
    }

    private fun hitEmWithConfetti() {
        //val party = Party(
        //    speed = 10f,
        //    maxSpeed = 30f,
        //    damping = 0.9f,
        //    angle = Angle.RIGHT - 45,
        //    spread = Spread.SMALL,
        //    colors = listOf(0x2ecc71, 0xff726d, 0x18e7ff, 0xff00ff),
        //    emitter = Emitter(duration = 2, TimeUnit.SECONDS).perSecond(30),
        //    position = Position.Relative(0.0, 0.4)
        //)
        //val parties = listOf(
        //    party,
        //    party.copy(
        //        angle = party.angle - 90,
        //        position = Position.Relative(1.0, 0.4)
        //    ),
        //)
        //binding.konfettiView.start(parties)
        val party = Party(
            speed = 0f,
            maxSpeed = 30f,
            damping = 0.9f,
            spread = 360, // default
            colors = listOf(0x2ecc71, 0xff726d, 0x18e7ff, 0xff00ff),
            position = Position.Relative(0.5, 0.4),
            emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(80),
            timeToLive = 2000L, // default
        )
        binding.konfettiView.start(party)
    }
}
