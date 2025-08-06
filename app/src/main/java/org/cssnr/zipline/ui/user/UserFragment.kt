package org.cssnr.zipline.ui.user

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.CornerFamily
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cssnr.zipline.R
import org.cssnr.zipline.api.ServerApi
import org.cssnr.zipline.api.ServerApi.PatchUser
import org.cssnr.zipline.api.parseErrorBody
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

            val totpEnabled = user.totpSecret?.isNotEmpty() == true
            Log.i(LOG_TAG, "totpEnabled: $totpEnabled")
            binding.enableTotp.visibility = if (totpEnabled) View.GONE else View.VISIBLE
            binding.disableTotp.visibility = if (totpEnabled) View.VISIBLE else View.GONE

            binding.serverSettings.visibility =
                if (user.role == "ADMIN" || user.role == "SUPERADMIN") View.VISIBLE else View.GONE

            binding.headingName.text = user.username
            binding.helloText.text = ctx.getString(R.string.user_welcome_text, user.username)
            binding.userId.text = user.id

            //binding.userCreatedAt.text =
            //    DateFormat.getTimeFormat(ctx).format(Date.from(Instant.parse(user.createdAt)))

            //val updatedAt = Date.from(Instant.parse(user.updatedAt))
            //binding.userUpdatedAt.text =
            //    "${dateFormat.format(updatedAt)} ${timeFormat.format(updatedAt)}"

            //binding.userUpdatedAt.text =
            //    customFormat.format(Date.from(Instant.parse(user.updatedAt)))

            binding.userCreatedAt.text =
                ZonedDateTime.parse(user.createdAt).withZoneSameInstant(ZoneId.systemDefault())
                    .format(dateTimeFormat)
            binding.userUpdatedAt.text =
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
            binding.statsFileViews.text = server.views.toString()

            val avgViews = server.avgViews ?: 0.0
            val viewsRounded = avgViews.toBigDecimal().setScale(4, RoundingMode.HALF_UP).toDouble()
            binding.statsFileAvgViews.text = if (avgViews == viewsRounded) {
                avgViews.toString()
            } else {
                String.format(Locale.getDefault(), "%.4f", avgViews)
            }

            binding.statsUrls.text = server.urlsCreated.toString()

            //// TODO: Need to save stat updatedAt from server response and not Long...
            //binding.statsUpdatedAt.text =
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

        val radius = ctx.resources.getDimension(R.dimen.avatar_radius)
        binding.appIcon.setShapeAppearanceModel(
            binding.appIcon.shapeAppearanceModel.toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, radius).build()
        )

        val avatarFile = File(ctx.filesDir, "avatar.png")
        Log.d(LOG_TAG, "avatarFile: $avatarFile")
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
                val user = requireActivity().updateUserActivity()
                Log.d(LOG_TAG, "binding.updateProfile - user: $user")
                viewModel.user.value = user
                _binding?.updateProfile?.isEnabled = true
                Snackbar.make(view, "Profile Refreshed from Server.", Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.changeUsername.setOnClickListener {
            Log.d(LOG_TAG, "binding.changeUsername.setOnClickListener")
            ctx.changeUsernameDialog(view)
        }

        binding.changePassword.setOnClickListener {
            Log.d(LOG_TAG, "binding.changeUsername.setOnClickListener")
            ctx.changePasswordDialog(view)
        }

        binding.copyToken.setOnClickListener {
            Log.d(LOG_TAG, "binding.copyToken.setOnClickListener")
            val authToken = preferences.getString("ziplineToken", null)
            Log.d(LOG_TAG, "authToken: $authToken")
            if (authToken != null) {
                val clipboard = ctx.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Token", authToken))
                Snackbar.make(view, "Token Copied to Clipboard.", Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(view, "Token is null! This is a Problem!", Snackbar.LENGTH_LONG)
                    .setTextColor("#D32F2F".toColorInt()).show()
            }
        }

        binding.enableTotp.setOnClickListener {
            Log.d(LOG_TAG, "binding.enableTotp.setOnClickListener")
            //Snackbar.make(view, "Not Yet Implemented!", Snackbar.LENGTH_SHORT).show()
            lifecycleScope.launch {
                val api = ServerApi(ctx)
                val totpResponse = api.getTotpSecret()
                Log.d(LOG_TAG, "totpResponse: $totpResponse")
                if (totpResponse?.secret != null) {
                    // TODO: Cache the totpResponse.secret in the viewModel...
                    ctx.enableTotpDialog(view, totpResponse.secret)
                } else {
                    Snackbar.make(view, "Error Getting TOTP Secret!", Snackbar.LENGTH_LONG)
                        .setTextColor("#D32F2F".toColorInt()).show()
                }
            }
        }

        binding.disableTotp.setOnClickListener {
            Log.d(LOG_TAG, "binding.disableTotp.setOnClickListener")
            ctx.disableTotpDialog(view)
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
                val file = requireActivity().updateAvatar()
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

            if (!avatarFile.exists()) {
                Snackbar.make(view, "Avatar File Not Found!", Snackbar.LENGTH_LONG)
                    .setTextColor("#D32F2F".toColorInt()).show()
                return@setOnClickListener
            }

            // TODO: Determine how to deal with caching...
            //val timestamp = System.currentTimeMillis()
            //val shareFile = File(ctx.cacheDir, "avatar_$timestamp.png")
            val shareFile = File(ctx.cacheDir, "avatar.png")
            Log.d(LOG_TAG, "shareFile: $shareFile")
            avatarFile.copyTo(shareFile, true)

            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", shareFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                //putExtra(Intent.EXTRA_TITLE, "avatar.png")
                //putExtra(Intent.EXTRA_SUBJECT, "avatar.png")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(shareIntent, "Share Avatar"))
        }

        binding.logOutBtn.setOnClickListener {
            Log.d(LOG_TAG, "binding.logOutBtn.setOnClickListener")

            MaterialAlertDialogBuilder(ctx)
                .setTitle("Log Out")
                .setIcon(R.drawable.md_logout_24px)
                .setMessage("Log out of the application?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log Out") { _, _ ->
                    Log.d(LOG_TAG, "LOG OUT")
                    preferences.edit { remove("ziplineToken") }
                    val bundle = bundleOf("url" to savedUrl)
                    Log.d(LOG_TAG, "bundle: $bundle")
                    navController.navigate(
                        R.id.nav_item_login, bundle, NavOptions.Builder()
                            .setPopUpTo(navController.graph.id, true)
                            .build()
                    )
                }
                .show()
        }

        class MyOnClickListener : View.OnClickListener {
            override fun onClick(v: View) {
                Log.d(LOG_TAG, "MyOnClickListener: $v")
            }
        }

        binding.clearTemp.setOnClickListener {
            Log.d(LOG_TAG, "binding.clearTemp.setOnClickListener")
            MaterialAlertDialogBuilder(ctx)
                .setTitle("Clear Temporary Files")
                .setIcon(R.drawable.md_delete_sweep_24px)
                .setMessage("Delete temporary files in your temp directory.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm") { _, _ ->
                    Log.d(LOG_TAG, "Confirm")
                    lifecycleScope.launch {
                        val api = ServerApi(ctx)
                        val response = api.clearTemp()
                        if (response.isSuccessful) {
                            val body = response.body()
                            val status = body?.status.toString()
                            Log.d(LOG_TAG, "status: $status")
                            Snackbar.make(view, status, Snackbar.LENGTH_SHORT).show()
                        } else {
                            val errorResponse = response.parseErrorBody(ctx) ?: "Unknown Error"
                            Log.d(LOG_TAG, "errorResponse: $errorResponse")
                            Snackbar.make(view, errorResponse, Snackbar.LENGTH_LONG)
                                .setTextColor("#D32F2F".toColorInt()).show()
                        }
                    }
                }
                .show()
        }

        binding.clearZeros.setOnClickListener {
            Log.d(LOG_TAG, "binding.clearZeros.setOnClickListener")
            MaterialAlertDialogBuilder(ctx)
                .setTitle("Clear Zero Byte Files")
                .setIcon(R.drawable.md_delete_sweep_24px)
                .setMessage("Delete files with zero-byte size from your storage/database.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm") { _, _ ->
                    Log.d(LOG_TAG, "Confirm")
                    lifecycleScope.launch {
                        val api = ServerApi(ctx)
                        val response = api.clearZeros()
                        if (response.isSuccessful) {
                            val body = response.body()
                            val status = body?.status.toString()
                            Log.d(LOG_TAG, "status: $status")
                            Snackbar.make(view, status, Snackbar.LENGTH_SHORT).show()
                        } else {
                            val errorResponse = response.parseErrorBody(ctx) ?: "Unknown Error"
                            Log.d(LOG_TAG, "errorResponse: $errorResponse")
                            Snackbar.make(view, errorResponse, Snackbar.LENGTH_LONG)
                                .setTextColor("#D32F2F".toColorInt()).show()
                        }
                    }
                }
                .show()
        }

        binding.genThumbnails.setOnClickListener {
            Log.d(LOG_TAG, "binding.genThumbnails.setOnClickListener")
            MaterialAlertDialogBuilder(ctx)
                .setTitle("Thumbnail Generation")
                .setIcon(R.drawable.md_videocam_24px)
                .setMessage("Trigger the video thumbnail generation task.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm") { _, _ ->
                    Log.d(LOG_TAG, "Confirm")
                    lifecycleScope.launch {
                        val api = ServerApi(ctx)
                        val response = api.thumbnails()
                        if (response.isSuccessful) {
                            val body = response.body()
                            val status = body?.status.toString()
                            Log.d(LOG_TAG, "status: $status")
                            Snackbar.make(view, status, Snackbar.LENGTH_SHORT).show()
                        } else {
                            val errorResponse = response.parseErrorBody(ctx) ?: "Unknown Error"
                            Log.d(LOG_TAG, "errorResponse: $errorResponse")
                            Snackbar.make(view, errorResponse, Snackbar.LENGTH_LONG)
                                .setTextColor("#D32F2F".toColorInt()).show()
                        }
                    }
                }
                .show()
        }

        binding.testBtn.setOnClickListener {
            Log.d(LOG_TAG, "binding.testBtn.setOnClickListener")
            Snackbar.make(view, "Test Snackbar Action Message.", Snackbar.LENGTH_LONG)
                .setAction("Action", MyOnClickListener()).show()
        }
    }

    private fun Context.changeUsernameDialog(parentView: View) {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_username, null)
        val input = view.findViewById<EditText>(R.id.username_text)

        val savedUrl = preferences.getString("ziplineUrl", null) ?: return
        Log.d("changeUsernameDialog", "savedUrl: $savedUrl")

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnPositive.setOnClickListener {
                btnPositive.isEnabled = false
                val newValue = input.text.toString().trim()
                Log.d("changeUsernameDialog", "newValue: $newValue")
                if (newValue.isEmpty()) {
                    input.error = "Required"
                    input.requestFocus()
                } else if (viewModel.user.value?.username == newValue) {
                    input.error = "Not Changed"
                    input.requestFocus()
                } else {
                    val api = ServerApi(this)
                    val patchUser = PatchUser(username = newValue)
                    val dao: UserDao = UserDatabase.getInstance(this).userDao()
                    lifecycleScope.launch {
                        val newUser = api.editUser(patchUser)
                        Log.d("changeUsernameDialog", "newUser: $newUser")
                        if (newUser != null) {
                            val userRepository = UserRepository(dao)
                            val user = userRepository.updateUser(savedUrl, newUser)
                            Log.d("changeUsernameDialog", "user: $user")
                            viewModel.user.value = user
                            val message = "Username Changed to ${user.username}"
                            Snackbar.make(parentView, message, Snackbar.LENGTH_SHORT).show()
                            dialog.dismiss()
                        } else {
                            // TODO: Better Handle Errors here...
                            input.error = "Error Changing Username"
                            input.requestFocus()
                        }
                    }
                }
                btnPositive.isEnabled = true
            }
            input.setText(viewModel.user.value?.username ?: "")
            input.setSelection(input.text.length)
            input.requestFocus()
        }

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Change Username") { _, _ -> }
        dialog.show()
    }


    private fun Context.changePasswordDialog(parentView: View) {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_password, null)
        val input = view.findViewById<EditText>(R.id.password_text)
        val input2 = view.findViewById<EditText>(R.id.password_text2)

        val savedUrl = preferences.getString("ziplineUrl", null) ?: return
        Log.d("changePasswordDialog", "savedUrl: $savedUrl")

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnPositive.setOnClickListener {
                btnPositive.isEnabled = false
                val newValue = input.text.toString()
                Log.d("changePasswordDialog", "newValue: $newValue")
                val newValue2 = input2.text.toString()
                Log.d("changePasswordDialog", "newValue2: $newValue2")
                if (newValue.isEmpty()) {
                    input.error = "Required"
                    input.requestFocus()
                } else if (newValue.length < 2) {
                    input.error = "Must be 2 Characters"
                    input.requestFocus()
                } else if (newValue != newValue2) {
                    input2.error = "Does Not Match"
                    input2.requestFocus()
                } else {
                    val api = ServerApi(this)
                    val patchUser = PatchUser(password = newValue)
                    val dao: UserDao = UserDatabase.getInstance(this).userDao()
                    lifecycleScope.launch {
                        val newUser = api.editUser(patchUser)
                        Log.d("changePasswordDialog", "newUser: $newUser")
                        if (newUser != null) {
                            val userRepository = UserRepository(dao)
                            val user = userRepository.updateUser(savedUrl, newUser)
                            Log.d("changePasswordDialog", "user: $user")
                            viewModel.user.value = user
                            val message = "Password Changed."
                            Snackbar.make(parentView, message, Snackbar.LENGTH_SHORT).show()
                            dialog.dismiss()
                        } else {
                            // TODO: Better Handle Errors here...
                            input.error = "Error Changing Password"
                            input.requestFocus()
                        }
                    }
                }
                btnPositive.isEnabled = true
            }
            input.requestFocus()
        }

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Change Password") { _, _ -> }
        dialog.show()
    }


    private fun Context.disableTotpDialog(parentView: View) {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_totp_disable, null)
        val input = view.findViewById<EditText>(R.id.totp_code)

        val savedUrl = preferences.getString("ziplineUrl", null) ?: return
        Log.d("disableTotpDialog", "savedUrl: $savedUrl")

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnPositive.setOnClickListener {
                btnPositive.isEnabled = false
                val totpCode = input.text.toString()
                Log.d("disableTotpDialog", "totpCode: $totpCode")
                if (totpCode.isEmpty()) {
                    input.error = "Required"
                    input.requestFocus()
                } else if (totpCode.length < 6) {
                    input.error = "Must be 6 Digits"
                    input.requestFocus()
                } else {
                    val api = ServerApi(this)
                    val dao: UserDao = UserDatabase.getInstance(this).userDao()
                    lifecycleScope.launch {
                        val userResponse = api.disableTotp(totpCode)
                        Log.d("disableTotpDialog", "userResponse: $userResponse")
                        if (userResponse != null) {
                            val userRepository = UserRepository(dao)
                            val user = userRepository.updateUser(savedUrl, userResponse)
                            Log.d("disableTotpDialog", "user: $user")
                            viewModel.user.value = user
                            val message = "TOTP Disabled!"
                            Snackbar.make(parentView, message, Snackbar.LENGTH_SHORT).show()
                            dialog.dismiss()
                        } else {
                            // TODO: Better Handle Errors here...
                            input.error = "Error Disabling TOTP"
                            input.requestFocus()
                        }
                    }
                }
                btnPositive.isEnabled = true
            }
            input.requestFocus()
        }

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Disable TOTP") { _, _ -> }
        dialog.show()
    }


    private fun Context.enableTotpDialog(parentView: View, totpSecret: String) {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_totp_enable, null)
        val secretLayout = view.findViewById<FrameLayout>(R.id.secret_layout)
        val secretTextView = view.findViewById<TextView>(R.id.totp_secret)
        val openAuthLink = view.findViewById<Button>(R.id.open_auth_link)
        val copySecretBtn = view.findViewById<ImageView>(R.id.copy_secret_btn)
        val input = view.findViewById<EditText>(R.id.totp_code)

        Log.d("enableTotpDialog", "totpSecret: $totpSecret")
        secretTextView.text = totpSecret

        openAuthLink.setOnClickListener {
            Log.d("enableTotpDialog", "openAuthLink.setOnClickListener")
            val username = viewModel.user.value?.username ?: "unknown"
            val uri =
                "otpauth://totp/Zipline:${username}?secret=${totpSecret}&issuer=Zipline".toUri()
            Log.d("enableTotpDialog", "uri: $uri")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }

        copySecretBtn.setOnClickListener {
            Log.d("enableTotpDialog", "copySecretBtn.setOnClickListener")
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Token", totpSecret))
            // TODO: Snake Bar in an AlertDialog does does not drop to bottom due to panel constraint
            //Snackbar.make(view, "Copied", Snackbar.LENGTH_SHORT)
            //    .setAnchorView(secretLayout).show()
        }

        val savedUrl = preferences.getString("ziplineUrl", null) ?: return
        Log.d("enableTotpDialog", "savedUrl: $savedUrl")

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnPositive.setOnClickListener {
                btnPositive.isEnabled = false
                val totpCode = input.text.toString()
                Log.d("enableTotpDialog", "totpCode: $totpCode")
                if (totpCode.isEmpty()) {
                    input.error = "Required"
                    input.requestFocus()
                } else if (totpCode.length < 6) {
                    input.error = "Must be 6 Digits"
                    input.requestFocus()
                } else {
                    val api = ServerApi(this)
                    val dao: UserDao = UserDatabase.getInstance(this).userDao()
                    lifecycleScope.launch {
                        val userResponse = api.enableTotp(totpSecret, totpCode)
                        Log.d("enableTotpDialog", "userResponse: $userResponse")
                        if (userResponse != null) {
                            val userRepository = UserRepository(dao)
                            val user = userRepository.updateUser(savedUrl, userResponse)
                            Log.d("enableTotpDialog", "user: $user")
                            viewModel.user.value = user
                            val message = "TOTP Enabled!"
                            Snackbar.make(parentView, message, Snackbar.LENGTH_SHORT).show()
                            dialog.dismiss()
                        } else {
                            // TODO: Better Handle Errors here...
                            input.error = "Error Enabling TOTP"
                            input.requestFocus()
                        }
                    }
                }
                btnPositive.isEnabled = true
            }
            input.requestFocus()
        }

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Enable TOTP") { _, _ -> }
        dialog.show()
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
    debugLog("updateStats: response: ${statsResponse.code()}")
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


suspend fun Context.updateUser(): UserEntity? {
    Log.d("updateUser", "updateUser")
    val preferences = PreferenceManager.getDefaultSharedPreferences(this)
    val savedUrl = preferences.getString("ziplineUrl", null).toString()
    Log.d("updateUser", "savedUrl: $savedUrl")
    if (preferences.getString("ziplineToken", null).isNullOrEmpty()) {
        Log.d("updateUser", "ziplineToken: isNullOrEmpty")
        return null
    }
    val api = ServerApi(this, savedUrl)
    val user = api.user() ?: return null
    Log.d("updateUser", "user: $user")
    debugLog("updateUser: $user")
    val repo = UserRepository(UserDatabase.getInstance(this).userDao())
    val userEntity: UserEntity = repo.updateUser(savedUrl, user)
    Log.d("updateUser", "repo.updateUser: DONE")
    return userEntity
}

suspend fun Activity.updateUserActivity(): UserEntity? {
    Log.d("updateUserActivity", "calling: updateUser()")
    val user = updateUser()
    if (user != null) {
        withContext(Dispatchers.Main) {
            Log.d("updateUserActivity", "Dispatchers.Main")
            val headerUsername = findViewById<TextView>(R.id.header_username)
            headerUsername?.text = user.username
        }
    }
    return user
}


suspend fun Activity.updateAvatar(): File? {
    val preferences = PreferenceManager.getDefaultSharedPreferences(this)
    val savedUrl = preferences.getString("ziplineUrl", null).toString()
    Log.d("updateAvatar", "savedUrl: $savedUrl")

    val api = ServerApi(this, savedUrl)
    val avatar = api.avatar()
    Log.d("updateAvatar", "avatar: ${avatar?.take(100)}")

    val file = File(filesDir, "avatar.png")
    if (avatar == null) {
        Log.d("updateAvatar", "No Avatar Returned! Deleting File: $file")
        file.delete()
    } else {
        val base64Data = avatar.substringAfter("base64,", "")
        val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
        file.outputStream().use { it.write(imageBytes) }
        Log.d("updateAvatar", "Saving Avatar to File: $file")
    }

    withContext(Dispatchers.Main) {
        Log.d("updateAvatar", "Dispatchers.Main")
        val headerImage = findViewById<ImageView>(R.id.header_image)
        headerImage.let {
            if (file.exists()) {
                Log.i("updateAvatar", "GLIDE LOAD - headerImage: $file")
                Glide.with(it).load(file).signature(ObjectKey(file.lastModified())).into(it)
            } else {
                Log.d("updateAvatar", "Set Header Image: Default Drawable")
                Glide.with(it).load(R.mipmap.ic_launcher_round).into(it)
            }
        }
    }
    Log.d("updateAvatar", "DONE - file: $file")
    return file
}

//fun Context.copyToClipboard(text: String, label: String = "Text") {
//    Log.d("copyToClipboard", "text: $text")
//    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
//    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
//}
