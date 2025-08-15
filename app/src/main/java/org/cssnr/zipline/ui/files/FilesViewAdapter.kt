package org.cssnr.zipline.ui.files

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import org.cssnr.zipline.R
import org.cssnr.zipline.api.ServerApi.FileEditRequest
import org.cssnr.zipline.api.ServerApi.FileResponse


class FilesViewAdapter(
    private val context: Context,
    private val dataSet: MutableList<FileResponse>,
    val selected: MutableSet<Int>,
    var savedUrl: String,
    var isMetered: Boolean,
    private val listener: OnFileItemClickListener,
) : RecyclerView.Adapter<FilesViewAdapter.ViewHolder>() {

    private var colorOnSecondary: ColorStateList? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fileImage: ShapeableImageView = view.findViewById(R.id.file_image)
        val fileName: TextView = view.findViewById(R.id.file_name)
        val fileSize: TextView = view.findViewById(R.id.file_size)
        val fileView: TextView = view.findViewById(R.id.file_view)
        val fileFavorite: TextView = view.findViewById(R.id.file_favorite)
        val filePassword: TextView = view.findViewById(R.id.file_password)
        val fileExpr: TextView = view.findViewById(R.id.file_expr)
        val itemSelect: FrameLayout = view.findViewById(R.id.item_select)
        val itemPreview: LinearLayout = view.findViewById(R.id.item_preview)
        val itemBorder: LinearLayout = view.findViewById(R.id.item_border)
        val checkMark: ImageView = view.findViewById(R.id.check_mark)
        val menuButton: LinearLayout = view.findViewById(R.id.menu_button)
        val loadingSpinner: ProgressBar = view.findViewById(R.id.loading_spinner)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.file_item_files, viewGroup, false)
        return ViewHolder(view)
    }

    @SuppressLint("UseCompatTextViewDrawableApis")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        //Log.d("UploadMultiAdapter", "position: $position")
        val data = dataSet[position]
        //Log.d("onBindViewHolder", "data[$position]: $data")
        //Log.d("onBindViewHolder", "data[$position]: ${data.name}")

        //viewHolder.fileImage.transitionName = data.id.toString()

        // Setup
        val typedValue = TypedValue()
        val theme = context.theme
        theme.resolveAttribute(
            com.google.android.material.R.attr.colorOnSecondary,
            typedValue,
            true
        )
        colorOnSecondary = ContextCompat.getColorStateList(context, typedValue.resourceId)

        // Name
        viewHolder.fileName.text = data.originalName ?: data.name

        // Size
        viewHolder.fileSize.text = bytesToHuman(data.size.toDouble())

        // Views
        viewHolder.fileView.text = if (data.views > 0) data.views.toString() else ""
        viewHolder.fileView.compoundDrawableTintList =
            if (data.views > 0) null else colorOnSecondary

        // Favorite
        // TODO: Cleanup icon tint setting...
        viewHolder.fileFavorite.compoundDrawableTintList = if (data.favorite) {
            ColorStateList.valueOf(
                ContextCompat.getColor(context, android.R.color.holo_orange_light)
            )
        } else {
            colorOnSecondary
        }

        // Password
        viewHolder.filePassword.compoundDrawableTintList =
            if (data.password == true) null else colorOnSecondary

        // Expiration
        //viewHolder.fileExpr.text = data.deletesAt
        viewHolder.fileExpr.compoundDrawableTintList =
            if (data.deletesAt == null) colorOnSecondary else null

        // Menu Button - Bottom Sheet - listener.onMenuClick
        viewHolder.menuButton.setOnClickListener { view -> listener.onMenuClick(data, view) }

        // File Image - Item Preview - listener.onPreview
        viewHolder.itemPreview.setOnClickListener { listener.onPreview(data) }
        //viewHolder.itemSelect.setOnClickListener {
        //    it.findNavController().navigate(R.id.nav_item_files_action_preview, bundle)
        //}

        // Item View - Item Select - listener.onSelect
        viewHolder.itemSelect.setOnClickListener {
            Log.d("Adapter[itemView]", "setOnClickListener")

            val pos = viewHolder.bindingAdapterPosition
            Log.d("Adapter[itemView]", "itemView: $pos")
            if (pos != RecyclerView.NO_POSITION) {
                if (pos in selected) {
                    Log.d("Adapter[itemView]", "REMOVE - $data")
                    selected.remove(pos)
                    viewHolder.checkMark.visibility = View.GONE
                    //viewHolder.itemBorder.setBackgroundResource(R.drawable.image_border)
                    viewHolder.itemBorder.background = null
                } else {
                    Log.d("Adapter[itemView]", "ADD - $data")
                    selected.add(pos)
                    viewHolder.checkMark.visibility = View.VISIBLE
                    viewHolder.itemBorder.setBackgroundResource(R.drawable.image_border_selected_2dp)
                }
                notifyItemChanged(viewHolder.bindingAdapterPosition)

                listener.onSelect(selected)
            }
        }

        if (position in selected) {
            viewHolder.checkMark.visibility = View.VISIBLE
            viewHolder.itemBorder.setBackgroundResource(R.drawable.image_border_selected_2dp)
        } else {
            viewHolder.checkMark.visibility = View.GONE
            viewHolder.itemBorder.background = null
        }

        // Image - Holder
        val radius = context.resources.getDimension(R.dimen.image_preview_small)
        viewHolder.fileImage.setShapeAppearanceModel(
            viewHolder.fileImage.shapeAppearanceModel
                .toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, radius)
                .build()
        )

        // Image - Glide Listener
        val glideListener = object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>,
                isFirstResource: Boolean
            ): Boolean {
                Log.d("Glide", "onLoadFailed: ${data.name}")
                viewHolder.loadingSpinner.visibility = View.GONE
                viewHolder.fileImage.setImageResource(getGenericIcon(data.type))
                return true
            }

            override fun onResourceReady(
                resource: Drawable,
                model: Any,
                target: Target<Drawable>,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                //Log.d("Glide", "onResourceReady: ${data.name}")
                viewHolder.loadingSpinner.visibility = View.GONE
                //viewHolder.fileImage.scaleType = ImageView.ScaleType.CENTER_CROP
                return false
            }
        }

        // TODO: These methods are in the ViewModel but need to be accessible here...
        //val viewUrl = "${savedUrl}${data.url}"
        val rawUrl = "${savedUrl}/raw/${data.name}"
        val thumbUrl =
            if (data.thumbnail != null) "${savedUrl}/raw/${data.thumbnail.path}" else null

        // Image - Logic
        if (data.password == true) {
            viewHolder.fileImage.setImageResource(R.drawable.md_encrypted_24px)
        } else if (thumbUrl != null || isGlideMime(data.type)) {
            viewHolder.loadingSpinner.visibility = View.VISIBLE
            //val url = data.thumbnail ?: rawUrl
            val url = thumbUrl ?: rawUrl
            //Log.i("Glide", "load: ${data.id}: ${data.type}: $url")
            Glide.with(viewHolder.itemView)
                .load(url)
                .onlyRetrieveFromCache(isMetered)
                .listener(glideListener)
                .into(viewHolder.fileImage)

            //viewHolder.fileImage.transitionName = data.id.toString()
            //Log.d("FilesPreviewFragment", "transitionName: ${viewHolder.fileImage.transitionName}")

        } else {
            viewHolder.fileImage.setImageResource(getGenericIcon(data.type))
            //viewHolder.fileImage.transitionName = null
            //viewHolder.previewLink.setOnClickListener { }
        }
    }

    override fun getItemCount() = dataSet.size

    @SuppressLint("NotifyDataSetChanged")
    fun addData(newData: List<FileResponse>, reset: Boolean = false) {
        Log.d("addData", "addData: ${newData.size}: $reset")
        if (reset) dataSet.clear()
        val start = dataSet.size
        dataSet.addAll(newData)
        if (reset) {
            Log.d("addData", "notifyDataSetChanged")
            notifyDataSetChanged()
        } else {
            Log.d("addData", "notifyItemRangeInserted: $start - ${newData.size}")
            notifyItemRangeInserted(start, newData.size)
        }
    }

    fun getData(): List<FileResponse> {
        return dataSet
    }

    fun updateFavorite(positions: List<Int>, favorite: Boolean) {
        for (position in positions) {
            val item = dataSet[position]
            dataSet[position] = item.copy(favorite = favorite)
            notifyItemChanged(position)
        }
    }


    //fun notifyIdsUpdated(positions: List<Int>) {
    //    val sorted = positions.sortedDescending()
    //    Log.d("notifyIdsUpdated", "sorted: $sorted")
    //    for (pos in sorted) {
    //        //Log.d("notifyIdsUpdated", "pos: $pos")
    //        if (pos in dataSet.indices) {
    //            Log.d("notifyIdsUpdated", "notifyItemChanged: pos: $pos")
    //            notifyItemChanged(pos)
    //        }
    //    }
    //    selected.clear()
    //    //onItemClick(selected)
    //
    //    //Log.d("deleteIds", "start: ${sorted.min()} - count: ${dataSet.size - sorted.min()}")
    //    //notifyItemRangeChanged(sorted.min(), dataSet.size - sorted.min())
    //}

    fun deleteIds(positions: List<Int>) {
        val sorted = positions.sortedDescending()
        Log.d("deleteIds", "sorted: $sorted")
        for (pos in sorted) {
            //Log.d("deleteIds", "pos: $pos")
            if (pos in dataSet.indices) {
                Log.d("deleteIds", "removeAt: $pos")
                dataSet.removeAt(pos)
                notifyItemRemoved(pos)
            }
        }
        selected.clear()
        //onItemClick(selected)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun deleteById(fileId: String) {
        val index = dataSet.indexOfFirst { it.id == fileId }
        if (index != -1) {
            dataSet.removeAt(index)
            notifyItemRemoved(index)
            // TODO: We deleted a file so the indexes are invalid, this is a temporary fix
            //for (pos in selected) {
            //    notifyItemChanged(pos)
            //}
            selected.clear()
            // TODO: Need to store selected File IDs and not Position Index IDs
            notifyDataSetChanged()
        }
    }

    fun editById(request: FileEditRequest) {
        Log.d("editById", "request: $request")
        Log.d("editById", "id: ${request.id}")
        val index = dataSet.indexOfFirst { it.id == request.id }
        Log.d("editById", "index: $index")
        if (index != -1) {
            val file = dataSet[index]
            Log.d("editById", "file: $file")
            Log.d("editById", "file: ${file.favorite} - request: ${request.favorite}")
            if (request.favorite != null) {
                file.favorite = request.favorite
            }
            notifyItemChanged(index)
        }
    }

    private fun bytesToHuman(bytes: Double) = when {
        bytes >= 1 shl 30 -> "%.1f GB".format(bytes / (1 shl 30))
        bytes >= 1 shl 20 -> "%.1f MB".format(bytes / (1 shl 20))
        bytes >= 1 shl 10 -> "%.0f kB".format(bytes / (1 shl 10))
        else -> "$bytes b"
    }
}
