package org.ole.planet.myplanet.ui.mypersonals

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import java.io.File
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSelectedMyPersonal
import org.ole.planet.myplanet.databinding.AlertMyPersonalBinding
import org.ole.planet.myplanet.databinding.RowMyPersonalBinding
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.ui.mypersonals.AdapterMyPersonal.ViewHolderMyPersonal
import org.ole.planet.myplanet.ui.viewer.ImageViewerActivity
import org.ole.planet.myplanet.ui.viewer.PDFReaderActivity
import org.ole.planet.myplanet.ui.viewer.VideoPlayerActivity
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.IntentUtils.openAudioFile
import org.ole.planet.myplanet.utilities.TimeUtils.getFormattedDate
import org.ole.planet.myplanet.utilities.Utilities

class AdapterMyPersonal(private val context: Context, private var list: MutableList<RealmMyPersonal>) : RecyclerView.Adapter<ViewHolderMyPersonal>() {
    private lateinit var rowMyPersonalBinding: RowMyPersonalBinding
    private var realm: Realm? = null
    private var listener: OnSelectedMyPersonal? = null

    fun setListener(listener: OnSelectedMyPersonal?) {
        this.listener = listener
    }

    fun updateList(newList: List<RealmMyPersonal>) {
        val safeNewList = realm?.copyFromRealm(newList) ?: newList
        val diffResult = DiffUtils.calculateDiff(
            list,
            safeNewList,
            areItemsTheSame = { old, new -> old._id == new._id },
            areContentsTheSame = { old, new ->
                old.title == new.title &&
                    old.description == new.description &&
                    old.date == new.date &&
                    old.path == new.path
            }
        )
        list.clear()
        list.addAll(safeNewList)
        diffResult.dispatchUpdatesTo(this)
    }
    
    fun getList(): List<RealmMyPersonal> = list
    fun setRealm(realm: Realm?) {
        this.realm = realm
        list = realm?.copyFromRealm(list)?.toMutableList() ?: list
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderMyPersonal {
        rowMyPersonalBinding = RowMyPersonalBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderMyPersonal(rowMyPersonalBinding)
    }
    override fun onBindViewHolder(holder: ViewHolderMyPersonal, position: Int) {
        rowMyPersonalBinding.title.text = list[position].title
        rowMyPersonalBinding.description.text = list[position].description
        rowMyPersonalBinding.date.text = getFormattedDate(list[position].date)
        rowMyPersonalBinding.imgDelete.setOnClickListener {
            AlertDialog.Builder(context, R.style.AlertDialogTheme)
                .setMessage(R.string.delete_record)
                .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                    listener?.onDeletePersonal(list[position])
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        rowMyPersonalBinding.imgEdit.setOnClickListener {
            editPersonal(list[position])
        }
        holder.itemView.setOnClickListener {
            openResource(list[position].path)
        }
        rowMyPersonalBinding.imgUpload.setOnClickListener {
            if (listener != null) {
                listener?.onUpload(list[position])
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
        val alertMyPersonalBinding = AlertMyPersonalBinding.inflate(LayoutInflater.from(context))
        alertMyPersonalBinding.etDescription.setText(personal.description)
        alertMyPersonalBinding.etTitle.setText(personal.title)
        AlertDialog.Builder(context, R.style.AlertDialogTheme)
            .setTitle(R.string.edit_personal)
            .setIcon(R.drawable.ic_edit)
            .setView(alertMyPersonalBinding.root)
            .setPositiveButton(R.string.button_submit) {_: DialogInterface?, _: Int ->
                val title = alertMyPersonalBinding.etDescription.text.toString().trim { it <= ' ' }
                val desc = alertMyPersonalBinding.etTitle.text.toString().trim { it <= ' ' }
                if (title.isEmpty()) {
                    Utilities.toast(context, context.getString(R.string.please_enter_title))
                    return@setPositiveButton
                }
                listener?.onEditPersonal(personal, title, desc)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    override fun getItemCount(): Int {
        return list.size
    }
    class ViewHolderMyPersonal(rowMyPersonalBinding: RowMyPersonalBinding) : RecyclerView.ViewHolder(rowMyPersonalBinding.root)
}
