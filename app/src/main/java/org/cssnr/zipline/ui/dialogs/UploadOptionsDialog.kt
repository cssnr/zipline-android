package org.cssnr.zipline.ui.dialogs

import UploadOptions
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.cssnr.zipline.R

class UploadOptionsDialog : DialogFragment() {

    private var uploadOptions: UploadOptions? = null

    companion object {
        fun newInstance(data: UploadOptions): UploadOptionsDialog {
            return UploadOptionsDialog().apply {
                arguments = Bundle().apply {
                    putParcelable("upload_options", data)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uploadOptions = arguments?.getParcelable("upload_options")
    }


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_upload_options, null)

        val filePassword = view.findViewById<EditText>(R.id.file_password)
        val fileDeletesAt = view.findViewById<EditText>(R.id.file_deletes_at)
        val fileMaxViews = view.findViewById<EditText>(R.id.file_max_views)

        uploadOptions?.password?.let { filePassword.setText(it) }
        uploadOptions?.deletesAt?.let { fileDeletesAt.setText(it) }
        uploadOptions?.maxViews?.let { fileMaxViews.setText(it.toString()) }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val bundle = bundleOf(
                    "filePassword" to filePassword.text.toString().takeIf { it.isNotEmpty() },
                    "deletesAt" to fileDeletesAt.text.toString().takeIf { it.isNotEmpty() },
                    "maxViews" to fileMaxViews.text.toString().toIntOrNull(),
                )
                Log.i("UploadOptionsDialog", "bundle: $bundle")
                setFragmentResult("upload_options_result", bundle)
                dismiss()
            }
        }
        return dialog
    }
}
