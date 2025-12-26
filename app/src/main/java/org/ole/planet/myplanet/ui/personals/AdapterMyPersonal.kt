package org.ole.planet.myplanet.ui.personals

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import org.ole.planet.myplanet.callback.OnSelectedMyPersonal
import org.ole.planet.myplanet.databinding.RowMyPersonalBinding
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.ui.personals.AdapterMyPersonal.ViewHolderMyPersonal
import org.ole.planet.myplanet.ui.viewer.ImageViewerActivity
import org.ole.planet.myplanet.ui.viewer.PDFReaderActivity
import org.ole.planet.myplanet.ui.viewer.VideoPlayerActivity
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.IntentUtils.openAudioFile
import org.ole.planet.myplanet.utilities.TimeUtils.getFormattedDate

class AdapterMyPersonal(private val context: Context) : ListAdapter<RealmMyPersonal, ViewHolderMyPersonal>(DiffCallback) {
    private var listener: OnSelectedMyPersonal? = null

    fun setListener(listener: OnSelectedMyPersonal?) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderMyPersonal {
        val binding = RowMyPersonalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderMyPersonal(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderMyPersonal, position: Int) {
        val binding = holder.binding
        val item = getItem(position)
        binding.title.text = item.title
        binding.description.text = item.description
        binding.date.text = getFormattedDate(item.date)
        binding.imgDelete.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                listener?.onDeletePersonal(getItem(adapterPosition))
            }
        }
        binding.imgEdit.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                listener?.onEditPersonal(getItem(adapterPosition))
            }
        }
        holder.itemView.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                openResource(getItem(adapterPosition).path)
            }
        }
        binding.imgUpload.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION && listener != null) {
                listener?.onUpload(getItem(adapterPosition))
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

    class ViewHolderMyPersonal(val binding: RowMyPersonalBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DiffCallback =
            DiffUtils.itemCallback<RealmMyPersonal>(
                areItemsTheSame = { old, new -> old._id == new._id },
                areContentsTheSame = { old, new ->
                    old.title == new.title &&
                            old.description == new.description &&
                            old.date == new.date &&
                            old.path == new.path
                }
            )
    }
}
