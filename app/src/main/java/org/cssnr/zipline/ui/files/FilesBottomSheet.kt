package org.cssnr.zipline.ui.files

import android.app.DownloadManager
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.CornerFamily
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.cssnr.zipline.R
import org.cssnr.zipline.api.ServerApi
import org.cssnr.zipline.api.ServerApi.FileEditRequest
import org.cssnr.zipline.api.ServerApi.FileResponse
import org.cssnr.zipline.databinding.FragmentFilesBottomBinding
import org.cssnr.zipline.ui.upload.copyToClipboard

class FilesBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentFilesBottomBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FilesViewModel by activityViewModels()

    private lateinit var downloadManager: DownloadManager

    private lateinit var data: FileResponse
    private lateinit var rawUrl: String
    private lateinit var viewUrl: String

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

        val radius = ctx.resources.getDimension(R.dimen.image_preview_large)
        binding.imagePreview.setShapeAppearanceModel(
            binding.imagePreview.shapeAppearanceModel
                .toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, radius)
                .build()
        )

        viewModel.activeFile.observe(viewLifecycleOwner) { file ->
            Log.i("activeFile.observe", "file: $file")
            if (file == null) return@observe
            // Data
            data = file
            rawUrl = viewModel.getRawUrl(file)
            Log.d("activeFile.observe", "rawUrl: $rawUrl")
            viewUrl = viewModel.getViewUrl(file)
            Log.d("activeFile.observe", "viewUrl: $viewUrl")
            val thumbUrl = viewModel.getThumbUrl(file)
            Log.d("activeFile.observe", "thumbUrl: $thumbUrl")

            // Name
            binding.fileName.text = file.originalName ?: file.name

            // Favorite
            Log.d("activeFile.observe", "file.favorite: ${file.favorite}")
            if (file.favorite) {
                Log.d("activeFile.observe", "${file.id}: tintImage")
                tintImage(binding.favoriteButton)
            } else {
                Log.d("activeFile.observe", "${file.id}: tintImage - NULL TRUE")
                //tintImage(binding.favoriteButton, true)
            }

            // Image
            if (file.password == true) {
                binding.imagePreview.setImageResource(R.drawable.md_encrypted_24px)
            } else if (isGlideMime(file.type) || thumbUrl != null) {
                Log.d("activeFile.observe", "isGlideMime")
                Glide.with(this)
                    .load(thumbUrl ?: rawUrl)
                    .into(binding.imagePreview)
            } else {
                binding.imagePreview.setImageResource(getGenericIcon(file.type))
            }
        }

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
            ctx.copyToClipboard(viewUrl)
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
            deleteConfirmDialog(data)
        }

        // Favorite
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
                    val text = if (result.favorite == true) "Added to" else "Removed from"
                    val snackbar =
                        Snackbar.make(view, "File $text Favorites.", Snackbar.LENGTH_SHORT)
                    snackbar.setAction("Close") { snackbar.dismiss() }
                    snackbar.setAnchorView(requireView()).show()
                } else {
                    Snackbar.make(view, "Error Changing File Favorite.", Snackbar.LENGTH_LONG)
                        .setTextColor("#D32F2F".toColorInt())
                        .setAnchorView(requireView()).show()
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
        binding.imagePreview.setOnClickListener {
            Log.d("Bottom[onCreateView]", "onClick: imagePreview")
            findNavController().navigate(R.id.nav_item_files_action_preview, arguments)
            dismiss()
        }
    }

    private fun tintImage(item: ImageView, toNull: Boolean = false) {
        // TODO: Cleanup icon tint setting...
        val ctx = requireContext()
        if (toNull) {
            val defaultColor = TypedValue()
            ctx.theme.resolveAttribute(android.R.attr.textColorPrimary, defaultColor, true)
            val color = ContextCompat.getColor(ctx, defaultColor.resourceId)
            item.imageTintList = ColorStateList.valueOf(color)
        } else {
            item.imageTintList =
                ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, android.R.color.holo_orange_light)
                )
        }
    }

    private fun deleteConfirmDialog(data: FileResponse) {
        Log.d("deleteConfirmDialog", "${data.id}: ${data.name}")

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogTheme)
            .setTitle("Delete File?")
            .setIcon(R.drawable.md_delete_24px)
            .setMessage("Name: ${data.name}\nOriginal: ${data.originalName}\nID: ${data.id}")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete", null)
            .create()


        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                Log.d("deleteConfirmDialog", "Delete Confirm: fileId ${data.id}:")
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false

                lifecycleScope.launch {
                    val result = ServerApi(requireContext()).deleteSingle(data.id)
                    Log.d("deleteConfirmDialog", "result: $result")
                    viewModel.deleteId.value = data.id
                    val msg = if (result != null) "File Deleted" else "File Not Found"
                    viewModel.showSnackbar(msg)
                    dialog.dismiss()
                    dismiss()
                }
            }
        }

        dialog.show()
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
