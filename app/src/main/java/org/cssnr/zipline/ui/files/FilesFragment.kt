package org.cssnr.zipline.ui.files

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.CookieManager
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import androidx.transition.Slide
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.cssnr.zipline.R
import org.cssnr.zipline.api.ServerApi
import org.cssnr.zipline.api.ServerApi.FileResponse
import org.cssnr.zipline.api.ServerApi.FilesTransaction
import org.cssnr.zipline.databinding.FragmentFilesBinding
import java.io.InputStream

class FilesFragment : Fragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FilesViewModel by activityViewModels()

    private var atEnd = false
    private var errorCount = 0
    private var isMetered = false

    private lateinit var api: ServerApi
    private lateinit var filesAdapter: FilesViewAdapter
    private lateinit var downloadManager: DownloadManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("File[onCreateView]", "savedInstanceState: ${savedInstanceState?.size()}")

        //enterTransition = Slide(Gravity.END)
        returnTransition = Slide(Gravity.END)

        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }

    override fun onDestroyView() {
        Log.d("File[onDestroyView]", "ON DESTROY")
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //Log.d("File[onViewCreated]", "savedInstanceState: ${savedInstanceState?.size()}")

        val ctx = requireContext()

        Log.d("File[onViewCreated]", "DELAY: postponeEnterTransition")
        postponeEnterTransition()
        binding.filesRecyclerView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    binding.filesRecyclerView.viewTreeObserver.removeOnPreDrawListener(this)
                    Log.d("File[onPreDraw]", "BEGIN: startPostponedEnterTransition")
                    startPostponedEnterTransition()
                    return true
                }
            }
        )

        val preferences = PreferenceManager.getDefaultSharedPreferences(ctx)
        val savedUrl = preferences.getString("ziplineUrl", "").toString()
        //Log.d("File[onViewCreated]", "savedUrl: $savedUrl")
        val authToken = preferences.getString("ziplineToken", "")
        //Log.d("File[onViewCreated]", "authToken: $authToken")
        val perPage = preferences.getInt("files_per_page", 25)
        //Log.d("File[onViewCreated]", "perPage: $perPage")
        val previewMetered = preferences.getBoolean("file_preview_metered", false)
        //Log.d("File[checkMetered]", "previewMetered: $previewMetered")

        viewModel.setUrl(savedUrl)

        if (authToken.isNullOrEmpty()) {
            Log.w("File[onViewCreated]", "NO AUTH TOKEN")
            Toast.makeText(ctx, "Missing Auth Token!", Toast.LENGTH_LONG).show()
            return
        }

        api = ServerApi(ctx, savedUrl)
        checkMetered(previewMetered) // Set isMetered

        val versionName = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        val userAgent = "${ctx.packageName}/${versionName}"

        val cookie = CookieManager.getInstance().getCookie(savedUrl)
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Cookie", cookie)
                    .header("User-Agent", userAgent)
                    .build()
                chain.proceed(request)
            }
            .build()
        val okHttpUrlLoader = OkHttpUrlLoader.Factory(okHttpClient)
        Glide.get(ctx).registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            okHttpUrlLoader
        )

        val selected = viewModel.selected.value?.toMutableSet() ?: mutableSetOf<Int>()
        Log.d("File[onViewCreated]", "viewModel.selected -> selected.size: ${selected.size}")

        if (!::filesAdapter.isInitialized) {
            Log.i("File[onViewCreated]", "INITIALIZE ADAPTER isMetered: $isMetered")
            filesAdapter = FilesViewAdapter(
                ctx,
                mutableListOf(),
                selected,
                savedUrl,
                isMetered,
                object : OnFileItemClickListener {
                    override fun onSelect(list: MutableSet<Int>) {
                        Log.d("OnFileItemClickListener", "onSelect: $list")
                        viewModel.selected.value = list
                    }

                    override fun onMenuClick(file: FileResponse, anchor: View) {
                        Log.d("OnFileItemClickListener", "onMenuClick: $file")
                        val bottomSheet = FilesBottomSheet()
                        viewModel.activeFile.value = file
                        bottomSheet.show(parentFragmentManager, bottomSheet.tag)
                    }

                    override fun onPreview(file: FileResponse) {
                        Log.d("OnFileItemClickListener", "onPreview: $file")
                        viewModel.activeFile.value = file
                        //findNavController().navigate(R.id.nav_item_files_action_preview)
                        val navController = findNavController()
                        if (navController.currentDestination?.id == R.id.nav_item_files) {
                            navController.navigate(R.id.nav_item_files_action_preview)
                        }

                    }
                }
            )
        }

        fun updateCheckButton() {
            val selectedSize = viewModel.selected.value?.size ?: 0
            val filesSize = viewModel.filesData.value?.size ?: 0
            binding.filesTotalText.text = getString(R.string.files_total, filesSize)
            binding.filesSelectedText.text =
                getString(R.string.files_selected_total, selectedSize, filesSize)
            if (selectedSize == filesSize) {
                //Log.i("filesData[updateCheckButton]", "ALL SELECTED")
                binding.filesSelectAll.setImageResource(R.drawable.md_check_box_24px)
            } else {
                //Log.i("filesData[updateCheckButton]", "NOT ALL SELECTED")
                binding.filesSelectAll.setImageResource(R.drawable.md_check_box_outline_blank_24px)
            }
        }

        Log.d("File[onViewCreated]", "viewModel.selectedUris.value: ${viewModel.selected.value}")
        if (viewModel.selected.value != null && viewModel.selected.value!!.isEmpty() != true) {
            Log.d(
                "File[onViewCreated]",
                "viewModel.selectedUris.value: ${viewModel.selected.value}"
            )
            binding.filesSelectedHeader.visibility = View.VISIBLE
            Log.i("updateCheckButton", "DEBUG 1 - updateCheckButton")
            updateCheckButton()
        }

        binding.filesRecyclerView.layoutManager = LinearLayoutManager(ctx)
        binding.filesRecyclerView.adapter = filesAdapter

        Log.d("File[onViewCreated]", "filesAdapter.itemCount: ${filesAdapter.itemCount}")
        Log.d("File[onViewCreated]", "viewModel.filesData: ${viewModel.filesData.value?.size}")

        if (!viewModel.filesData.value.isNullOrEmpty() && filesAdapter.itemCount == 0) {
            Log.i("File[onViewCreated]", "LOAD FROM CACHE")
            filesAdapter.addData(viewModel.filesData.value!!)
            binding.loadingSpinner.visibility = View.GONE
        } else if (viewModel.filesData.value.isNullOrEmpty()) {
            lifecycleScope.launch { getFiles(perPage) }
            // binding.loadingSpinner.visibility = View.GONE // getFiles handles this
        } else {
            Log.i("File[onViewCreated]", "ALREADY LOADED")
            binding.loadingSpinner.visibility = View.GONE
        }

        viewModel.filesData.observe(viewLifecycleOwner) { list ->
            if (list != null) {
                Log.d("filesData[observe]", "list: ${list.size}")
                Log.i("updateCheckButton", "DEBUG 2 - updateCheckButton")
                updateCheckButton()
            }
        }
        viewModel.selected.observe(viewLifecycleOwner) { selected ->
            Log.d("selected[observe]", "selected.size: ${selected?.size}")
            //viewModel.selected.value = selected
            if (selected.isNotEmpty()) {
                binding.filesSelectedHeader.visibility = View.VISIBLE
            } else {
                binding.filesSelectedHeader.visibility = View.GONE
            }
            //binding.filesSelectedText.text = getString(R.string.files_selected, selected.size)
            Log.i("updateCheckButton", "DEBUG 3 - updateCheckButton")
            updateCheckButton()
        }
        viewModel.atEnd.observe(viewLifecycleOwner) {
            Log.d("atEnd[observe]", "atEnd: $atEnd")
            atEnd = it ?: false
        }

        //binding.refreshLayout.isEnabled = false

        binding.filesRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (!rv.canScrollVertically(1)) {
                    Log.d("File[onScrolled]", "atEnd: $atEnd")
                    if (!atEnd) {
                        Log.d("File[onScrolled]", "loadingSpinner: View.VISIBLE")
                        binding.loadingSpinner.visibility = View.VISIBLE
                        Log.i("File[onScrolled]", "2 - getFiles: ON SCROLL")
                        lifecycleScope.launch {
                            getFiles(perPage)
                        }
                    } else {
                        Log.d("File[onScrolled]", "AT END - NOTHING TO DO")
                    }
                }

                //// Only Enable Refresh Layout when At Top
                //if (!rv.canScrollVertically(-1) && rv.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
                //    Log.i("File[onScrolled]", "REFRESH: ON")
                //    binding.refreshLayout.isEnabled = true
                //} else if (binding.refreshLayout.isEnabled) {
                //    Log.i("File[onScrolled]", "REFRESH: OFF")
                //    binding.refreshLayout.isEnabled = false
                //}
            }
        })

        // Setup refresh listener which triggers new data loading
        binding.refreshLayout.setOnRefreshListener(object : OnRefreshListener {
            override fun onRefresh() {
                // TODO: This will be overhauled and possibly disabled until then...
                Log.d("File[refreshLayout]", "onRefresh")
                lifecycleScope.launch {
                    Log.d("File[refreshLayout]", "START")

                    //Log.d("File[refreshLayout]", "Get Albums in the Background...")
                    //launch(Dispatchers.IO) {
                    //    ctx.getAlbums(savedUrl)
                    //}

                    //viewModel.selected.value?.clear()
                    viewModel.selected.value = mutableSetOf<Int>()
                    filesAdapter.selected.clear()
                    Log.i("File[refreshLayout]", "3 - getFiles: ON REFRESH")
                    getFiles(perPage, true)
                    binding.refreshLayout.isRefreshing = false
                    //binding.refreshLayout.isEnabled = false
                    Log.d("File[refreshLayout]", "DONE")
                    // Fade In
                    binding.refreshBanner.post {
                        binding.refreshBanner.translationY = -binding.refreshBanner.height.toFloat()
                        binding.refreshBanner.visibility = View.VISIBLE
                        binding.refreshBanner.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(400)
                            .start()
                    }
                    Handler(Looper.getMainLooper()).postDelayed({
                        // Fade Out
                        binding.refreshBanner.animate()
                            .alpha(0f)
                            .translationY(-binding.refreshBanner.height.toFloat())
                            .setDuration(400)
                            .withEndAction {
                                binding.refreshBanner.visibility = View.GONE
                            }
                            .start()
                    }, 1600)
                }
            }
        })

        val filesSelectAll: (View) -> Unit = { view ->
            val totalSize = viewModel.filesData.value?.size ?: 0
            Log.d("File[filesSelectAll]", "totalSize: $totalSize")
            val currentSelected = viewModel.selected.value?.toSet()
            Log.d("File[filesSelectAll]", "currentSelected: $currentSelected")
            if (currentSelected.isNullOrEmpty() || currentSelected.size < totalSize) {
                Log.i("File[filesSelectAll]", "SELECT ALL")
                Log.d("File[filesSelectAll]", "size: ${viewModel.selected.value?.size}")
                binding.filesSelectedHeader.visibility = View.VISIBLE
                val positionIds: MutableSet<Int> =
                    viewModel.filesData.value?.indices?.toMutableSet() ?: mutableSetOf<Int>()
                Log.d("deleteId[observe]", "positionIds: $positionIds")
                viewModel.selected.value = positionIds
                filesAdapter.selected.addAll(viewModel.selected.value!!)
                Log.d("File[filesSelectAll]", "size: ${viewModel.selected.value?.size}")
                //binding.filesSelectedText.text =
                //    getString(R.string.files_selected, viewModel.selected.value?.size)
                Log.i("updateCheckButton", "DEBUG 4 - updateCheckButton")
                updateCheckButton()

                if (positionIds.isNotEmpty()) {
                    //val first = positionIds.first()
                    //Log.d("File[filesSelectAll]", "first: $first ")
                    val last = positionIds.size
                    Log.d("File[filesSelectAll]", "last: $last")
                    filesAdapter.notifyItemRangeChanged(0, positionIds.size)
                    //filesAdapter.notifyItemRangeChanged(first, last - first + 1)
                }
            } else {
                Log.i("File[filesSelectAll]", "UNSELECT ALL")
                viewModel.selected.value = mutableSetOf<Int>()
                filesAdapter.selected.clear()
                Log.d(
                    "File[filesSelectAll]",
                    "viewModel.selected.value.size: ${viewModel.selected.value?.size}"
                )
                binding.filesSelectedHeader.visibility = View.GONE

                Log.d("File[filesSelectAll]", "currentSelected: $currentSelected")
                currentSelected.forEach { position ->
                    Log.d("File[filesSelectAll]", "position: $position")
                    filesAdapter.notifyItemChanged(position)
                }
                Log.d("File[filesSelectAll]", "currentSelected: $currentSelected")
            }
        }

        binding.filesSelectAll.setOnClickListener(filesSelectAll)
        //binding.filesDeselect.setOnClickListener(filesSelectAll)

        binding.deleteAllButton.setOnClickListener {
            Log.d("File[deleteAllButton]", "viewModel.selected.value: ${viewModel.selected.value}")
            if (viewModel.selected.value.isNullOrEmpty()) return@setOnClickListener
            val positions = viewModel.selected.value!!.toList()
            Log.d("File[deleteAllButton]", "positions: $positions")
            val data = viewModel.filesData.value!!.toList()
            Log.d("File[deleteAllButton]", "data.size: ${data.size}")
            val selectedPositions: List<Int> = viewModel.selected.value!!.toList()
            Log.d("File[deleteAllButton]", "selectedPositions: $selectedPositions")
            val ids: List<String> = selectedPositions.map { index -> data[index].id }
            Log.d("File[deleteAllButton]", "ids: $ids")
            fun callback(deletePositions: List<Int>) {
                Log.d("File[deleteAllButton]", "callback: $deletePositions")
                filesAdapter.deleteIds(deletePositions)
            }
            ctx.deleteConfirmDialog(ids, selectedPositions, ::callback)
        }

        downloadManager = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        binding.downloadAllButton.setOnClickListener {
            if (viewModel.selected.value.isNullOrEmpty()) return@setOnClickListener
            val filesData = viewModel.filesData.value!!.toList()
            val positions: List<Int> = viewModel.selected.value!!.toList()
            Log.d("File[downloadAllButton]", "positions: $positions")
            fun callback() {
                //val fileUrls: List<String> = positions.map { index -> "${savedUrl}/raw/${data[index].name}" }
                //Log.d("File[callback]", "fileUrls: $fileUrls")
                lifecycleScope.launch {
                    for (pos in positions) {
                        val data = filesData[pos]
                        Log.d("File[callback]", "data: $data")
                        val request = getDownloadRequest(savedUrl, data)
                        val downloadId = downloadManager.enqueue(request)
                        Log.d("downloadButton", "Download ID: $downloadId")
                    }
                }
            }
            ctx.downloadConfirmDialog(positions, ::callback)
        }

        binding.favoriteAllButton.setOnClickListener {
            Log.d(
                "File[favoriteAllButton]",
                "viewModel.selected.value: ${viewModel.selected.value}"
            )
            if (viewModel.selected.value.isNullOrEmpty()) return@setOnClickListener
            val positions = viewModel.selected.value!!.toList()
            Log.d("File[favoriteAllButton]", "positions: $positions")
            val data = viewModel.filesData.value!!.toList()
            Log.d("File[favoriteAllButton]", "data.size: ${data.size}")
            val selectedPositions: List<Int> = viewModel.selected.value!!.toList()
            Log.d("File[favoriteAllButton]", "selectedPositions: $selectedPositions")
            val ids: List<String> = selectedPositions.map { index -> data[index].id }
            Log.d("File[favoriteAllButton]", "ids: $ids")
            fun callback() {
                Log.d("File[favoriteAllButton]", "callback: $selectedPositions")
                // TODO: Update viewModel.filesData then notify filesAdapter
                //filesAdapter.notifyIdsUpdated(selectedPositions)
            }
            ctx.favoriteConfirmDialog(ids, selectedPositions, ::callback)
        }

        //binding.expireAllButton.setOnClickListener {
        //    Log.d("File[expireAllButton]", "viewModel.selected.value: ${viewModel.selected.value}")
        //    fun callback(newExpr: String) {
        //        Log.d("File[expireAllButton]", "callback: ${viewModel.selected.value}")
        //        for (index in viewModel.selected.value!!) {
        //            val file = viewModel.filesData.value?.get(index)
        //            file?.expr = newExpr
        //        }
        //        filesAdapter.notifyIdsUpdated(viewModel.selected.value!!.toList())
        //    }
        //
        //    val fileIds = getFileIds(viewModel.selected.value!!.toList())
        //    Log.d("File[expireAllButton]", "fileIds: $fileIds")
        //    ctx.showExpireDialog(fileIds, ::callback)
        //}

        //binding.albumAllButton.setOnClickListener {
        //    Log.d("File[albumAllButton]", "viewModel.selected.value: ${viewModel.selected.value}")
        //    setFragmentResultListener("albums_result") { _, bundle ->
        //        val albums = bundle.getIntegerArrayList("albums")
        //        Log.d("File[fragmentResultListener]", "albums: $albums")
        //        for (index in viewModel.selected.value!!) {
        //            val file = viewModel.filesData.value?.get(index)
        //            file?.albums = albums!!
        //        }
        //        // TODO: Look into notifyIdsUpdated and determine if it should be NUKED!!!
        //        filesAdapter.notifyIdsUpdated(viewModel.selected.value!!.toList())
        //    }
        //    lifecycleScope.launch {
        //        val dao = AlbumDatabase.getInstance(ctx, savedUrl).albumDao()
        //        val albums = withContext(Dispatchers.IO) { dao.getAll() }
        //        Log.d("File[lifecycleScope]", "albums: $albums")
        //        val albumFragment = AlbumFragment()
        //        val fileIds = getFileIds(viewModel.selected.value!!.toList())
        //        Log.d("File[lifecycleScope]", "fileIds: $fileIds")
        //        albumFragment.setAlbumData(albums, fileIds, emptyList())
        //        albumFragment.show(parentFragmentManager, "AlbumFragment")
        //    }
        //}

        // Monitor viewModel.deleteId for changes and attempt to filesAdapter.deleteById the ID
        viewModel.deleteId.observe(viewLifecycleOwner) { fileId ->
            Log.d("deleteId[observe]", "fileId: $fileId")
            if (fileId != null) {
                filesAdapter.deleteById(fileId)
            }
        }

        // Monitor viewModel.editRequest for changes and do something...
        viewModel.editRequest.observe(viewLifecycleOwner) { editRequest ->
            Log.d("editRequest[observe]", "editRequest: $editRequest")
            if (editRequest != null) {
                filesAdapter.editById(editRequest)
            }
        }

        //// TODO: Avoid using this...
        //// Monitor viewModel.updateRequest for changes and do something...
        //viewModel.updateRequest.observe(viewLifecycleOwner) { updateRequest ->
        //    Log.d("updateRequest[observe]", "updateRequest: $updateRequest")
        //    for (pos in updateRequest) {
        //        filesAdapter.notifyItemChanged(pos)
        //    }
        //}

        binding.downloadManager.setOnClickListener {
            startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS), null)
        }

    }

    //fun getFileIds(positions: List<Int>): List<Int> {
    //    Log.d("File[getFileIds]", "positions: $positions")
    //    val data = viewModel.filesData.value!!.toList()
    //    Log.d("File[getFileIds]", "data.size: ${data.size}")
    //    val ids: List<Int> = positions.map { index -> data[index].id }
    //    Log.d("File[getFileIds]", "ids: $ids")
    //    return ids
    //}

    suspend fun getFiles(perPage: Int, reset: Boolean = false) {
        try {
            if (reset) {
                viewModel.currentPage.value = 1
                atEnd = false
            }
            Log.d("getFiles", "currentPage: ${viewModel.currentPage.value}")
            val files = api.files(viewModel.currentPage.value!!, perPage)
            viewModel.currentPage.value = viewModel.currentPage.value?.plus(1)
            Log.d("getFiles", "files.count: ${files?.count()}")
            if (!files.isNullOrEmpty()) {
                filesAdapter.addData(files, reset)
                viewModel.filesData.value = filesAdapter.getData()
                if (files.count() < perPage) {
                    Log.i("getFiles", "LESS THAN $perPage - SET AT END")
                    atEnd = true
                    viewModel.atEnd.value = atEnd
                }
            } else {
                Log.i("getFiles", "NO DATA RETURNED - SET AT END")
                atEnd = true
                viewModel.atEnd.value = atEnd
            }

        } catch (e: Exception) {
            Log.e("getFiles", "Exception: $e")
            errorCount += 1
            val msg = e.message ?: "Exception Fetching Files"
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }
        }
        Log.d("loadingSpinner", "loadingSpinner: View.GONE")
        binding.loadingSpinner.visibility = View.GONE
        if (errorCount > 5) {
            atEnd = true
            viewModel.atEnd.value = atEnd
            val msg = "Recieved $errorCount Errors. Aborting!"
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    //override fun onStart() {
    //    Log.d("File[onStart]", "ON START")
    //    super.onStart()
    //    val request = NetworkRequest.Builder().build()
    //    connectivityManager.registerNetworkCallback(request, networkCallback)
    //}

    //override fun onStop() {
    //    Log.d("File[onStop]", "ON STOP")
    //    super.onStop()
    //    connectivityManager.unregisterNetworkCallback(networkCallback)
    //}

    override fun onPause() {
        Log.d("File[onPause]", "ON PAUSE")
        super.onPause()
    }

    override fun onResume() {
        Log.d("File[onResume]", "ON RESUME")
        super.onResume()
        checkMetered()
    }

    private fun checkMetered(metered: Boolean? = null) {
        Log.d("File[checkMetered]", "checkMetered")
        val previewMetered = if (metered != null) {
            metered
        } else {
            val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
            preferences.getBoolean("file_preview_metered", false)
        }
        Log.d("File[checkMetered]", "previewMetered: $previewMetered")
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        Log.d("File[checkMetered]", "METERED: ${connectivityManager.isActiveNetworkMetered}")
        isMetered = if (previewMetered) false else connectivityManager.isActiveNetworkMetered
        Log.d("File[checkMetered]", "isMetered: $isMetered")

        if (::filesAdapter.isInitialized) {
            filesAdapter.isMetered = isMetered
            Log.d("File[checkMetered]", "filesAdapter.isMetered: ${filesAdapter.isMetered}")
        }

        Log.d("File[checkMetered]", "viewModel.meterHidden.value: ${viewModel.meterHidden.value}")
        val displayMetered = if (viewModel.meterHidden.value == true) false else isMetered
        Log.d("File[checkMetered]", "displayMetered: $displayMetered")

        if (displayMetered && connectivityManager.isActiveNetworkMetered) {
            binding.meteredText.visibility = View.VISIBLE
            binding.meteredText.setOnClickListener {
                // TODO: The recycler view does not slide until after this animation completes...
                //binding.meteredText.visibility = View.GONE
                viewModel.meterHidden.value = true
                binding.meteredText.animate()
                    .translationY(-binding.meteredText.height.toFloat())
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        binding.meteredText.visibility = View.GONE
                    }
                    .start()
            }
        } else {
            binding.meteredText.visibility = View.GONE
        }
    }
}

private fun Context.deleteConfirmDialog(
    fileIds: List<String>,
    selectedPositions: List<Int>,
    callback: (deletePositions: List<Int>) -> Unit,
) {
    // TODO: Refactor this function to not use a callback or not exist at all...
    Log.d("deleteConfirmDialog", "fileIds: $fileIds - selectedPositions: $selectedPositions")
    val count = fileIds.count()
    val s = if (count > 1) "s" else ""
    MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
        .setTitle("Delete $count File${s}")
        .setIcon(R.drawable.md_delete_24px)
        .setMessage("This will permanently delete the file${s}!\nConfirm deleting $count file${s}?")
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Delete $count File${s}") { _, _ ->
            Log.d("deleteConfirmDialog", "Delete Confirm: fileIds $fileIds")
            val api = ServerApi(this)
            CoroutineScope(Dispatchers.IO).launch {
                //val transaction = FilesTransaction(files = fileIds)
                val response = api.deleteMany(fileIds)
                Log.d("deleteConfirmDialog", "response: $response")
                if (response != null) {
                    Log.d("deleteConfirmDialog", "filesAdapter.deleteIds: $selectedPositions")
                    withContext(Dispatchers.Main) {
                        callback(selectedPositions)
                    }
                }
            }
        }
        .show()
}

private fun Context.downloadConfirmDialog(positions: List<Int>, callback: () -> Unit) {
    Log.d("downloadConfirmDialog", "positions: $positions")
    val count = positions.count()
    val s = if (count > 1) "s" else ""
    MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
        .setTitle("Download $count File${s}")
        .setIcon(R.drawable.md_download_24px)
        .setMessage("Files are saved to the Downloads directory.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Download $count File${s}") { _, _ -> callback() }
        .show()
}

//private fun Context.favoriteConfirmDialog(
//    fileIds: List<String>,
//    selectedPositions: List<Int>,
//    callback: () -> Unit,
//) {
//    // TODO: Add option to Add to favorite or Remove to favorite...
//    Log.d("favoriteConfirmDialog", "fileIds: $fileIds - selectedPositions: $selectedPositions")
//    val count = fileIds.count()
//    val s = if (count > 1) "s" else ""
//    MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
//        .setTitle("Favorite $count File${s}")
//        .setIcon(R.drawable.md_delete_24px)
//        .setMessage("Set all $count file${s} as favorite files?")
//        .setNegativeButton("Cancel", null)
//        .setPositiveButton("Favorite $count File${s}") { _, _ ->
//            Log.d("favoriteConfirmDialog", "Favorite Confirm: fileIds $fileIds")
//            val api = ServerApi(this)
//            CoroutineScope(Dispatchers.IO).launch {
//                //val transaction = FilesTransaction(files = fileIds)
//                val transaction = FilesTransaction(files = fileIds, favorite = true)
//                val response = api.editMany(transaction)
//                Log.d("favoriteConfirmDialog", "response: $response")
//                if (response != null) {
//                    Log.d("favoriteConfirmDialog", "callback()")
//                    withContext(Dispatchers.Main) { callback() }
//                }
//            }
//        }
//        .show()
//}

private fun Context.favoriteConfirmDialog(
    fileIds: List<String>,
    selectedPositions: List<Int>,
    callback: () -> Unit,
) {
    Log.d("favoriteConfirmDialog", "fileIds: $fileIds - selectedPositions: $selectedPositions")
    val count = fileIds.count()
    val s = if (count > 1) "s" else ""
    MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
        .setTitle("Favorite $count File${s}")
        .setIcon(R.drawable.md_delete_24px)
        .setMessage("Set or remove favorite status for $count file${s}?")
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Add") { _, _ ->
            handleFavoriteUpdate(fileIds, true, callback)
        }
        .setNeutralButton("Remove") { _, _ ->
            handleFavoriteUpdate(fileIds, false, callback)
        }
        .show()
}

private fun Context.handleFavoriteUpdate(
    fileIds: List<String>,
    favorite: Boolean,
    callback: () -> Unit,
) {
    Log.d("favoriteConfirmDialog", "Favorite Confirm ($favorite): fileIds $fileIds")
    val api = ServerApi(this)
    CoroutineScope(Dispatchers.IO).launch {
        val transaction = FilesTransaction(files = fileIds, favorite = favorite)
        val response = api.editMany(transaction)
        Log.d("favoriteConfirmDialog", "response: $response")
        if (response != null) {
            Log.d("favoriteConfirmDialog", "callback()")
            withContext(Dispatchers.Main) { callback() }
        }
    }
}

//suspend fun Context.getAlbums(savedUrl: String) {
//    Log.d("getAlbums", "getAlbums: $savedUrl")
//    val api = ServerApi(this, savedUrl)
//    val response = api.albums()
//    Log.d("getAlbums", "response: $response")
//    if (response.isSuccessful) {
//        val dao: AlbumDao = AlbumDatabase.getInstance(this, savedUrl).albumDao()
//        val albumResponse = response.body()
//        Log.d("getAlbums", "albumResponse: $albumResponse")
//        if (albumResponse != null) {
//            dao.syncAlbums(albumResponse.albums)
//            //for (album in albumResponse.albums) {
//            //    Log.d("getAlbums", "album: $album")
//            //    val albumEntry = AlbumEntity(
//            //        id = album.id,
//            //        name = album.name,
//            //        password = album.password,
//            //        private = album.private,
//            //        info = album.info,
//            //        expr = album.expr,
//            //        date = album.date,
//            //        url = album.url,
//            //    )
//            //    Log.d("getAlbums", "albumEntry: $albumEntry")
//            //    dao.addOrUpdate(album = albumEntry)
//            //}
//            Log.d("getAlbums", "DONE")
//        }
//    }
//}
//
//fun Context.showExpireDialog(
//    fileIds: List<Int>,
//    callback: (newExpr: String) -> Unit,
//    currentValue: String? = null,
//) {
//    // TODO: Refactor this function to not use a callback or not exist at all...
//    Log.d("showExpireDialog", "$fileIds: $fileIds")
//
//    val layout = LinearLayout(this)
//    layout.orientation = LinearLayout.VERTICAL
//    layout.setPadding(10, 0, 10, 40)
//
//    val input = EditText(this)
//    input.inputType = android.text.InputType.TYPE_CLASS_TEXT
//    input.maxLines = 1
//    input.hint = "6mo"
//
//    if (currentValue != null) {
//        Log.d("showExpireDialog", "input.setText: currentValue: $currentValue")
//        input.setText(currentValue)
//        input.setSelection(0, currentValue.length)
//    }
//    input.requestFocus()
//    layout.addView(input)
//
//    val savedUrl =
//        this.getSharedPreferences("AppPreferences", MODE_PRIVATE).getString("ziplineUrl", "")
//            .toString()
//    MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
//        .setView(layout)
//        .setTitle("Set Expiration")
//        .setIcon(R.drawable.md_timer_24)
//        .setMessage("Leave Blank for None")
//        .setNegativeButton("Cancel", null)
//        .setPositiveButton("Save") { _, _ ->
//            val newExpire = input.text.toString().trim()
//            Log.d("showExpireDialog", "newExpire: $newExpire")
//            val api = ServerApi(this, savedUrl)
//            CoroutineScope(Dispatchers.IO).launch {
//                val response =
//                    api.filesEdit(FilesEditRequest(ids = fileIds, expr = newExpire))
//                Log.d("showExpireDialog", "response: $response")
//                if (response.isSuccessful) {
//                    withContext(Dispatchers.Main) {
//                        callback(newExpire)
//                    }
//                } else {
//                    Log.w("showExpireDialog", "RESPONSE FAILURE")
//                }
//            }
//        }
//        .show()
//}

fun Context.openUrl(url: String) {
    val openIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(url.toUri(), "text/plain")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    this.startActivity(Intent.createChooser(openIntent, null))
}

fun Context.shareUrl(url: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    this.startActivity(Intent.createChooser(shareIntent, null))
}


fun isGlideMime(mimeType: String): Boolean {
    return when (mimeType.lowercase()) {
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp",
        "image/heif",
            -> true

        else -> false
    }
}

fun isCodeMime(mimeType: String): Boolean {
    if (mimeType.startsWith("text/x-script")) return true
    return when (mimeType.lowercase()) {
        "application/atom+xml",
        "application/javascript",
        "application/json",
        "application/ld+json",
        "application/rss+xml",
        "application/xml",
        "application/x-httpd-php",
        "application/x-python",
        "application/x-www-form-urlencoded",
        "application/yaml",
        "text/javascript",
        "text/python",
        "text/x-go",
        "text/x-ruby",
        "text/x-php",
        "text/x-python",
        "text/x-shellscript",
            -> true

        else -> false
    }
}

fun getGenericIcon(mimeType: String): Int = when {
    isCodeMime(mimeType) -> R.drawable.md_code_blocks_24
    mimeType.startsWith("application/json") -> R.drawable.md_file_json_24
    mimeType.startsWith("application/pdf") -> R.drawable.md_picture_as_pdf_24
    mimeType.startsWith("image/gif") -> R.drawable.md_gif_box_24
    mimeType.startsWith("image/png") -> R.drawable.md_file_png_24
    mimeType.startsWith("text/csv") -> R.drawable.md_csv_24
    mimeType.startsWith("audio/") -> R.drawable.md_music_note_24
    mimeType.startsWith("image/") -> R.drawable.md_imagesmode_24
    mimeType.startsWith("text/") -> R.drawable.md_docs_24
    mimeType.startsWith("video/") -> R.drawable.md_videocam_24
    else -> R.drawable.md_unknown_document_24
}


fun getDownloadRequest(savedUrl: String, data: FileResponse): DownloadManager.Request {
    val rawUrl = "${savedUrl}/raw/${data.name}"
    Log.d("getDownloadRequest", "rawUrl: $rawUrl")
    return DownloadManager.Request(rawUrl.toUri()).apply {
        setTitle(data.originalName ?: data.name)
        setMimeType(data.type)
        setDescription("Zipline Download")
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            data.originalName ?: data.name
        )
        setAllowedOverMetered(true)
        setAllowedOverRoaming(true)
        setRequiresCharging(false)
    }
}

interface OnFileItemClickListener {
    fun onSelect(list: MutableSet<Int>)
    fun onMenuClick(file: FileResponse, anchor: View)
    fun onPreview(file: FileResponse)
}

