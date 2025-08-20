package org.cssnr.zipline.ui.upload

import UploadOptions
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cssnr.zipline.R
import org.cssnr.zipline.api.ServerApi
import org.cssnr.zipline.databinding.FragmentTextBinding
import org.cssnr.zipline.ui.dialogs.FolderFragment
import org.cssnr.zipline.ui.dialogs.UploadOptionsDialog

class TextFragment : Fragment() {

    private var _binding: FragmentTextBinding? = null
    private val binding get() = _binding!!

    private val viewModel: UploadViewModel by activityViewModels()

    private val navController by lazy { findNavController() }
    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("TextFragment", "onCreateView: $savedInstanceState")
        _binding = FragmentTextBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }

    override fun onDestroyView() {
        Log.d("TextFragment", "onDestroyView")
        super.onDestroyView()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        Log.d("Text[onStart]", "onStart - Hide UI")
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav).visibility = View.GONE
    }

    override fun onStop() {
        Log.d("Text[onStop]", "onStop - Show UI")
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav).visibility =
            View.VISIBLE
        super.onStop()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("Text[onViewCreated]", "savedInstanceState: $savedInstanceState")
        Log.d("Text[onViewCreated]", "arguments: $arguments")

        if (arguments?.getBoolean("optionsCleared") != true) {
            Log.i("Upload[onViewCreated]", "New Upload - null viewModel.uploadOptions")
            viewModel.uploadOptions.value = null
            arguments?.putBoolean("optionsCleared", true)
        }

        val ctx = requireContext()

        val savedUrl = preferences.getString("ziplineUrl", null)
        Log.d("Text[onViewCreated]", "savedUrl: $savedUrl")
        val authToken = preferences.getString("ziplineToken", null)
        Log.d("Text[onViewCreated]", "authToken: ${authToken?.take(24)}...")
        if (savedUrl.isNullOrEmpty() || authToken.isNullOrEmpty()) {
            Log.e("Text[onViewCreated]", "savedUrl is null")
            Toast.makeText(ctx, "Missing URL!", Toast.LENGTH_LONG).show()
            navController.navigate(
                R.id.nav_item_login, null, NavOptions.Builder()
                    .setPopUpTo(navController.graph.id, true)
                    .build()
            )
            return
        }

        val extraText = arguments?.getString("text")?.trim() ?: ""
        Log.d("Text[onViewCreated]", "extraText: ${extraText.take(100)}")

        if (extraText.isEmpty()) {
            // TODO: Better Handle this Error
            Log.w("Text[onViewCreated]", "extraText is null")
            Toast.makeText(ctx, "No Text to Process!", Toast.LENGTH_LONG).show()
            //return
        }

        // Set Initial UploadOptions
        if (viewModel.uploadOptions.value == null) {
            viewModel.uploadOptions.value = UploadOptions(
                folderId = preferences.getString("file_folder_id", null),
                deletesAt = preferences.getString("file_deletes_at", null),
                compression = preferences.getInt("file_compression", 0),
            )
        }
        Log.i("Upload[onViewCreated]", "uploadOptions: ${viewModel.uploadOptions.value}")

        binding.textContent.setText(extraText)

        // Upload Options Button
        binding.uploadOptions.setOnClickListener {
            setFragmentResultListener("upload_options_result") { _, bundle ->
                Log.i("uploadOptions", "bundle: $bundle")
                val filePassword = bundle.getString("filePassword")
                val deletesAt = bundle.getString("deletesAt")
                val maxViews = bundle.getInt("maxViews")
                val compression = bundle.getInt("compression")
                Log.d("uploadOptions", "filePassword: $filePassword")
                Log.d("uploadOptions", "deletesAt: $deletesAt")
                Log.d("uploadOptions", "maxViews: $maxViews")
                Log.d("uploadOptions", "compression: $compression")
                viewModel.uploadOptions.value?.password = filePassword
                viewModel.uploadOptions.value?.deletesAt = deletesAt
                viewModel.uploadOptions.value?.maxViews = if (maxViews == 0) null else maxViews
                viewModel.uploadOptions.value?.compression = compression
            }
            val uploadOptionsDialog =
                UploadOptionsDialog.newInstance(viewModel.uploadOptions.value!!)
            uploadOptionsDialog.show(parentFragmentManager, "UploadOptions")
        }

        // Options Button
        binding.optionsButton.setOnClickListener {
            Log.d("optionsButton", "setOnClickListener")
            navController.navigate(R.id.nav_item_settings, bundleOf("hide_bottom_nav" to true))
        }

        // Folder Button
        binding.folderButton.setOnClickListener {
            Log.d("folderButton", "setOnClickListener")
            setFragmentResultListener("folder_fragment_result") { _, bundle ->
                val folderId = bundle.getString("folderId")
                val folderName = bundle.getString("folderName")
                Log.d("folderButton", "folderId: $folderId")
                Log.d("folderButton", "folderName: $folderName")
                viewModel.uploadOptions.value?.folderId = folderId ?: ""
            }

            Log.i("folderButton", "folderId: ${viewModel.uploadOptions.value?.folderId}")

            lifecycleScope.launch {
                val folderFragment = FolderFragment()
                // NOTE: Not setting uploadOptions here. DUPLICATION: upload, uploadMulti, text
                folderFragment.setFolderData(ctx, viewModel.uploadOptions.value?.folderId)
                folderFragment.show(parentFragmentManager, "FolderFragment")
            }
        }

        // Upload Button
        binding.uploadButton.setOnClickListener {
            val finalText = binding.textContent.text.toString().trim()
            Log.d("uploadButton", "finalText: $finalText")
            val fileNameInput = binding.vanityName.text.toString().trim()
            Log.d("uploadButton", "fileNameInput: $fileNameInput")
            val fileName = when {
                fileNameInput.isEmpty() -> "paste.txt" // TODO: Add Default Name Option...
                !fileNameInput.contains('.') -> "${fileNameInput}.txt"
                else -> fileNameInput
            }
            Log.d("uploadButton", "fileName: $fileName")
            ctx.processUpload(finalText, fileName)
        }
    }

    // TODO: DUPLICATION: UploadFragment.processUpload
    private fun Context.processUpload(textContent: String, fileName: String) {
        Log.d("processUpload", "textContent: $textContent")
        Log.d("processUpload", "fileName: $fileName")

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val savedUrl = preferences.getString("ziplineUrl", null)
        Log.d("processUpload", "savedUrl: $savedUrl")
        val authToken = preferences.getString("ziplineToken", null)
        Log.d("processUpload", "authToken: ${authToken?.take(24)}...")
        val shareUrl = preferences.getBoolean("share_after_upload", true)
        Log.d("processShort", "shareUrl: $shareUrl")

        if (savedUrl == null || authToken == null) {
            // TODO: Show settings dialog here...
            Log.w("processUpload", "Missing OR savedUrl/authToken/fileName")
            Toast.makeText(this, getString(R.string.tst_no_url), Toast.LENGTH_SHORT)
                .show()
            return
        }
        val inputStream = textContent.byteInputStream()
        val api = ServerApi(this)
        Log.d("processUpload", "api: $api")
        Toast.makeText(this, getString(R.string.tst_uploading_file), Toast.LENGTH_SHORT)
            .show()
        lifecycleScope.launch {
            try {
                // TODO: Implement editRequest
                val response = api.upload(fileName, inputStream, viewModel.uploadOptions.value!!)
                Log.d("processUpload", "response: $response")
                if (response.isSuccessful) {
                    val uploadResponse = response.body()
                    Log.d("processUpload", "uploadResponse: $uploadResponse")
                    withContext(Dispatchers.Main) {
                        if (uploadResponse != null) {
                            logFileUpload(true, "Text Upload")
                            this@processUpload.copyToClipboard(uploadResponse.files.first().url)
                            val bundle = bundleOf("url" to uploadResponse.files.first().url)
                            navController.navigate(
                                R.id.nav_item_home, bundle, NavOptions.Builder()
                                    .setPopUpTo(navController.graph.id, true)
                                    .build()
                            )
                        } else {
                            Log.w("processUpload", "uploadResponse is null")
                            val msg = "Unknown Response!"
                            Toast.makeText(this@processUpload, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    val msg = "Error: ${response.code()}: ${response.message()}"
                    Log.w("processUpload", "Error: $msg")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@processUpload, msg, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                val msg = e.message ?: "Unknown Error!"
                Log.i("processUpload", "msg: $msg")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@processUpload, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
