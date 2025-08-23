package org.ole.planet.myplanet.ui.myPersonals

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSelectedMyPersonal
import org.ole.planet.myplanet.databinding.AlertMyPersonalBinding
import org.ole.planet.myplanet.databinding.RowMyPersonalBinding
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.ui.myPersonals.AdapterMyPersonal.ViewHolderMyPersonal
import org.ole.planet.myplanet.ui.viewer.ImageViewerActivity
import org.ole.planet.myplanet.ui.viewer.PDFReaderActivity
import org.ole.planet.myplanet.ui.viewer.VideoPlayerActivity
import org.ole.planet.myplanet.utilities.IntentUtils.openAudioFile
import org.ole.planet.myplanet.utilities.TimeUtils.getFormattedDate
import org.ole.planet.myplanet.utilities.Utilities

class AdapterMyPersonal(private val context: Context) : ListAdapter<RealmMyPersonal, ViewHolderMyPersonal>(MyPersonalDiffCallback()) {
    private lateinit var rowMyPersonalBinding: RowMyPersonalBinding
    private var listener: OnSelectedMyPersonal? = null
    fun setListener(listener: OnSelectedMyPersonal?) {
        this.listener = listener
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderMyPersonal {
        rowMyPersonalBinding = RowMyPersonalBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderMyPersonal(rowMyPersonalBinding)
    }
    override fun onBindViewHolder(holder: ViewHolderMyPersonal, position: Int) {
        val personal = getItem(position)
        rowMyPersonalBinding.title.text = personal.title
        rowMyPersonalBinding.description.text = personal.description
        rowMyPersonalBinding.date.text = getFormattedDate(personal.date)
        rowMyPersonalBinding.imgDelete.setOnClickListener {
            listener?.onDelete(personal)
        }
        rowMyPersonalBinding.imgEdit.setOnClickListener {
            editPersonal(personal)
        }
        holder.itemView.setOnClickListener {
            openResource(personal.path)
        }
        rowMyPersonalBinding.imgUpload.setOnClickListener {
            if (listener != null) {
                listener?.onUpload(personal)
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
    private fun editPersonal(personal: RealmMyPersonal) {
        listener?.onEdit(personal)
    }
    class ViewHolderMyPersonal(rowMyPersonalBinding: RowMyPersonalBinding) : RecyclerView.ViewHolder(rowMyPersonalBinding.root)
}
