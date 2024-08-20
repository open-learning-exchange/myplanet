package org.ole.planet.myplanet.ui.team.teamTask

import android.app.AlertDialog
import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowTaskBinding
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.team.teamTask.AdapterTask.ViewHolderTask
import org.ole.planet.myplanet.utilities.DialogUtils.showCloseAlert
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate

class AdapterTask(private val context: Context, private val realm: Realm, private val list: List<RealmTeamTask>?, private val nonTeamMember: Boolean) : RecyclerView.Adapter<ViewHolderTask>() {
    private lateinit var rowTaskBinding: RowTaskBinding
    private var listener: OnCompletedListener? = null
    fun setListener(listener: OnCompletedListener?) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderTask {
        rowTaskBinding = RowTaskBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderTask(rowTaskBinding) }

    override fun onBindViewHolder(holder: ViewHolderTask, position: Int) {
        list?.get(position)?.let {
            rowTaskBinding.checkbox.text = it.title
            rowTaskBinding.checkbox.isChecked = it.completed
            if (!it.completed) {
                rowTaskBinding.deadline.text = context.getString(R.string.deadline_colon, formatDate(it.deadline))
            } else {
                rowTaskBinding.deadline.text =context.getString(R.string.two_strings,
                    context.getString(R.string.deadline_colon, formatDate(it.deadline)), context.getString(R.string.completed_colon, formatDate(it.deadline)))
            }
            showAssignee(it)
            rowTaskBinding.icMore.setOnClickListener {
                listener?.onClickMore(list[position])
            }
            rowTaskBinding.editTask.setOnClickListener {
                listener?.onEdit(list[position])
            }
            rowTaskBinding.deleteTask.setOnClickListener {
                listener?.onDelete(list[position])
            }
            holder.itemView.setOnClickListener {
                val alertDialog = AlertDialog.Builder(context, R.style.AlertDialogTheme)
                    .setTitle(list[position].title)
                    .setMessage(list[position].description)
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .create()

                alertDialog.show()
            }
            if (nonTeamMember) {
                rowTaskBinding.editTask.visibility = View.GONE
                rowTaskBinding.deleteTask.visibility = View.GONE
                rowTaskBinding.icMore.visibility = View.GONE
                rowTaskBinding.checkbox.isClickable = false
                rowTaskBinding.checkbox.isFocusable = false
            } else {
                rowTaskBinding.checkbox.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
                    listener?.onCheckChange(it, b)
                }
            }
        }
    }

    private fun showAssignee(realmTeamTask: RealmTeamTask) {
        if (!TextUtils.isEmpty(realmTeamTask.assignee)) {
            val model = realm.where(RealmUserModel::class.java).equalTo("id", realmTeamTask.assignee).findFirst()
            if (model != null) {
                rowTaskBinding.assignee.text = context.getString(R.string.assigned_to_colon, model.name)
            }
        } else {
            rowTaskBinding.assignee.setText(R.string.no_assignee) }
    }

    override fun getItemCount(): Int {
        return list?.size ?: 0
    }

    interface OnCompletedListener {
        fun onCheckChange(realmTeamTask: RealmTeamTask?, b: Boolean)
        fun onEdit(task: RealmTeamTask?)
        fun onDelete(task: RealmTeamTask?)
        fun onClickMore(realmTeamTask: RealmTeamTask?)
    }

    class ViewHolderTask(rowTaskBinding: RowTaskBinding) : RecyclerView.ViewHolder(rowTaskBinding.root)
}
