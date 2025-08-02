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
import android.widget.Toast
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cssnr.zipline.MainActivity
import org.cssnr.zipline.R
import org.cssnr.zipline.databinding.FragmentDebugBinding
import org.cssnr.zipline.ui.settings.headers.HeadersFragment
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
        Log.d(HeadersFragment.Companion.LOG_TAG, "onStart - Hide UI and Lock Drawer")
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav).visibility = View.GONE
        (activity as? MainActivity)?.setDrawerLockMode(false)
    }

    override fun onStop() {
        Log.d(HeadersFragment.Companion.LOG_TAG, "onStop - Show UI and Unlock Drawer")
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav).visibility =
            View.VISIBLE
        (activity as? MainActivity)?.setDrawerLockMode(true)
        super.onStop()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(LOG_TAG, "savedInstanceState: ${savedInstanceState?.size()}")

        val ctx = requireContext()

        lifecycleScope.launch { binding.textView.text = ctx.readLogFile() }

        binding.buttonGroup.doOnLayout {
            binding.textView.updatePadding(bottom = it.height + 24)
        }

        binding.goBack.setOnClickListener {
            Log.d(HeadersFragment.Companion.LOG_TAG, "binding.goBack: navController.navigateUp()")
            findNavController().navigateUp()
        }

        binding.copyLogs.setOnClickListener {
            Log.d(LOG_TAG, "copyLogs")
            val text = binding.textView.text.toString().trim()
            if (text.isNotEmpty()) ctx.copyToClipboard(text, "Logs Copied")
        }

        binding.shareLogs.setOnClickListener {
            Log.d(LOG_TAG, "shareLogs")
            val text = binding.textView.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, binding.textView.text)
            }
            startActivity(Intent.createChooser(shareIntent, null))
        }

        binding.reloadLogs.setOnClickListener {
            Log.d(LOG_TAG, "reloadLogs")
            lifecycleScope.launch {
                binding.textView.text = ctx.readLogFile()
                Toast.makeText(ctx, "Logs Reloaded.", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(ctx, "Logs Cleared.", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        binding.swiperefresh.setOnRefreshListener(object : OnRefreshListener {
            override fun onRefresh() {
                Log.d(LOG_TAG, "setOnRefreshListener: onRefresh")
                lifecycleScope.launch {
                    _binding?.textView?.text = ctx.readLogFile()
                    Toast.makeText(ctx, "Logs Reloaded.", Toast.LENGTH_SHORT).show()
                    _binding?.swiperefresh?.isRefreshing = false
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        Log.d(LOG_TAG, "DebugFragment - onResume")
        lifecycleScope.launch { _binding?.textView?.text = requireContext().readLogFile() }
    }

    suspend fun Context.readLogFile(): String = withContext(Dispatchers.IO) {
        try {
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

    fun Context.copyToClipboard(text: String, msg: String? = null) {
        val clipboard = this.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, msg ?: "Copied to Clipboard", Toast.LENGTH_SHORT).show()
    }
}
