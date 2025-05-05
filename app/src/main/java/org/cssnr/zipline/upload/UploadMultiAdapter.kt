package org.cssnr.zipline.upload

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.cssnr.zipline.R

class UploadMultiAdapter(
    private val dataSet: List<Uri>,
    private val selectedUris: MutableSet<Uri>,
    private val onItemClick: (MutableSet<Uri>) -> Unit,
) : RecyclerView.Adapter<UploadMultiAdapter.ViewHolder>() {

    private lateinit var context: Context

    // TODO: Consider moving this to a ViewModel...

    //// Note: This data type redraws the list every time
    //val selectedUris = mutableSetOf<Uri>()
    //init {
    //    selectedUris.addAll(dataSet)
    //}

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return dataSet[position].hashCode().toLong()
    }

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val imageHolder: FrameLayout = view.findViewById(R.id.image_holder)
        val itemSelect: FrameLayout = view.findViewById(R.id.item_select)
        val imageView: ImageView = view.findViewById(R.id.image_view)
        val fileText: TextView = view.findViewById(R.id.file_name)
        val checKMark: ImageView = view.findViewById(R.id.check_mark)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.file_item_upload, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        //Log.d("Multi[onBindViewHolder]", "position: $position")
        val data = dataSet[position]
        //Log.d("Multi[onBindViewHolder]", "data: $data")

        val mimeType = context.contentResolver.getType(data)
        //Log.d("Multi[onBindViewHolder]", "mimeType: $mimeType")

        if (mimeType != null && isGlideMime(mimeType)) {
            Glide.with(holder.imageView).load(data).into(holder.imageView)
        } else {
            holder.imageView.setImageResource(getGenericIcon(mimeType!!))
        }

        val fileName = getFileNameFromUri(context, data)
        //Log.d("Multi[onBindViewHolder]", "fileName: $fileName")
        holder.fileText.text = fileName

        if (selectedUris.contains(data)) {
            holder.checKMark.visibility = View.VISIBLE
            holder.itemSelect.setBackgroundResource(R.drawable.image_border_selected)
        } else {
            holder.checKMark.visibility = View.GONE
            holder.itemSelect.setBackgroundResource(R.drawable.image_border)
        }

        holder.itemView.setOnClickListener {
            Log.d("Adapter[onClick]", "itemView: $position")

            if (selectedUris.contains(data)) {
                Log.d("Adapter[onClick]", "REMOVE - $data")
                selectedUris.remove(data)
                holder.checKMark.visibility = View.GONE
                holder.itemSelect.setBackgroundResource(R.drawable.image_border)
            } else {
                Log.d("Adapter[onClick]", "ADD - $data")
                selectedUris.add(data)
                holder.checKMark.visibility = View.VISIBLE
                holder.itemSelect.setBackgroundResource(R.drawable.image_border_selected)
            }
            notifyItemChanged(holder.bindingAdapterPosition)

            onItemClick(selectedUris)
        }

        //val screenWidth = holder.itemView.resources.displayMetrics.widthPixels
        ////val itemSpacing = 12 * 2 + 4 * 2 // parent padding + item padding (in pixels)
        //val itemSpacing = 64
        //val spanCount = 2
        //val size = (screenWidth - itemSpacing) / spanCount
        //Log.d("UploadMultiAdapter", "size: $size")
        //
        //holder.itemView.layoutParams.width = size
        //holder.itemView.layoutParams.height = size
        //Log.d("UploadMultiAdapter", "width: ${holder.itemView.layoutParams.width}")
        //Log.d("UploadMultiAdapter", "height: ${holder.itemView.layoutParams.height}")

    }

    override fun getItemCount(): Int = dataSet.size
}
