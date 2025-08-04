package org.cssnr.zipline.ui.user

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.toColorInt
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cssnr.zipline.R
import org.cssnr.zipline.api.ServerApi
import org.cssnr.zipline.api.ServerApi.PatchUser
import org.cssnr.zipline.databinding.FragmentUserBinding
import org.cssnr.zipline.db.UserDao
import org.cssnr.zipline.db.UserDatabase
import org.cssnr.zipline.db.UserEntity
import org.cssnr.zipline.db.UserRepository
import org.cssnr.zipline.log.debugLog
import java.io.File

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

        val savedUrl = preferences.getString("ziplineUrl", null).toString()

        viewModel.user.observe(viewLifecycleOwner) { user ->
            Log.i(LOG_TAG, "viewModel.user.observe - user: $user")
            _binding?.headingName?.text = user.username
            _binding?.helloText?.text = "Hello ${user.username}! Welcome to Zipline Upload."
            _binding?.userId?.text = user.id
            _binding?.userCreatedAt?.text = user.createdAt
            _binding?.userUpdatedAt?.text = user.updatedAt
        }

        lifecycleScope.launch {
            val dao: UserDao = UserDatabase.getInstance(ctx).userDao()
            val userEntity: UserEntity = dao.getUserByUrl(url = savedUrl) ?: return@launch
            Log.d(LOG_TAG, "lifecycleScope.launch - userEntity: $userEntity")
            viewModel.user.value = userEntity
        }

        // TODO: file is being shadowed...
        val file = File(ctx.filesDir, "avatar.png")
        if (file.exists()) {
            Log.i(LOG_TAG, "GLIDE LOAD - binding.appIcon: $file")
            Glide.with(binding.appIcon).load(file).signature(ObjectKey(file.lastModified()))
                .into(binding.appIcon)
        }

        binding.updateProfile.setOnClickListener {
            Log.d(LOG_TAG, "binding.updateProfile.setOnClickListener")
            binding.updateProfile.isEnabled = false
            lifecycleScope.launch {
                val user = requireActivity().getUser()
                Log.d(LOG_TAG, "binding.updateProfile - user: $user")
                viewModel.user.value = user
                _binding?.updateAvatar?.isEnabled = false
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
                        //
                        Glide.with(it.appIcon).load(file).signature(ObjectKey(file.lastModified()))
                            .into(it.appIcon)
                    } else {
                        Glide.with(it.appIcon).load(R.mipmap.ic_launcher_round).into(it.appIcon)
                    }
                    it.updateAvatar.isEnabled = true
                }
            }
        }

        // NOTE: Begin Test Button

        class MyOnClickListener : View.OnClickListener {
            override fun onClick(v: View) {
                Log.d(LOG_TAG, "MyOnClickListener: $v")
            }
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
                    newFile.renameTo(file)

                    Glide.with(binding.appIcon).load(file).signature(ObjectKey(file.lastModified()))
                        .into(binding.appIcon)
                    val headerImage = requireActivity().findViewById<ImageView>(R.id.header_image)
                    Glide.with(headerImage).load(file).signature(ObjectKey(file.lastModified()))
                        .into(headerImage)

                    Snackbar.make(view, "Image Crapped.", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(view, "Crap! Error!", Snackbar.LENGTH_SHORT)
                        .setTextColor("#D32F2F".toColorInt()).show()
                }
            }
        }

        filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                Log.d("filePickerLauncher", "uris: $uri")
                val bundle = bundleOf("uri" to uri.toString())
                navController.navigate(R.id.nav_item_crop, bundle)
            }

        binding.testBtn.setOnClickListener {
            Log.d(LOG_TAG, "binding.testBtn.setOnClickListener")
            //Snackbar.make(view, "AI is Dedrater.", Snackbar.LENGTH_SHORT).show()

            //Snackbar.make(view, "AI is Dedrater.", Snackbar.LENGTH_SHORT)
            //    .setAction("Action", MyOnClickListener()).show()

            filePickerLauncher.launch(arrayOf("image/*"))
        }
    }
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
    applicationContext.debugLog("AppWorker: getUser: user: $user")
    if (user != null) {
        val repo = UserRepository(UserDatabase.getInstance(this).userDao())
        val userEntity: UserEntity = repo.updateUser(savedUrl, user)
        Log.d("getUser", "repo.getUser: DONE")
        return userEntity
    }
    return null
}
