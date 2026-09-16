package org.cssnr.zipline.ui.settings.logs

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cssnr.zipline.R
import org.cssnr.zipline.databinding.FragmentLogsBinding
import org.cssnr.zipline.log.AppLogs
import org.cssnr.zipline.log.LogExportResult
import org.cssnr.zipline.ui.showSnackbar

class LogsFragment : Fragment() {

    private var _binding: FragmentLogsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: LogsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("LogsFragment", "onViewCreated")

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top)
            insets
        }

        val ctx = requireContext()

        binding.goBack.setOnClickListener {
            Log.d("LogsFragment", "goBack: navigateUp()")
            findNavController().navigateUp()
        }

        adapter = LogsAdapter(emptyList())
        binding.logsList.layoutManager = LinearLayoutManager(ctx)
        binding.logsList.adapter = adapter

        binding.btnCopy.setOnClickListener {
            Log.d("LogsFragment", "btnCopy")
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { AppLogs.exportAsText(ctx) }
                when (result) {
                    LogExportResult.Error ->
                        ctx.showSnackbar("Failed to export logs")

                    LogExportResult.Empty ->
                        ctx.showSnackbar("No Logs to Copy")

                    is LogExportResult.Success -> ctx.copyToClipboard(result.text)
                }
            }
        }

        binding.btnShare.setOnClickListener {
            Log.d("LogsFragment", "btnShare")
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { AppLogs.exportAsText(ctx) }
                when (result) {
                    LogExportResult.Error ->
                        ctx.showSnackbar("Failed to export logs")

                    LogExportResult.Empty ->
                        ctx.showSnackbar("No Logs to Share")

                    is LogExportResult.Success -> ctx.shareLogs(result.text)
                }
            }
        }

        binding.btnDelete.setOnClickListener {
            Log.d("LogsFragment", "btnDelete")
            MaterialAlertDialogBuilder(ctx, R.style.AlertDialogTheme)
                .setTitle("Delete Logs?")
                .setIcon(R.drawable.md_delete_24px)
                .setMessage("This will remove all stored log entries.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { AppLogs.clear(ctx) }
                        ctx.showSnackbar("Logs Deleted")
                    }
                }
                .show()
        }

        binding.swiperefresh.setOnRefreshListener {
            Log.d("LogsFragment", "onRefresh")
            binding.swiperefresh.isRefreshing = false
        }

        lifecycleScope.launch {
            AppLogs.getLogs(ctx).collectLatest { logs ->
                Log.d("LogsFragment", "collectLatest: ${logs.size}")
                adapter.updateData(logs)
                binding.emptyState.visibility =
                    if (logs.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun Context.copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Logs", text))
        showSnackbar("Logs Copied to Clipboard")
    }

    private fun Context.shareLogs(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Zipline Logs")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share Logs"))
    }
}