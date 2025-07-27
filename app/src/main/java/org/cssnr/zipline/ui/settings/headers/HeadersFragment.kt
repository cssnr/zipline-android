package org.cssnr.zipline.ui.settings.headers

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.cssnr.zipline.MainActivity
import org.cssnr.zipline.R
import org.cssnr.zipline.databinding.FragmentHeadersBinding

class HeadersFragment : Fragment() {

    private var _binding: FragmentHeadersBinding? = null
    private val binding get() = _binding!!

    private val navController by lazy { findNavController() }

    private lateinit var preferences: SharedPreferences
    private lateinit var adapter: CustomAdapter

    companion object {
        const val LOG_TAG = "HeadersFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHeadersBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }

    override fun onDestroyView() {
        Log.d(LOG_TAG, "onDestroyView")
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
        Log.d(LOG_TAG, "savedInstanceState: $savedInstanceState")

        val ctx = requireContext()

        binding.goBack.setOnClickListener {
            Log.d(LOG_TAG, "binding.goBack: navController.navigateUp()")
            navController.navigateUp()
        }

        preferences =
            ctx.getSharedPreferences("org.cssnr.zipline_custom_headers", Context.MODE_PRIVATE)
        Log.d(LOG_TAG, "preferences.all: ${preferences.all}")

        val items = preferences.all.map { it.key to it.value.toString() }.sortedBy { it.first }
        Log.d(LOG_TAG, "items: $items")

        val listener = object : OnHeaderItemClick {
            override fun onSelect(data: Pair<String, String>) {
                Log.d(LOG_TAG, "onSelect: $data")
                Log.d(LOG_TAG, "onSelect: showHeadersDialog")
                ctx.showHeadersDialog(data)
            }

            override fun onDelete(data: Pair<String, String>) {
                Log.d(LOG_TAG, "onDelete: $data")
                ctx.deleteConfirmDialog(data)
            }
        }

        binding.recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State
            ) {
                val position = parent.getChildAdapterPosition(view)
                val itemCount = state.itemCount
                if (itemCount == 0 || position == RecyclerView.NO_POSITION) return

                if (position == itemCount - 1) {
                    outRect.bottom = resources.getDimensionPixelSize(R.dimen.headers_bottom_padding)
                }
            }
        })

        adapter = CustomAdapter(items, listener)

        binding.recyclerView.layoutManager = LinearLayoutManager(ctx)
        binding.recyclerView.adapter = adapter

        if (items.isEmpty()) {
            Log.d(LOG_TAG, "items.isEmpty: showHeadersDialog")
            ctx.showHeadersDialog()
        }

        binding.addHeaderButton.setOnClickListener {
            Log.d(LOG_TAG, "addHeaderButton: showHeadersDialog")
            ctx.showHeadersDialog()
            //val dialog = ctx.showHeadersDialog()
            //dialog.setOnDismissListener {
            //    Log.d(LOG_TAG, "preferences.all: ${preferences.all}")
            //    val items = preferences.all.map { it.key to it.value.toString() }
            //    adapter.updateData(items)
            //}
        }
    }

    fun Context.showHeadersDialog(data: Pair<String, String>? = null) {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_headers, null)
        val inputKey = view.findViewById<EditText>(R.id.header_key)
        val inputValue = view.findViewById<EditText>(R.id.header_value)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save Header", null)
            .create()

        dialog.setOnShowListener {
            if (data != null) {
                Log.d(LOG_TAG, "Set data: $data")
                inputKey.setText(data.first)
                inputValue.setText(data.second)
                inputValue.setSelection(0, data.second.length)
                inputValue.requestFocus()
            } else {
                inputKey.requestFocus()
            }
            val sendButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            sendButton.setOnClickListener {
                sendButton.isEnabled = false
                var success = true

                val key = inputKey.text.toString().trim()
                Log.d(LOG_TAG, "key: \"${key}\"")
                inputKey.setText(key)
                if (key.isEmpty()) {
                    inputKey.error = "Required"
                    success = false
                }
                if (!key.matches(Regex("^[!#$%&'*+\\-.^_`|~0-9a-zA-Z]+$"))) {
                    inputKey.error = "Invalid Key Name"
                    inputKey.requestFocus()
                    inputKey.setSelection(inputKey.text.length)
                    success = false
                }
                if (key.lowercase() == "authorization") {
                    inputKey.error = "Not Allowed"
                    inputKey.requestFocus()
                    inputKey.setSelection(inputKey.text.length)
                    success = false
                }

                val value = inputValue.text.toString().replace(Regex("[\\r\\n]"), "").trim()
                Log.d(LOG_TAG, "value: \"${value}\"")
                inputValue.setText(value)
                if (value.isEmpty()) {
                    inputValue.error = "Required"
                    if (success) {
                        inputValue.requestFocus()
                        inputValue.setSelection(inputKey.text.length)
                    }
                    success = false
                }

                if (success) {
                    Log.d(LOG_TAG, "Add Header: ${key}: $value")
                    preferences.edit().apply {
                        if (data != null && key != data.first) {
                            remove(data.first)
                        }
                        putString(key, value)
                        apply()
                    }
                    val items =
                        preferences.all.map { it.key to it.value.toString() }.sortedBy { it.first }
                    adapter.updateData(items)
                    dialog.dismiss()
                }
                sendButton.isEnabled = true
            }
        }
        dialog.show()
    }

    private fun Context.deleteConfirmDialog(data: Pair<String, String>) {
        Log.d(LOG_TAG, "deleteConfirmDialog: ${data.first}")
        MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
            .setTitle(data.first)
            .setIcon(R.drawable.md_delete_24px)
            .setMessage(data.second)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete Header") { _, _ ->
                Log.d(LOG_TAG, "Delete: $data")
                preferences.edit { remove(data.first) }
                val items =
                    preferences.all.map { it.key to it.value.toString() }.sortedBy { it.first }

                adapter.updateData(items)
            }
            .show()
    }

    inner class CustomAdapter(
        private var dataSet: List<Pair<String, String>>,
        private val listener: OnHeaderItemClick
    ) :
        RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val headerKey: TextView = view.findViewById(R.id.header_key)
            val headerValue: TextView = view.findViewById(R.id.header_value)
            val deleteButton: ImageView = view.findViewById(R.id.delete_button)
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.header_item, viewGroup, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val data = dataSet[position]
            Log.d(LOG_TAG, "onBindViewHolder: $position: $data")
            Log.d(LOG_TAG, "\"${data.first}\": \"${data.second}\"")

            viewHolder.headerKey.text = data.first
            viewHolder.headerValue.text = data.second

            viewHolder.itemView.setOnClickListener { view -> listener.onSelect(data) }
            viewHolder.deleteButton.setOnClickListener { view -> listener.onDelete(data) }
        }

        override fun getItemCount() = dataSet.size

        @SuppressLint("NotifyDataSetChanged")
        fun updateData(newData: List<Pair<String, String>>) {
            Log.d(LOG_TAG, "updateData: newData: $newData")
            dataSet = newData
            notifyDataSetChanged()
        }
    }

    interface OnHeaderItemClick {
        fun onSelect(data: Pair<String, String>)
        fun onDelete(data: Pair<String, String>)
    }
}
