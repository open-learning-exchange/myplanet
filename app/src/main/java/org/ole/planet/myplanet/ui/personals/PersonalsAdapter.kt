package org.ole.planet.myplanet.ui.personals

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import org.ole.planet.myplanet.callback.OnPersonalSelectedListener
import org.ole.planet.myplanet.databinding.RowMyPersonalBinding
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.ui.personals.PersonalsAdapter.PersonalsViewHolder
import org.ole.planet.myplanet.ui.viewer.ResourceViewerActivity
import org.ole.planet.myplanet.ui.viewer.ResourceViewerFragment
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.IntentUtils.openAudioFile
import org.ole.planet.myplanet.utils.TimeUtils.getFormattedDate

class PersonalsAdapter(private val context: Context) : ListAdapter<RealmMyPersonal, PersonalsViewHolder>(DiffCallback) {
    private var listener: OnPersonalSelectedListener? = null

    fun setListener(listener: OnPersonalSelectedListener?) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonalsViewHolder {
        val binding = RowMyPersonalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PersonalsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PersonalsViewHolder, position: Int) {
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
                Intent(context, ResourceViewerActivity::class.java)
                    .putExtra("TOUCHED_FILE", path)
                    .putExtra("resourceType", ResourceViewerFragment.ResourceType.PDF.name)
            )
            "bmp", "gif", "jpg", "png", "webp" -> context.startActivity(
                Intent(context, ResourceViewerActivity::class.java)
                    .putExtra("TOUCHED_FILE", path)
                    .putExtra("isFullPath", true)
                    .putExtra("resourceType", ResourceViewerFragment.ResourceType.IMAGE.name)
            )
            "aac", "mp3" -> openAudioFile(context, path)
            "mp4" -> context.startActivity(
                Intent(context, ResourceViewerActivity::class.java)
                    .putExtra("TOUCHED_FILE", Uri.fromFile(File(path)).toString())
                    .putExtra("resourceType", ResourceViewerFragment.ResourceType.VIDEO.name)
            )
        }
    }

    class PersonalsViewHolder(val binding: RowMyPersonalBinding) : RecyclerView.ViewHolder(binding.root)

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
