package org.ole.planet.myplanet.ui.mypersonals

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import org.ole.planet.myplanet.callback.OnSelectedMyPersonal
import org.ole.planet.myplanet.databinding.RowMyPersonalBinding
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.ui.mypersonals.AdapterMyPersonal.ViewHolderMyPersonal
import org.ole.planet.myplanet.ui.viewer.ImageViewerActivity
import org.ole.planet.myplanet.ui.viewer.PDFReaderActivity
import org.ole.planet.myplanet.ui.viewer.VideoPlayerActivity
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.IntentUtils.openAudioFile
import org.ole.planet.myplanet.utilities.TimeUtils.getFormattedDate

class AdapterMyPersonal(private val context: Context, private var list: MutableList<RealmMyPersonal>) : RecyclerView.Adapter<ViewHolderMyPersonal>() {
    private var listener: OnSelectedMyPersonal? = null

    fun setListener(listener: OnSelectedMyPersonal?) {
        this.listener = listener
    }

    fun updateList(newList: List<RealmMyPersonal>) {
        val startTime = System.currentTimeMillis()
        Log.d("MyPersonalTiming", "[${startTime}] AdapterMyPersonal.updateList() called with ${newList.size} items (current: ${list.size})")

        val previousItems = list.toList()
        val beforeDiff = System.currentTimeMillis()

        val diffResult = DiffUtils.calculateDiff(
            previousItems,
            newList,
            areItemsTheSame = { old, new -> old._id == new._id },
            areContentsTheSame = { old, new ->
                old.title == new.title &&
                    old.description == new.description &&
                    old.date == new.date &&
                    old.path == new.path
            }
        )

        val afterDiff = System.currentTimeMillis()
        Log.d("MyPersonalTiming", "[${afterDiff}] DiffUtils calculation completed (+${afterDiff - beforeDiff}ms)")

        list = newList.toMutableList()
        diffResult.dispatchUpdatesTo(this)

        val endTime = System.currentTimeMillis()
        Log.d("MyPersonalTiming", "[${endTime}] Adapter updated, UI refresh dispatched (+${endTime - afterDiff}ms, total: ${endTime - startTime}ms)")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderMyPersonal {
        val binding = RowMyPersonalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderMyPersonal(binding)
    }
    override fun onBindViewHolder(holder: ViewHolderMyPersonal, position: Int) {
        val binding = holder.binding
        val item = list[position]
        binding.title.text = item.title
        binding.description.text = item.description
        binding.date.text = getFormattedDate(item.date)
        binding.imgDelete.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                listener?.onDeletePersonal(list[adapterPosition])
            }
        }
        binding.imgEdit.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                listener?.onEditPersonal(list[adapterPosition])
            }
        }
        holder.itemView.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                openResource(list[adapterPosition].path)
            }
        }
        binding.imgUpload.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION && listener != null) {
                listener?.onUpload(list[adapterPosition])
            }
        }
    }
    private fun openResource(path: String?) {
        val arr = path?.split("\\.".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()
        when (arr?.get(arr.size - 1)) {
            "pdf" -> context.startActivity(
                Intent(context, PDFReaderActivity::class.java).putExtra("TOUCHED_FILE", path)
            )
            "bmp", "gif", "jpg", "png", "webp" -> {
                val ii = Intent(context, ImageViewerActivity::class.java).putExtra("TOUCHED_FILE", path)
                ii.putExtra("isFullPath", true)
                context.startActivity(ii)
            }
            "aac", "mp3" -> openAudioFile(context, path)
            "mp4" -> openVideo(path)
        }
    }
    private fun openVideo(path: String?) {
        val b = Bundle()
        b.putString("videoURL", "" + Uri.fromFile(path?.let { File(it) }))
        b.putString("Auth", "" + Uri.fromFile(path?.let { File(it) }))
        b.putString("videoType", "offline")
        val i = Intent(context, VideoPlayerActivity::class.java).putExtra("TOUCHED_FILE", path)
        i.putExtras(b)
        context.startActivity(i)
    }
    override fun getItemCount(): Int {
        return list.size
    }
    class ViewHolderMyPersonal(val binding: RowMyPersonalBinding) : RecyclerView.ViewHolder(binding.root)
}
