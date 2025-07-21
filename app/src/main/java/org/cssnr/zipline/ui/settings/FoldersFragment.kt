package org.cssnr.zipline.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cssnr.zipline.R
import org.cssnr.zipline.api.ServerApi

class FoldersFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val preferences = PreferenceManager.getDefaultSharedPreferences(ctx)
        val savedUrl = preferences.getString("ziplineUrl", "")
        Log.d("folders", "savedUrl: $savedUrl")
        val api = ServerApi(ctx, savedUrl)

        val dialog = MaterialAlertDialogBuilder(ctx, R.style.AlertDialogTheme)
            .setTitle("Default Folder")
            .setIcon(R.drawable.md_folder_24px)
            .setNegativeButton("Cancel") { _, _ -> }
            .create()

        dialog.setOnShowListener {
            lifecycleScope.launch {
                val folders = withContext(Dispatchers.IO) { api.folders() }
                Log.d("folders", "folders: $folders")
            }
        }

        return dialog
    }
}
