package org.cssnr.zipline.ui.files

import android.app.DownloadManager
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.CornerFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cssnr.zipline.R
import org.cssnr.zipline.api.ServerApi
import org.cssnr.zipline.api.ServerApi.FileEditRequest
import org.cssnr.zipline.copyToClipboard
import org.cssnr.zipline.databinding.FragmentFilesBottomBinding

class FilesBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentFilesBottomBinding? = null
    private val binding get() = _binding!!

    //private val viewModel: FilesViewModel by viewModels()
    private val viewModel: FilesViewModel by activityViewModels()

    private lateinit var downloadManager: DownloadManager

    companion object {
        fun newInstance(bundle: Bundle) = FilesBottomSheet().apply {
            arguments = bundle
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFilesBottomBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }

    override fun onStart() {
        Log.d("Bottom[onStart]", "ON START")
        super.onStart()
        // Force max height sheet in landscape
        val behavior = BottomSheetBehavior.from(requireView().parent as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("File[onViewCreated]", "savedInstanceState: ${savedInstanceState?.size()}")

        val ctx = requireContext()
        //val preferences = PreferenceManager.getDefaultSharedPreferences(ctx)

        //Log.d("Bottom[onCreateView]", "arguments: $arguments")
        val rawUrl = arguments?.getString("rawUrl") ?: ""
        Log.d("Bottom[onCreateView]", "rawUrl: $rawUrl")
        val viewUrl = arguments?.getString("viewUrl") ?: ""
        val position = requireArguments().getInt("position")
        val data = viewModel.filesData.value?.get(position)
        Log.d("Bottom[onCreateView]", "${position}: $data")
        if (data == null) {
            // TODO: HANDLE THIS ERROR!!!
            return
        }

        // Name
        binding.fileName.text = data.originalName ?: data.name

        //// Private
        //if (data.private) {
        //    tintImage(binding.togglePrivate)
        //}
        //binding.togglePrivate.setOnClickListener {
        //    data.private = !data.private
        //    Log.d("togglePrivate", "New Value: ${data.private}")
        //    val api = ServerApi(ctx, savedUrl)
        //    lifecycleScope.launch {
        //        val response = api.edit(data.id, FileEditRequest(private = data.private))
        //        Log.d("deleteButton", "response: $response")
        //        viewModel.editRequest.value = FileEditRequest(id = data.id, private = data.private)
        //        if (data.private) {
        //            tintImage(binding.togglePrivate)
        //        } else {
        //            binding.togglePrivate.imageTintList = null
        //        }
        //    }
        //}

        //// Album
        //binding.albumButton.setOnClickListener {
        //    Log.d("albumButton", "Album Button")
        //    val dao = AlbumDatabase.getInstance(ctx, savedUrl).albumDao()
        //    lifecycleScope.launch {
        //        Log.d("File[albumButton]", "viewModel.selected.value: ${viewModel.selected.value}")
        //        setFragmentResultListener("albums_result") { _, bundle ->
        //            val albums = bundle.getIntegerArrayList("albums")
        //            Log.d("File[albumButton]", "albums: $albums")
        //            data.albums = albums!!.toList() // TODO: This is enough for non-display items?
        //            //viewModel.updateRequest.value = data
        //        }
        //
        //        val albums = withContext(Dispatchers.IO) { dao.getAll() }
        //        Log.d("File[albumButton]", "albums: $albums")
        //        val albumFragment = AlbumFragment()
        //        albumFragment.setAlbumData(albums, listOf(data.id), data.albums)
        //        albumFragment.show(parentFragmentManager, "AlbumFragment")
        //    }
        //}

        // Share
        binding.shareButton.setOnClickListener {
            ctx.shareUrl(viewUrl)
        }
        // Copy
        binding.copyButton.setOnClickListener {
            copyToClipboard(ctx, viewUrl)
        }

        // Download
        downloadManager = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        //// TODO: Code to query and cleanup download manager entries...
        //val query = DownloadManager.Query()
        //val cursor = downloadManager.query(query)
        //while (cursor.moveToNext()) {
        //    val status = cursor.getIntOrNull(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
        //    val title = cursor.getStringOrNull(cursor.getColumnIndex(DownloadManager.COLUMN_TITLE))
        //    val downloadId = cursor.getStringOrNull(cursor.getColumnIndex(DownloadManager.COLUMN_ID))
        //    Log.i("DM", "$status - $downloadId - $title")
        //    //downloadManager.remove(downloadId?.toLong()!!)
        //}

        binding.downloadButton.setOnClickListener {
            Log.d("downloadButton", "id: ${data.id}: ${data.originalName ?: data.name}")
            Log.d("downloadButton", "rawUrl: $rawUrl")
            binding.downloadButton.isEnabled = false

            val request = DownloadManager.Request(rawUrl.toUri()).apply {
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

            val downloadId = downloadManager.enqueue(request)
            Log.d("downloadButton", "Download ID: $downloadId")
            Toast.makeText(ctx, "Download Started", Toast.LENGTH_SHORT).show()
            //dismiss()
        }

        // Delete
        binding.deleteButton.setOnClickListener {
            Log.d("deleteButton", "fileId: ${data.id}")
            deleteConfirmDialog(data.id, data.name)
        }

        // Favorite
        if (data.favorite) {
            tintImage(binding.favoriteButton)
        }
        binding.favoriteButton.setOnClickListener {
            Log.d("favoriteButton", "setOnClickListener: $data")
            lifecycleScope.launch {
                val api = ServerApi(ctx)
                val editRequest = FileEditRequest(id = data.id, favorite = !data.favorite)
                val result = api.editSingle(data.id, editRequest)
                Log.d("favoriteButton", "result: $result")
                if (result != null) {
                    viewModel.editRequest.value = editRequest
                    tintImage(binding.favoriteButton, result.favorite != true)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Favorite Updated", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        //// Password
        //if (filePassword) {
        //    tintImage(binding.setPassword)
        //}
        //binding.setPassword.setOnClickListener {
        //    Log.d("setPassword", "setOnClickListener")
        //    setPasswordDialog(ctx, data.id, data.name)
        //}
        //// Expire
        //binding.expireButton.setOnClickListener {
        //    Log.d("expireButton", "Expire Button")
        //    fun callback(newExpr: String) {
        //        Log.d("Bottom[expireAllButton]", "newExpr: $newExpr")
        //        data.expr = newExpr
        //        viewModel.updateRequest.value = listOf(position)
        //    }
        //    ctx.showExpireDialog(listOf(data.id), ::callback, data.expr)
        //}
        // Open
        binding.openButton.setOnClickListener {
            ctx.openUrl(viewUrl)
        }

        // Image
        val radius = ctx.resources.getDimension(R.dimen.image_preview_large)
        binding.imagePreview.setShapeAppearanceModel(
            binding.imagePreview.shapeAppearanceModel
                .toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, radius)
                .build()
        )
        if (isGlideMime(data.type)) {
            Log.d("Bottom[onCreateView]", "isGlideMime")
            Glide.with(this)
                .load(rawUrl)
                .into(binding.imagePreview)
        } else {
            binding.imagePreview.setImageResource(getGenericIcon(data.type))
        }
        binding.imagePreview.setOnClickListener {
            Log.d("Bottom[onCreateView]", "onClick: imagePreview")
            findNavController().navigate(R.id.nav_item_files_action_preview, arguments)
            dismiss()
        }
    }

    private fun tintImage(item: ImageView, toNull: Boolean = false) {
        if (toNull) {
            item.imageTintList = null
        } else {
            item.imageTintList =
                ColorStateList.valueOf(
                    ContextCompat.getColor(
                        requireContext(),
                        android.R.color.holo_orange_light
                    )
                )
        }
    }

    private fun deleteConfirmDialog(fileId: String, fileName: String) {
        Log.d("deleteConfirmDialog", "${fileId}: $fileName")
        MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogTheme)
            .setTitle("Delete File?")
            .setIcon(R.drawable.md_delete_24px)
            .setMessage(fileName)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                Log.d("deleteConfirmDialog", "Delete Confirm: fileId $fileId")
                val api = ServerApi(requireContext())
                lifecycleScope.launch {
                    val result = api.deleteSingle(fileId)
                    Log.d("deleteConfirmDialog", "result: $result")
                    val msg = if (result != null) "File Deleted" else "File Not Found"
                    viewModel.deleteId.value = fileId
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT)
                            .show()
                    }
                    dismiss()
                }
            }
            .show()
    }

    //private fun setPasswordDialog(context: Context, fileId: Int, fileName: String) {
    //    Log.d("setPasswordDialog", "$fileId - savedUrl: $fileId")
    //
    //    val layout = LinearLayout(context)
    //    layout.orientation = LinearLayout.VERTICAL
    //    layout.setPadding(10, 0, 10, 40)
    //
    //    val input = EditText(context)
    //    input.inputType = android.text.InputType.TYPE_CLASS_TEXT
    //    input.maxLines = 1
    //    input.hint = "Leave Blank to Remove"
    //    input.setText(filePassword)
    //    input.requestFocus()
    //    layout.addView(input)
    //    input.setSelection(0, filePassword.length)
    //
    //    MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogTheme)
    //        .setView(layout)
    //        .setTitle("Set Password")
    //        .setIcon(R.drawable.md_key_24)
    //        .setMessage(fileName)
    //        .setNegativeButton("Cancel", null)
    //        .setPositiveButton("Save") { _, _ ->
    //            val newPassword = input.text.toString().trim()
    //            Log.d("setPasswordDialog", "newPassword: $newPassword")
    //            if (newPassword == filePassword) {
    //                Log.d("setPasswordDialog", "Password Not Changed.")
    //                return@setPositiveButton
    //            }
    //            filePassword = newPassword
    //            val api = ServerApi(requireContext(), savedUrl)
    //            lifecycleScope.launch {
    //                val response = api.edit(fileId, FileEditRequest(password = newPassword))
    //                Log.d("setPasswordDialog", "response: $response")
    //                viewModel.editRequest.value =
    //                    FileEditRequest(id = fileId, password = newPassword)
    //                if (newPassword.isEmpty()) {
    //                    binding.setPassword.imageTintList = null
    //                } else {
    //                    tintImage(binding.setPassword)
    //                }
    //            }
    //        }
    //        .show()
    //}
}
