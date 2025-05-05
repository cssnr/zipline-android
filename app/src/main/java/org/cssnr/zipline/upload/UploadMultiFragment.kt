package org.cssnr.zipline.upload

import android.content.Context.MODE_PRIVATE
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.launch
import org.cssnr.zipline.R
import org.cssnr.zipline.ZiplineApi
import org.cssnr.zipline.ZiplineApi.FileResponse
import org.cssnr.zipline.databinding.FragmentUploadMultiBinding

class UploadMultiFragment : Fragment() {

    private var _binding: FragmentUploadMultiBinding? = null
    private val binding get() = _binding!!

    private val viewModel: UploadViewModel by activityViewModels()

    private lateinit var navController: NavController
    private lateinit var adapter: UploadMultiAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("Multi[onCreateView]", "savedInstanceState: ${savedInstanceState?.size()}")
        _binding = FragmentUploadMultiBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }

    override fun onDestroyView() {
        Log.d("UploadMultiFragment", "onDestroyView")
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("Multi[onViewCreated]", "savedInstanceState: ${savedInstanceState?.size()}")
        Log.d("Multi[onViewCreated]", "arguments: $arguments")

        navController = findNavController()

        val sharedPreferences = context?.getSharedPreferences("default_preferences", MODE_PRIVATE)
        val savedUrl = sharedPreferences?.getString("ziplineUrl", null)
        Log.d("Multi[onViewCreated]", "savedUrl: $savedUrl")
        val authToken = sharedPreferences?.getString("ziplineToken", null)
        Log.d("Multi[onViewCreated]", "authToken: $authToken")

        if (savedUrl == null) {
            Log.w("Multi[onViewCreated]", "savedUrl is null")
            Toast.makeText(requireContext(), "Missing URL!", Toast.LENGTH_LONG)
                .show()
            navController.navigate(
                R.id.nav_item_setup, null, NavOptions.Builder()
                    .setPopUpTo(R.id.nav_item_home, true)
                    .build()
            )
            return
        }

        val fileUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getParcelableArrayList("fileUris", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getParcelableArrayList("fileUris")
        }
        if (fileUris == null) {
            Log.w("Multi[onCreate]", "fileUris is null")
            return
        }

        if (viewModel.selectedUris.value == null) {
            Log.i("Multi[onCreate]", "RESET SELECTED URIS TO ALL")
            viewModel.selectedUris.value = fileUris.toSet()
        } else {
            Log.i("Multi[onCreate]", "USE VIEW MODEL SELECTED URIS")
        }

        Log.d("Multi[onViewCreated]", "fileUris.size: ${fileUris.size}")
        val selectedUris = viewModel.selectedUris.value!!.toMutableSet()
        Log.d("Multi[onViewCreated]", "selectedUris.size: ${selectedUris.size}")

        if (!::adapter.isInitialized) {
            Log.i("Multi[onViewCreated]", "INITIALIZE NEW ADAPTER")
            adapter = UploadMultiAdapter(fileUris, selectedUris) { updated ->
                viewModel.selectedUris.value = updated
                binding.uploadButton.text = getString(R.string.upload_multi, updated.size)
            }
        }

        binding.previewRecycler.layoutManager = GridLayoutManager(requireContext(), 2)
        if (binding.previewRecycler.adapter == null) {
            binding.previewRecycler.adapter = adapter
        }

        // Upload Button
        binding.uploadButton.text = getString(R.string.upload_multi, selectedUris.size)
        binding.uploadButton.setOnClickListener {
            val selectedUris = viewModel.selectedUris.value
            //Log.d("uploadButton", "selectedUris: $selectedUris")
            Log.d("uploadButton", "selectedUris.size: ${selectedUris?.size}")
            if (selectedUris.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "No Files Selected!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            processMultiUpload(selectedUris)
        }

        // Options Button
        binding.optionsButton.setOnClickListener {
            Log.d("optionsButton", "setOnClickListener: navigate: nav_item_settings")
            navController.navigate(R.id.nav_item_settings)
        }
    }

    private fun processMultiUpload(fileUris: Set<Uri>) {
        Log.d("processMultiUpload", "fileUris: $fileUris")
        Log.d("processMultiUpload", "fileUris.size: ${fileUris.size}")
        val sharedPreferences =
            requireContext().getSharedPreferences("default_preferences", MODE_PRIVATE)
        val savedUrl = sharedPreferences.getString("ziplineUrl", null)
        Log.d("processMultiUpload", "savedUrl: $savedUrl")
        val authToken = sharedPreferences.getString("ziplineToken", null)
        Log.d("processMultiUpload", "authToken: $authToken")

        if (savedUrl == null || authToken == null) {
            // TODO: Show settings dialog here...
            Log.w("processMultiUpload", "Missing OR savedUrl/authToken/fileName")
            Toast.makeText(requireContext(), getString(R.string.tst_no_url), Toast.LENGTH_SHORT)
                .show()
            return
        }
        val msg = "Uploading ${fileUris.size} Files..."
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

        val api = ZiplineApi(requireContext())
        Log.d("processMultiUpload", "api: $api")
        val results: MutableList<FileResponse> = mutableListOf()
        val currentContext = requireContext()
        lifecycleScope.launch {
            for (fileUri in fileUris) {
                Log.d("processMultiUpload", "fileUri: $fileUri")
                val fileName = getFileNameFromUri(currentContext, fileUri)
                Log.d("processMultiUpload", "fileName: $fileName")
                try {
                    val inputStream = currentContext.contentResolver.openInputStream(fileUri)
                    if (inputStream == null) {
                        Log.w("processMultiUpload", "inputStream is null")
                        continue
                    }
                    val response = api.upload(fileName!!, inputStream, savedUrl)
                    Log.d("processMultiUpload", "response: $response")
                    if (response.isSuccessful) {
                        val fileResponse = response.body()
                        Log.d("processMultiUpload", "fileResponse: $fileResponse")
                        if (fileResponse != null) {
                            results.add(fileResponse)
                        }
                    } else {
                        val msg = "Error: ${response.code()}: ${response.message()}"
                        Log.w("processMultiUpload", "UPLOAD ERROR: $msg")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            Log.d("processMultiUpload", "results: $results")
            Log.d("processMultiUpload", "results,size: ${results.size}")
            if (results.isEmpty()) {
                // TODO: Handle upload failures better...
                Toast.makeText(requireContext(), "All Uploads Failed!", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val destUrl =
                if (results.size != 1) "${savedUrl}/dashboard/files/" else results.first().files.first().url
            Log.d("processMultiUpload", "destUrl: $destUrl")
            val msg = "Uploaded ${results.size} Files."
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            navController.navigate(
                R.id.nav_item_home,
                bundleOf("url" to destUrl),
                NavOptions.Builder()
                    .setPopUpTo(R.id.nav_graph, inclusive = true)
                    .build()
            )
        }
    }
}
