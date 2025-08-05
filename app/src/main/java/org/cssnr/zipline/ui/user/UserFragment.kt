package org.cssnr.zipline.ui.user

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.shape.CornerFamily
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cssnr.zipline.R
import org.cssnr.zipline.api.ServerApi
import org.cssnr.zipline.api.ServerApi.PatchUser
import org.cssnr.zipline.databinding.FragmentUserBinding
import org.cssnr.zipline.db.ServerDao
import org.cssnr.zipline.db.ServerDatabase
import org.cssnr.zipline.db.ServerEntity
import org.cssnr.zipline.db.UserDao
import org.cssnr.zipline.db.UserDatabase
import org.cssnr.zipline.db.UserEntity
import org.cssnr.zipline.db.UserRepository
import org.cssnr.zipline.log.debugLog
import java.io.File
import java.math.RoundingMode
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class UserFragment : Fragment() {

    private var _binding: FragmentUserBinding? = null
    private val binding get() = _binding!!

    private val viewModel: UserViewModel by activityViewModels()
    private val navController by lazy { findNavController() }
    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }

    private lateinit var filePickerLauncher: ActivityResultLauncher<Array<String>>

    companion object {
        const val LOG_TAG = "UserFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(LOG_TAG, "onCreateView: ${savedInstanceState?.size()}")
        _binding = FragmentUserBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }

    override fun onDestroyView() {
        Log.d(LOG_TAG, "onDestroyView")
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(LOG_TAG, "onViewCreated: ${savedInstanceState?.size()}")

        val ctx = requireContext()

        //val dateFormat = DateFormat.getDateFormat(ctx)
        //val timeFormat = DateFormat.getTimeFormat(ctx)
        //val customFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

        val savedUrl = preferences.getString("ziplineUrl", null).toString()
        binding.headingHost.text = savedUrl.toUri().host

        viewModel.user.observe(viewLifecycleOwner) { user ->
            Log.i(LOG_TAG, "viewModel.user.observe - user: $user")
            _binding?.headingName?.text = user.username
            _binding?.helloText?.text = ctx.getString(R.string.user_welcome_text, user.username)
            _binding?.userId?.text = user.id

            //_binding?.userCreatedAt?.text =
            //    DateFormat.getTimeFormat(ctx).format(Date.from(Instant.parse(user.createdAt)))

            //val updatedAt = Date.from(Instant.parse(user.updatedAt))
            //_binding?.userUpdatedAt?.text =
            //    "${dateFormat.format(updatedAt)} ${timeFormat.format(updatedAt)}"

            //_binding?.userUpdatedAt?.text =
            //    customFormat.format(Date.from(Instant.parse(user.updatedAt)))

            _binding?.userCreatedAt?.text =
                ZonedDateTime.parse(user.createdAt).withZoneSameInstant(ZoneId.systemDefault())
                    .format(dateTimeFormat)
            _binding?.userUpdatedAt?.text =
                ZonedDateTime.parse(user.updatedAt).withZoneSameInstant(ZoneId.systemDefault())
                    .format(dateTimeFormat)
        }

        viewModel.server.observe(viewLifecycleOwner) { server ->
            Log.i(LOG_TAG, "viewModel.server.observe - server: $server")
            binding.statsFilesCount.text = server.filesUploaded.toString()

            binding.statsFilesSize.text =
                Formatter.formatShortFileSize(context, server.storageUsed?.toLong() ?: 0)

            binding.statsFileAvg.text =
                Formatter.formatShortFileSize(context, server.avgStorageUsed?.toLong() ?: 0)

            binding.statsFileFav.text = server.favoriteFiles.toString()

            val avgViews = server.avgViews ?: 0.0
            val viewsRounded = avgViews.toBigDecimal().setScale(4, RoundingMode.HALF_UP).toDouble()
            binding.statsFileAvgViews.text = if (avgViews == viewsRounded) {
                avgViews.toString()
            } else {
                String.format(Locale.getDefault(), "%.4f", avgViews)
            }

            binding.statsUrls.text = server.urlsCreated.toString()

            //// TODO: Need to save stat updatedAt from server response and not Long...
            //_binding?.statsUpdatedAt?.text =
            //    ZonedDateTime.parse(server.updatedAt).withZoneSameInstant(ZoneId.systemDefault())
            //        .format(dateTimeFormat)
        }

        lifecycleScope.launch {
            val userDao: UserDao = UserDatabase.getInstance(ctx).userDao()
            val userEntity: UserEntity? = userDao.getUserByUrl(url = savedUrl)
            Log.d(LOG_TAG, "userEntity: $userEntity")
            if (userEntity != null) {
                viewModel.user.value = userEntity
            }

            val serverDao: ServerDao = ServerDatabase.getInstance(ctx).serverDao()
            val server: ServerEntity? = serverDao.get(savedUrl)
            Log.d(LOG_TAG, "server: $server")
            if (server != null) {
                viewModel.server.value = server
            }
        }

        val radius = ctx.resources.getDimension(R.dimen.user_page_avatar)
        binding.appIcon.setShapeAppearanceModel(
            binding.appIcon.shapeAppearanceModel.toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, radius).build()
        )

        val avatarFile = File(ctx.filesDir, "avatar.png")
        if (avatarFile.exists()) {
            Log.i(LOG_TAG, "GLIDE LOAD - binding.appIcon: $avatarFile")
            Glide.with(binding.appIcon).load(avatarFile)
                .signature(ObjectKey(avatarFile.lastModified())).into(binding.appIcon)
        }

        setFragmentResultListener("CropFragment") { requestKey, bundle ->
            Log.d(LOG_TAG, "CropFragment: $bundle")
            val fileName = bundle.getString("fileName") ?: return@setFragmentResultListener
            Log.d(LOG_TAG, "fileName: $fileName")

            val newFile = File(ctx.filesDir, fileName)
            Log.d(LOG_TAG, "newFile: $newFile")

            val base64String = Base64.encodeToString(newFile.readBytes(), Base64.NO_WRAP)
            Log.d(LOG_TAG, "base64String: ${base64String.take(100)}")

            val api = ServerApi(ctx, savedUrl)
            lifecycleScope.launch {
                val user = api.editUser(PatchUser(avatar = "data:image/png;base64,$base64String"))
                Log.d(LOG_TAG, "user: $user")
                if (user != null && newFile.exists()) {
                    // TODO: Verify result
                    val result = newFile.renameTo(avatarFile)
                    Log.d(LOG_TAG, "renameTo result: $result")

                    Glide.with(binding.appIcon).load(avatarFile)
                        .signature(ObjectKey(avatarFile.lastModified())).into(binding.appIcon)
                    val headerImage = requireActivity().findViewById<ImageView>(R.id.header_image)
                    Glide.with(headerImage).load(avatarFile)
                        .signature(ObjectKey(avatarFile.lastModified())).into(headerImage)

                    Snackbar.make(view, "Avatar Updated.", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(view, "Error Updating Avatar!", Snackbar.LENGTH_LONG)
                        .setTextColor("#D32F2F".toColorInt()).show()
                }
            }
        }

        filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                Log.d("filePickerLauncher", "uris: $uri")
                if (uri != null) {
                    val bundle = bundleOf("uri" to uri.toString())
                    navController.navigate(R.id.nav_item_crop, bundle)
                }
                //} else {
                //    Snackbar.make(view, "No Image Selected.", Snackbar.LENGTH_SHORT).show()
                //}
                // NOTE: Uses setFragmentResultListener
            }

        binding.updateProfile.setOnClickListener {
            Log.d(LOG_TAG, "binding.updateProfile.setOnClickListener")
            binding.updateProfile.isEnabled = false
            lifecycleScope.launch {
                val user = requireActivity().getUser()
                Log.d(LOG_TAG, "binding.updateProfile - user: $user")
                viewModel.user.value = user
                _binding?.updateProfile?.isEnabled = true
                Snackbar.make(view, "Profile Refreshed from Server.", Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.updateStats.setOnClickListener {
            Log.d(LOG_TAG, "binding.updateStats.setOnClickListener")
            binding.updateStats.isEnabled = false
            lifecycleScope.launch {
                val serverEntity = ctx.updateStats()
                Log.d(LOG_TAG, "binding.updateStats - serverEntity: $serverEntity")
                viewModel.server.value = serverEntity
                _binding?.updateStats?.isEnabled = true
                Snackbar.make(view, "Stats Refreshed from Server.", Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.updateAvatar.setOnClickListener {
            Log.d(LOG_TAG, "binding.updateAvatar.setOnClickListener")
            binding.updateAvatar.isEnabled = false
            lifecycleScope.launch {
                val file = requireActivity().getAvatar()
                Log.d(LOG_TAG, "binding.updateAvatar: file: $file")
                _binding?.let {
                    if (file?.exists() == true) {
                        Log.i(LOG_TAG, "GLIDE LOAD - binding.appIcon: $file")
                        Glide.with(it.appIcon).load(file).signature(ObjectKey(file.lastModified()))
                            .into(it.appIcon)
                    } else {
                        Glide.with(it.appIcon).load(R.mipmap.ic_launcher_round).into(it.appIcon)
                    }
                    it.updateAvatar.isEnabled = true
                    Snackbar.make(view, "Avatar Refreshed from Server.", Snackbar.LENGTH_SHORT)
                        .show()
                }
            }
        }

        binding.changeAvatar.setOnClickListener {
            Log.d(LOG_TAG, "binding.changeAvatar.setOnClickListener")
            filePickerLauncher.launch(arrayOf("image/*"))
            // NOTE: Uses filePickerLauncher
        }

        binding.shareAvatar.setOnClickListener {
            Log.d(LOG_TAG, "binding.shareAvatar.setOnClickListener")
            // TODO: Cleanup this logic...
            val dir = File(ctx.filesDir, "share")
            dir.mkdirs()
            val shareFile = File(dir, "avatar.png")
            avatarFile.copyTo(shareFile, true)
            if (shareFile.exists()) {
                val uri =
                    FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", shareFile)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(intent, "Share avatar"))
            }
        }

        class MyOnClickListener : View.OnClickListener {
            override fun onClick(v: View) {
                Log.d(LOG_TAG, "MyOnClickListener: $v")
            }
        }

        binding.testBtn.setOnClickListener {
            Log.d(LOG_TAG, "binding.testBtn.setOnClickListener")
            Snackbar.make(view, "Test Snackbar Action Message.", Snackbar.LENGTH_LONG)
                .setAction("Action", MyOnClickListener()).show()
        }
    }
}


suspend fun Context.updateStats(): ServerEntity? {
    Log.d("updateStats", "updateStats")
    val preferences = PreferenceManager.getDefaultSharedPreferences(this)
    val savedUrl = preferences.getString("ziplineUrl", null).toString()
    Log.d("updateStats", "savedUrl: $savedUrl")
    if (preferences.getString("ziplineToken", null).isNullOrEmpty()) {
        Log.i("updateStats", "ziplineToken: isNullOrEmpty")
        return null
    }
    val api = ServerApi(this, savedUrl)
    val statsResponse = api.stats()
    Log.d("updateStats", "statsResponse: $statsResponse")
    debugLog("AppWorker: updateStats: response code: ${statsResponse.code()}")
    if (statsResponse.isSuccessful) {
        val stats = statsResponse.body()
        Log.d("updateStats", "stats: $stats")
        if (stats != null) {
            val dao: ServerDao = ServerDatabase.getInstance(this).serverDao()
            val serverEntity = ServerEntity(
                url = savedUrl,
                filesUploaded = stats.filesUploaded,
                favoriteFiles = stats.favoriteFiles,
                views = stats.views,
                avgViews = stats.avgViews,
                storageUsed = stats.storageUsed,
                avgStorageUsed = stats.avgStorageUsed,
                urlsCreated = stats.urlsCreated,
                urlViews = stats.urlViews,
                updatedAt = System.currentTimeMillis(),
            )
            dao.upsert(serverEntity)
            Log.d("updateStats", "dao.upsert: DONE")
            return serverEntity
        }
    }
    return null
}


suspend fun Activity.getAvatar(): File? {
    val preferences = PreferenceManager.getDefaultSharedPreferences(this)
    val savedUrl = preferences.getString("ziplineUrl", null).toString()
    Log.d("getAvatar", "savedUrl: $savedUrl")

    val api = ServerApi(this, savedUrl)
    val avatar = api.avatar()
    Log.d("getAvatar", "avatar: ${avatar?.take(100)}")

    val file = File(filesDir, "avatar.png")
    if (avatar == null) {
        Log.d("getAvatar", "No Avatar Returned! Deleting File: $file")
        file.delete()
    } else {
        val base64Data = avatar.substringAfter("base64,", "")
        val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
        file.outputStream().use { it.write(imageBytes) }
        Log.d("getAvatar", "Saving Avatar to File: $file")
    }

    withContext(Dispatchers.Main) {
        Log.d("getAvatar", "Dispatchers.Main")
        val headerImage = findViewById<ImageView>(R.id.header_image)
        headerImage.let {
            if (file.exists()) {
                Log.i("getAvatar", "GLIDE LOAD - headerImage: $file")
                Glide.with(it).load(file).signature(ObjectKey(file.lastModified())).into(it)
            } else {
                Log.d("getAvatar", "Set Header Image: Default Drawable")
                Glide.with(it).load(R.mipmap.ic_launcher_round).into(it)
            }
        }
    }
    Log.d("getAvatar", "DONE - file: $file")
    return file
}


suspend fun Context.getUser(): UserEntity? {
    Log.d("getUser", "getUser")
    val preferences = PreferenceManager.getDefaultSharedPreferences(this)
    val savedUrl = preferences.getString("ziplineUrl", null).toString()
    Log.d("getUser", "savedUrl: $savedUrl")
    if (preferences.getString("ziplineToken", null).isNullOrEmpty()) {
        Log.d("getUser", "ziplineToken: isNullOrEmpty")
        return null
    }
    val api = ServerApi(this, savedUrl)
    val user = api.user()
    Log.d("getUser", "user: $user")
    debugLog("AppWorker: getUser: user: $user")
    if (user != null) {
        val repo = UserRepository(UserDatabase.getInstance(this).userDao())
        val userEntity: UserEntity = repo.updateUser(savedUrl, user)
        Log.d("getUser", "repo.getUser: DONE")
        return userEntity
    }
    return null
}
