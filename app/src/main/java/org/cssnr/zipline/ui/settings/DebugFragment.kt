package org.cssnr.zipline.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cssnr.zipline.MainActivity
import org.cssnr.zipline.R
import org.cssnr.zipline.databinding.FragmentDebugBinding
import java.io.File

class DebugFragment : Fragment() {

    private var _binding: FragmentDebugBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val LOG_TAG = "DebugFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDebugBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        Log.d(LOG_TAG, "onStart - Hide UI and Lock Drawer")
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav).visibility = View.GONE
        (activity as? MainActivity)?.setDrawerLockMode(false)
    }

    override fun onStop() {
        Log.d(LOG_TAG, "onStop - Show UI and Unlock Drawer")
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav).visibility =
            View.VISIBLE
        (activity as? MainActivity)?.setDrawerLockMode(true)
        super.onStop()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(LOG_TAG, "savedInstanceState: ${savedInstanceState?.size()}")

        val ctx = requireContext()

        lifecycleScope.launch { _binding?.textView?.text = ctx.readLogFile() }

        // TODO: Look into the usage of doOnLayout and updatePadding
        binding.buttonGroup.doOnLayout {
            _binding?.textView?.updatePadding(bottom = it.height + 24)
        }

        binding.goBack.setOnClickListener {
            Log.d(LOG_TAG, "binding.goBack: navController.navigateUp()")
            findNavController().navigateUp()
        }

        binding.copyLogs.setOnClickListener {
            Log.d(LOG_TAG, "copyLogs")
            val text = binding.textView.text.toString().trim()
            if (text.isNotEmpty()) {
                val clipboard = ctx.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Text", text))
                Snackbar.make(view, "Copied to Clipboard.", Snackbar.LENGTH_SHORT)
                    .setAnchorView(binding.buttonGroup).show()
            } else {
                Snackbar.make(view, "Nothing to Copy!", Snackbar.LENGTH_LONG)
                    .setTextColor("#D32F2F".toColorInt()).setAnchorView(binding.buttonGroup).show()
            }
        }

        binding.shareLogs.setOnClickListener {
            Log.d(LOG_TAG, "shareLogs")
            val text = binding.textView.text.toString().trim()
            if (text.isNotEmpty()) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, binding.textView.text)
                }
                startActivity(Intent.createChooser(shareIntent, null))
            } else {
                Snackbar.make(view, "Nothing to Share!", Snackbar.LENGTH_LONG)
                    .setTextColor("#D32F2F".toColorInt()).setAnchorView(binding.buttonGroup).show()
            }
        }

        binding.reloadLogs.setOnClickListener {
            Log.d(LOG_TAG, "reloadLogs")
            lifecycleScope.launch {
                _binding?.textView?.text = ctx.readLogFile()
                Snackbar.make(view, "Logs Reloaded.", Snackbar.LENGTH_SHORT)
                    .setAnchorView(binding.buttonGroup).show()
            }
        }

        binding.clearLogs.setOnClickListener {
            Log.d(LOG_TAG, "clearLogs")
            val text = binding.textView.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            MaterialAlertDialogBuilder(ctx, R.style.AlertDialogTheme)
                .setIcon(R.drawable.md_delete_24px)
                .setTitle("Confirm")
                .setMessage("Delete All Logs?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ ->
                    val logFile = File(ctx.filesDir, "debug_log.txt")
                    logFile.writeText("")
                    binding.textView.text = ""
                    Snackbar.make(view, "Logs Cleared.", Snackbar.LENGTH_SHORT)
                        .setAnchorView(binding.buttonGroup).show()
                }
                .show()
        }

        binding.swiperefresh.setOnRefreshListener {
            Log.d(LOG_TAG, "setOnRefreshListener: onRefresh")
            lifecycleScope.launch {
                _binding?.textView?.text = ctx.readLogFile()
                Snackbar.make(view, "Logs Reloaded.", Snackbar.LENGTH_SHORT)
                    .setAnchorView(binding.buttonGroup).show()
                _binding?.swiperefresh?.isRefreshing = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(LOG_TAG, "DebugFragment - onResume")
        lifecycleScope.launch { _binding?.textView?.text = requireContext().readLogFile() }
    }

    private suspend fun Context.readLogFile(): String = withContext(Dispatchers.IO) {
        try {
            // TODO: Ensure file exist here...
            val file = File(filesDir, "debug_log.txt")
            if (!file.canRead()) {
                Log.e("readLogFile", "Log File Not Found or Not Readable: ${file.absolutePath}")
                return@withContext "Unable to read log file: ${file.absolutePath}"
            }
            file.readLines().asReversed().joinToString("\n")
        } catch (e: Exception) {
            Log.e("readLogFile", "Exception", e)
            "Exception reading logs: ${e.message}"
        }
    }
}
