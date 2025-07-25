package org.cssnr.zipline.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.cssnr.zipline.R
import org.cssnr.zipline.api.ServerApi

class FolderFragment : DialogFragment() {

    private var folders: List<ServerApi.FolderResponse> = emptyList()
    private var selectedId: String? = null
    private var selectedName: String? = null

    suspend fun setFolderData(context: Context): String? {

        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val savedUrl = preferences?.getString("ziplineUrl", null)
        Log.d("Settings", "savedUrl: $savedUrl")
        var selectedId = preferences?.getString("file_folder_id", null)
        Log.d("Settings", "file_folder_id: selectedId: $selectedId")

        val api = ServerApi(context, savedUrl)
        val folders = api.folders().orEmpty()
        Log.d("Settings", "folders: $folders")
        if (selectedId != null) {
            val current = folders.firstOrNull { it.id == selectedId }
            Log.d("Settings", "current: $current")
            if (current == null) {
                Log.i("Settings", "Resetting Default Folder due to Folder Not Found...")
                preferences?.edit {
                    putString("file_folder_id", null)
                    putString("file_folder_name", null)
                }
                selectedId = null
            }
        }

        this.folders = folders
        this.selectedId = selectedId
        return selectedId
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        //val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        //val savedUrl = preferences.getString("saved_url", "").orEmpty()

        Log.d("dialog[setButton]", "folders: $folders")
        val folderNames = folders.map { it.name }.toMutableList()
        folderNames.add(0, "None")

        //val folder = folders.firstOrNull { it.id == selectedId }
        val index = folders.indexOfFirst { it.id == selectedId }
        Log.d("dialog[setButton]", "index: $index")
        val folder = if (index != -1) folders[index] else null
        Log.d("dialog[setButton]", "folder: $folder")
        val selected = if (index >= 0) index + 1 else 0
        Log.d("dialog[setButton]", "selected: $selected")

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogTheme)
            .setTitle("Select Folder")
            .setIcon(R.drawable.md_folder_24px)
            .setNegativeButton("Cancel") { _, _ -> }
        if (folders.isNotEmpty()) {
            dialog.setSingleChoiceItems(folderNames.toTypedArray(), selected) { _, which ->
                Log.d("dialog[setButton]", "which: $which")
                if (which == 0) {
                    selectedId = null
                    selectedName = null
                } else {
                    val selectedFolder = folders[which - 1]
                    selectedId = selectedFolder.id
                    selectedName = selectedFolder.name
                    Log.d("dialog[setButton]", "selectedFolder: $selectedFolder")
                }
            }
            dialog.setPositiveButton("Save") { _, _ ->
                Log.d("dialog[setButton]", "selectedName: $selectedName - selectedId: $selectedId")
                val bundle = bundleOf(
                    "folderId" to selectedId,
                    "folderName" to selectedName,
                )
                setFragmentResult("folder_fragment_result", bundle)
                dismiss()
            }
        } else {
            dialog.setMessage("No Folders Found.")
        }

        return dialog.create()
    }
}