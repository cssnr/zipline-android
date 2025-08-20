package org.cssnr.zipline.ui.dialogs

import UploadOptions
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.cssnr.zipline.R

class UploadOptionsDialog : DialogFragment() {

    private var uploadOptions: UploadOptions? = null

    private lateinit var imageCompressionText: TextView
    private lateinit var imageCompressionBar: SeekBar

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
        val deleteAtExamples = view.findViewById<TextView>(R.id.delete_at_examples)

        uploadOptions?.password?.let { filePassword.setText(it) }
        uploadOptions?.deletesAt?.let { fileDeletesAt.setText(it) }
        uploadOptions?.maxViews?.let { fileMaxViews.setText(it.toString()) }

        val compression = uploadOptions?.compression ?: 0
        Log.d("UploadOptionsDialog", "compression: $compression")

        imageCompressionText = view.findViewById(R.id.image_compression_text)
        // TODO: Set Default Value Here...
        imageCompressionText.text = getString(R.string.image_compression, compression)

        imageCompressionBar = view.findViewById(R.id.image_compression)
        // TODO: Set Default Value Here...
        imageCompressionBar.progress = compression

        imageCompressionBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                Log.d("UploadOptionsDialog", "progress: $progress")
                if (fromUser && seekBar != null) {
                    //val stepped = ((progress + 2) / 5) * 5
                    //seekBar.progress = stepped
                    imageCompressionText.text = getString(R.string.image_compression, progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                Log.d("UploadOptionsDialog", "START")
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                Log.d("UploadOptionsDialog", "STOP")
            }
        })

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {

                Log.d("UploadOptionsDialog", "fileDeletesAt.text: ${fileDeletesAt.text}")
                if (fileDeletesAt.text.isNotEmpty()) {
                    val normalized = fileDeletesAt.text.filter { it.isLetterOrDigit() }
                    Log.d("UploadOptionsDialog", "normalized: $normalized")
                    val pattern = Regex(
                        "^\\d+(ms|msec|msecs|millisecond|milliseconds|s|sec|secs|second|seconds|m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days|w|week|weeks|y|yr|yrs|year|years)$",
                        RegexOption.IGNORE_CASE
                    )
                    if (!pattern.matches(normalized)) {
                        fileDeletesAt.error = "Invalid Value"
                        deleteAtExamples.visibility = View.VISIBLE
                        return@setOnClickListener
                    }
                    fileDeletesAt.setText(normalized)
                }

                val bundle = bundleOf(
                    "filePassword" to filePassword.text.toString().takeIf { it.isNotEmpty() },
                    "deletesAt" to fileDeletesAt.text.toString().takeIf { it.isNotEmpty() },
                    "maxViews" to fileMaxViews.text.toString().toIntOrNull(),
                    "compression" to imageCompressionBar.progress,
                )
                Log.i("UploadOptionsDialog", "bundle: $bundle")
                setFragmentResult("upload_options_result", bundle)
                dismiss()
            }
        }
        return dialog
    }

    override fun onResume() {
        if (::imageCompressionBar.isInitialized && ::imageCompressionBar.isInitialized) {
            Log.d("UploadOptionsDialog", "progress ${imageCompressionBar.progress}")
            imageCompressionText.text =
                getString(R.string.image_compression, imageCompressionBar.progress)
        }
        super.onResume()
    }
}
