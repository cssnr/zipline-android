package org.cssnr.zipline.ui.upload

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cssnr.zipline.R
import org.cssnr.zipline.api.ServerApi
import org.cssnr.zipline.api.UploadOptions
import org.cssnr.zipline.databinding.FragmentTextBinding
import org.cssnr.zipline.ui.dialogs.FolderFragment

class TextFragment : Fragment() {

    private var _binding: FragmentTextBinding? = null
    private val binding get() = _binding!!

    private val uploadOptions = UploadOptions()

    private lateinit var navController: NavController

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

        navController = findNavController()

        val savedUrl = preferences.getString("ziplineUrl", null)
        Log.d("Text[onViewCreated]", "savedUrl: $savedUrl")
        val authToken = preferences.getString("ziplineToken", null)
        Log.d("Text[onViewCreated]", "authToken: ${authToken?.take(24)}...")
        if (savedUrl.isNullOrEmpty() || authToken.isNullOrEmpty()) {
            Log.e("Text[onViewCreated]", "savedUrl is null")
            Toast.makeText(requireContext(), "Missing URL!", Toast.LENGTH_LONG).show()
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
            Toast.makeText(requireContext(), "No Text to Process!", Toast.LENGTH_LONG).show()
            //return
        }

        // TODO: Store UploadOptions in ViewModel otherwise their lost on config changes...
        val fileFolderId = preferences.getString("file_folder_id", null)
        uploadOptions.fileFolderId = fileFolderId
        Log.i("Upload[onViewCreated]", "uploadOptions: $uploadOptions")

        binding.textContent.setText(extraText)

        // Share Button
        binding.shareButton.setOnClickListener {
            Log.d("shareButton", "setOnClickListener")
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, binding.textContent.text)
            }
            startActivity(Intent.createChooser(shareIntent, null))
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
                uploadOptions.fileFolderId = folderId
            }

            Log.d("folderButton", "fileFolderId: ${uploadOptions.fileFolderId}")

            lifecycleScope.launch {
                val folderFragment = FolderFragment()
                uploadOptions.fileFolderId = folderFragment.setFolderData(requireContext())
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
            requireContext().processUpload(finalText, fileName)
        }
    }

    // TODO: DUPLICATION: UploadFragment.processUpload
    private fun Context.processUpload(textContent: String, fileName: String) {
        Log.d("processUpload", "textContent: $textContent")
        Log.d("processUpload", "fileName: $fileName")

        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val savedUrl = preferences.getString("ziplineUrl", null)
        Log.d("processUpload", "savedUrl: $savedUrl")
        val authToken = preferences.getString("ziplineToken", null)
        Log.d("processUpload", "authToken: ${authToken?.take(24)}...")
        val shareUrl = preferences.getBoolean("share_after_upload", true)
        Log.d("processShort", "shareUrl: $shareUrl")

        if (savedUrl == null || authToken == null) {
            // TODO: Show settings dialog here...
            Log.w("processUpload", "Missing OR savedUrl/authToken/fileName")
            Toast.makeText(requireContext(), getString(R.string.tst_no_url), Toast.LENGTH_SHORT)
                .show()
            return
        }
        val inputStream = textContent.byteInputStream()
        val api = ServerApi(requireContext())
        Log.d("processUpload", "api: $api")
        Toast.makeText(requireContext(), getString(R.string.tst_uploading_file), Toast.LENGTH_SHORT)
            .show()
        lifecycleScope.launch {
            try {
                // TODO: Implement editRequest
                val response = api.upload(fileName, inputStream, uploadOptions)
                Log.d("processUpload", "response: $response")
                if (response.isSuccessful) {
                    val uploadResponse = response.body()
                    Log.d("processUpload", "uploadResponse: $uploadResponse")
                    withContext(Dispatchers.Main) {
                        if (uploadResponse != null) {
                            logFileUpload(true, "Text Upload")
                            requireContext().copyToClipboard(uploadResponse.files.first().url)
                            val bundle = bundleOf("url" to uploadResponse.files.first().url)
                            navController.navigate(
                                R.id.nav_item_home, bundle, NavOptions.Builder()
                                    .setPopUpTo(navController.graph.id, true)
                                    .build()
                            )
                        } else {
                            Log.w("processUpload", "uploadResponse is null")
                            val msg = "Unknown Response!"
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    val msg = "Error: ${response.code()}: ${response.message()}"
                    Log.w("processUpload", "Error: $msg")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val msg = e.message ?: "Unknown Error!"
                Log.i("processUpload", "msg: $msg")
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
