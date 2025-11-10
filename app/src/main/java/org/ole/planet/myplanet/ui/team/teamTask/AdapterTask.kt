package org.ole.planet.myplanet.ui.team.teamTask

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowTaskBinding
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.team.teamTask.AdapterTask.ViewHolderTask
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate

class AdapterTask(
    private val context: Context,
    private val list: List<RealmTeamTask>?,
    private val nonTeamMember: Boolean,
    private val coroutineScope: CoroutineScope
) : RecyclerView.Adapter<ViewHolderTask>() {
    private val assigneeCache: MutableMap<String, String> = mutableMapOf()
    private var listener: OnCompletedListener? = null
    fun setListener(listener: OnCompletedListener?) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderTask {
        val binding = RowTaskBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderTask(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderTask, position: Int) {
        holder.assigneeJob?.cancel()
        list?.get(position)?.let {
            val binding = holder.binding
            binding.checkbox.setOnCheckedChangeListener(null)
            binding.checkbox.text = it.title
            binding.checkbox.isChecked = it.completed
            if (!it.completed) {
                binding.deadline.text = context.getString(R.string.deadline_colon, formatDate(it.deadline))
            } else {
                binding.deadline.text = context.getString(
                    R.string.two_strings,
                    context.getString(R.string.deadline_colon, formatDate(it.deadline)),
                    context.getString(R.string.completed_colon, formatDate(it.deadline))
                )
            }
            holder.assigneeJob = showAssignee(binding, it)
            binding.icMore.setOnClickListener {
                listener?.onClickMore(list[position])
            }
            binding.editTask.setOnClickListener {
                listener?.onEdit(list[position])
            }
            binding.deleteTask.setOnClickListener {
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
                binding.editTask.visibility = View.GONE
                binding.deleteTask.visibility = View.GONE
                binding.icMore.visibility = View.GONE
                binding.checkbox.isClickable = false
                binding.checkbox.isFocusable = false
            } else {
                binding.checkbox.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
                    listener?.onCheckChange(it, b)
                }
            }
        }
    }

    private fun showAssignee(binding: RowTaskBinding, realmTeamTask: RealmTeamTask): Job? {
        val assigneeId = realmTeamTask.assignee
        if (assigneeId.isNullOrEmpty()) {
            binding.assignee.setText(R.string.no_assignee)
            return null
        }

        assigneeCache[assigneeId]?.let {
            binding.assignee.text = context.getString(R.string.assigned_to_colon, it)
            return null
        }

        return coroutineScope.launch(Dispatchers.IO) {
            var user: RealmUserModel? = null
            Realm.getDefaultInstance().use { realm ->
                user = realm.where(RealmUserModel::class.java).equalTo("id", assigneeId).findFirst()?.let {
                    realm.copyFromRealm(it)
                }
            }
            withContext(Dispatchers.Main) {
                val name = user?.name
                if (name != null) {
                    assigneeCache[assigneeId] = name
                    binding.assignee.text = context.getString(R.string.assigned_to_colon, name)
                } else {
                    binding.assignee.setText(R.string.no_assignee)
                }
            }
        }
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

    class ViewHolderTask(val binding: RowTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        var assigneeJob: Job? = null
    }
}
