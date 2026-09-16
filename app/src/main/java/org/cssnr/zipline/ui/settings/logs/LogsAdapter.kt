package org.cssnr.zipline.ui.settings.logs

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import org.cssnr.zipline.R
import org.cssnr.zipline.log.LogEntry
import org.cssnr.zipline.log.LogLevel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class LogsAdapter(
    private var items: List<LogEntry>,
) : RecyclerView.Adapter<LogsAdapter.ViewHolder>() {

    private lateinit var context: Context

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logTimestamp: TextView = view.findViewById(R.id.log_timestamp)
        val logLevel: TextView = view.findViewById(R.id.log_level)
        val logMessage: TextView = view.findViewById(R.id.log_message)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val data = items[position]
        val level = data.levelEnum

        holder.logLevel.text = level.name
        holder.logLevel.setTextColor(MaterialColors.getColor(context, levelAttr(level), 0))

        val instant = Instant.ofEpochMilli(data.timestamp)
        val zonedDateTime = instant.atZone(ZoneId.systemDefault())
        holder.logTimestamp.text = zonedDateTime.format(
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss", Locale.US)
        )

        holder.logMessage.text = data.message
    }

    private fun levelAttr(level: LogLevel): Int = when (level) {
        LogLevel.DEBUG -> com.google.android.material.R.attr.colorOnSurfaceVariant
        LogLevel.INFO -> com.google.android.material.R.attr.colorOnSurface
        LogLevel.WARNING -> com.google.android.material.R.attr.colorTertiary
        LogLevel.ERROR -> android.R.attr.colorError
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newItems: List<LogEntry>) {
        items = newItems
        notifyDataSetChanged()
    }
}
