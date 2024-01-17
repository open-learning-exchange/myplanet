package org.ole.planet.myplanet.ui.team.teamTask

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
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
import org.ole.planet.myplanet.utilities.Utilities

class AdapterTask(private val context: Context, private val realm: Realm, private val list: List<RealmTeamTask>) : RecyclerView.Adapter<ViewHolderTask>() {
    private lateinit var rowTaskBinding: RowTaskBinding
    private var listener: OnCompletedListener? = null
    fun setListener(listener: OnCompletedListener?) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderTask {
        rowTaskBinding = RowTaskBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderTask(rowTaskBinding) }

    override fun onBindViewHolder(holder: ViewHolderTask, position: Int) {
        rowTaskBinding.checkbox.text = list[position].title
        rowTaskBinding.checkbox.isChecked = list[position].completed
        Utilities.log(list[position].deadline.toString() + "")
        rowTaskBinding.deadline.text = context.getString(R.string.deadline_colon) +
                formatDate(list[position].deadline) + if (list[position].completed)
                    context.getString(R.string.completed_colon) + formatDate(list[position].completedTime) else ""
        showAssignee(list[position])
        rowTaskBinding.checkbox.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            if (listener != null) listener!!.onCheckChange(list[position], b)
        }
        rowTaskBinding.icMore.setOnClickListener {
            if (listener != null) listener!!.onClickMore(list[position])
        }
        rowTaskBinding.editTask.setOnClickListener {
            if (listener != null) listener!!.onEdit(list[position])
        }
        rowTaskBinding.deleteTask.setOnClickListener {
            if (listener != null) listener!!.onDelete(list[position])
        }
        holder.itemView.setOnClickListener {
            showCloseAlert(context, list[position].title!!, list[position].description!!)
        }
    }

    private fun showAssignee(realmTeamTask: RealmTeamTask) {
        if (!TextUtils.isEmpty(realmTeamTask.assignee)) {
            val model = realm.where(RealmUserModel::class.java).equalTo("id", realmTeamTask.assignee).findFirst()
            if (model != null) {
                rowTaskBinding.assignee.text = context.getString(R.string.assigned_to_colon) + model.name
            }
        } else {
            rowTaskBinding.assignee.setText(R.string.no_assignee) }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    interface OnCompletedListener {
        fun onCheckChange(realmTeamTask: RealmTeamTask?, b: Boolean)
        fun onEdit(task: RealmTeamTask?)
        fun onDelete(task: RealmTeamTask?)
        fun onClickMore(realmTeamTask: RealmTeamTask?)
    }

    class ViewHolderTask(rowTaskBinding: RowTaskBinding) : RecyclerView.ViewHolder(rowTaskBinding.root)
}