package org.cssnr.zipline.ui.upload

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
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
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import org.cssnr.zipline.R
import org.cssnr.zipline.api.ServerApi
import org.cssnr.zipline.api.ServerApi.UploadedFiles
import org.cssnr.zipline.api.UploadOptions
import org.cssnr.zipline.databinding.FragmentUploadMultiBinding
import org.cssnr.zipline.ui.dialogs.FolderFragment

class UploadMultiFragment : Fragment() {

    private var _binding: FragmentUploadMultiBinding? = null
    private val binding get() = _binding!!

    private val viewModel: UploadViewModel by activityViewModels()

    private val uploadOptions = UploadOptions()

    private lateinit var navController: NavController
    private lateinit var adapter: UploadMultiAdapter

    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }

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

    override fun onStart() {
        super.onStart()
        Log.d("Multi[onStart]", "onStart - Hide UI")
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav).visibility = View.GONE
    }

    override fun onStop() {
        Log.d("Multi[onStop]", "onStop - Show UI")
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav).visibility =
            View.VISIBLE
        super.onStop()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("Multi[onViewCreated]", "savedInstanceState: ${savedInstanceState?.size()}")
        Log.d("Multi[onViewCreated]", "arguments: $arguments")

        navController = findNavController()

        val savedUrl = preferences.getString("ziplineUrl", null)
        Log.d("Multi[onViewCreated]", "savedUrl: $savedUrl")
        val authToken = preferences.getString("ziplineToken", null)
        Log.d("Multi[onViewCreated]", "authToken: $authToken")
        if (savedUrl.isNullOrEmpty() || authToken.isNullOrEmpty()) {
            Log.e("Multi[onViewCreated]", "savedUrl is null")
            Toast.makeText(requireContext(), "Missing URL!", Toast.LENGTH_LONG).show()
            navController.navigate(
                R.id.nav_item_login, null, NavOptions.Builder()
                    .setPopUpTo(navController.graph.id, true)
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

        // TODO: Store UploadOptions in ViewModel otherwise their lost on config changes...
        val fileFolderId = preferences.getString("file_folder_id", null)
        uploadOptions.fileFolderId = fileFolderId
        Log.i("Upload[onViewCreated]", "uploadOptions: $uploadOptions")

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

        val spanCount =
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 2 else 4
        Log.i("Multi[onViewCreated]", "spanCount: $spanCount")

        val bottomPadding =
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                resources.getDimensionPixelSize(R.dimen.recycler_bottom_port) else
                resources.getDimensionPixelSize(R.dimen.recycler_bottom_land)
        Log.d("Multi[onViewCreated]", "bottomPadding: $bottomPadding")

        binding.previewRecycler.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State
            ) {
                val position = parent.getChildAdapterPosition(view)
                val itemCount = state.itemCount
                if (itemCount == 0 || position == RecyclerView.NO_POSITION) return

                val rowCount = (itemCount + spanCount - 1) / spanCount
                val itemRow = position / spanCount

                if (itemRow == rowCount - 1) {
                    outRect.bottom = bottomPadding
                }
            }
        })

        binding.previewRecycler.layoutManager = GridLayoutManager(requireContext(), spanCount)
        if (binding.previewRecycler.adapter == null) {
            binding.previewRecycler.adapter = adapter
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
        binding.uploadButton.text = getString(R.string.upload_multi, selectedUris.size)
        binding.uploadButton.setOnClickListener {
            val selectedUris = viewModel.selectedUris.value
            //Log.d("uploadButton", "selectedUris: $selectedUris")
            Log.d("uploadButton", "selectedUris.size: ${selectedUris?.size}")
            if (selectedUris.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "No Files Selected!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            requireContext().processMultiUpload(selectedUris)
        }
    }

    private fun Context.processMultiUpload(fileUris: Set<Uri>) {
        Log.d("processMultiUpload", "fileUris: $fileUris")
        Log.d("processMultiUpload", "fileUris.size: ${fileUris.size}")
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val savedUrl = preferences.getString("ziplineUrl", null)
        Log.d("processMultiUpload", "savedUrl: $savedUrl")
        val authToken = preferences.getString("ziplineToken", null)
        Log.d("processMultiUpload", "authToken: $authToken")
        val shareUrl = preferences.getBoolean("share_after_upload", true)
        Log.d("processUpload", "shareUrl: $shareUrl")

        if (savedUrl == null || authToken == null) {
            // TODO: Show settings dialog here...
            Log.w("processMultiUpload", "Missing OR savedUrl/authToken")
            Toast.makeText(this, getString(R.string.tst_no_url), Toast.LENGTH_SHORT).show()
            logFileUpload(false, "URL or Token is null", true)
            return
        }
        val msg = "Uploading ${fileUris.size} Files..."
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

        val api = ServerApi(this)
        Log.d("processMultiUpload", "api: $api")
        val results: MutableList<UploadedFiles> = mutableListOf()
        lifecycleScope.launch {
            for (fileUri in fileUris) {
                Log.d("processMultiUpload", "fileUri: $fileUri")
                val fileName = getFileNameFromUri(this@processMultiUpload, fileUri)
                Log.d("processMultiUpload", "fileName: $fileName")
                try {
                    val inputStream = contentResolver.openInputStream(fileUri)
                    if (inputStream == null) {
                        Log.w("processMultiUpload", "inputStream is null")
                        continue
                    }
                    val response = api.upload(fileName!!, inputStream, uploadOptions)
                    Log.d("processMultiUpload", "response: $response")
                    if (response.isSuccessful) {
                        val uploadedFiles = response.body()
                        Log.d("processMultiUpload", "uploadedFiles: $uploadedFiles")
                        if (uploadedFiles != null) {
                            results.add(uploadedFiles)
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
            Log.d("processMultiUpload", "results.size: ${results.size}")
            if (results.isEmpty()) {
                // TODO: Handle upload failures better...
                Toast.makeText(this@processMultiUpload, "All Uploads Failed!", Toast.LENGTH_SHORT)
                    .show()
                logFileUpload(false, "All Uploads Failed", true)
                return@launch
            }
            //val destUrl =
            //    if (results.size != 1) "${savedUrl}/dashboard/files/" else results.first().files.first().url
            //Log.d("processMultiUpload", "destUrl: $destUrl")

            val urls = results.flatMap { it.files }.joinToString("\n") { it.url }
            Log.d("processMultiUpload", "urls: \"${urls}\"")
            if (!urls.isEmpty()) {
                copyToClipboard(urls)
            }

            val msg = "Uploaded ${results.size} Files."
            Toast.makeText(this@processMultiUpload, msg, Toast.LENGTH_SHORT).show()
            val fcMsg = if (results.size == fileUris.size) null else "Some Files Failed to Upload"
            logFileUpload(true, fcMsg, true)
            if (shareUrl && !urls.isEmpty()) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, urls)
                }
                startActivity(Intent.createChooser(shareIntent, null))
            }
            val bundle = bundleOf("url" to "${savedUrl}/dashboard/files")
            navController.navigate(
                R.id.nav_item_home, bundle, NavOptions.Builder()
                    .setPopUpTo(navController.graph.id, true)
                    .build()
            )
        }
    }
}
