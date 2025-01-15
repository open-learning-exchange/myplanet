package org.ole.planet.myplanet.ui.myPersonals
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
import org.ole.planet.myplanet.utilities.TimeUtils.getFormatedDate
import org.ole.planet.myplanet.utilities.Utilities
import java.io.File

class AdapterMyPersonal(private val context: Context, private val list: List<RealmMyPersonal>) : RecyclerView.Adapter<ViewHolderMyPersonal>() {
    private lateinit var rowMyPersonalBinding: RowMyPersonalBinding
    private var realm: Realm? = null
    private var listener: OnSelectedMyPersonal? = null
    fun setListener(listener: OnSelectedMyPersonal?) {
        this.listener = listener
    }
    fun setRealm(realm: Realm?) {
        this.realm = realm
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderMyPersonal {
        rowMyPersonalBinding = RowMyPersonalBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderMyPersonal(rowMyPersonalBinding)
    }
    override fun onBindViewHolder(holder: ViewHolderMyPersonal, position: Int) {
        rowMyPersonalBinding.title.text = list[position].title
        rowMyPersonalBinding.description.text = list[position].description
        rowMyPersonalBinding.date.text = getFormatedDate(list[position].date)
        rowMyPersonalBinding.imgDelete.setOnClickListener {
            AlertDialog.Builder(context, R.style.AlertDialogTheme)
                .setMessage(R.string.delete_record)
                .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                    if (!realm?.isInTransaction!!) realm?.beginTransaction()
                    val personal = realm?.where(RealmMyPersonal::class.java)
                        ?.equalTo("_id", list[position]._id)?.findFirst()
                    personal?.deleteFromRealm()
                    realm?.commitTransaction()
                    notifyDataSetChanged()
                    listener?.onAddedResource()
                }.setNegativeButton(R.string.cancel, null).show()
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
                    Utilities.toast(context, R.string.please_enter_title.toString())
                    return@setPositiveButton
                }
                if (!realm?.isInTransaction!!) realm?.beginTransaction()
                personal.description = desc
                personal.title = title
                realm?.commitTransaction()
                notifyDataSetChanged()
                listener?.onAddedResource()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    override fun getItemCount(): Int {
        return list.size
    }
    class ViewHolderMyPersonal(rowMyPersonalBinding: RowMyPersonalBinding) : RecyclerView.ViewHolder(rowMyPersonalBinding.root)
}