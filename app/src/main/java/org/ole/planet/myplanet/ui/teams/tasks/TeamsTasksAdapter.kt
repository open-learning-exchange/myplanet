package org.ole.planet.myplanet.ui.teams.tasks

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnTaskCompletedListener
import org.ole.planet.myplanet.databinding.RowTaskBinding
import org.ole.planet.myplanet.model.TeamTaskItem
import org.ole.planet.myplanet.ui.teams.tasks.TeamsTasksAdapter.TeamsTasksViewHolder
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.TimeUtils.formatDate

class TeamsTasksAdapter(
    private val context: Context,
    var nonTeamMember: Boolean
) : ListAdapter<TeamTaskItem, TeamsTasksViewHolder>(DIFF_CALLBACK) {
    private val assigneeCache: MutableMap<String, String> = mutableMapOf()
    private var listener: OnTaskCompletedListener? = null
    fun setListener(listener: OnTaskCompletedListener?) {
        this.listener = listener
    }

    fun hasAssignee(id: String): Boolean = assigneeCache.containsKey(id)

    fun getKnownAssigneeIds(): Set<String> = assigneeCache.keys.toSet()

    fun updateAssignees(newAssignees: Map<String, String>) {
        assigneeCache.putAll(newAssignees)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamsTasksViewHolder {
        val binding = RowTaskBinding.inflate(LayoutInflater.from(context), parent, false)
        return TeamsTasksViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TeamsTasksViewHolder, position: Int) {
        val it = getItem(position)
        val binding = holder.binding
        binding.checkbox.setOnCheckedChangeListener(null)
        binding.checkbox.text = it.title
        binding.checkbox.isChecked = it.completed
        if (!it.completed) {
            binding.deadline.text =
                context.getString(R.string.deadline_colon, formatDate(it.deadline))
        } else {
            binding.deadline.text = context.getString(
                R.string.two_strings,
                context.getString(R.string.deadline_colon, formatDate(it.deadline)),
                context.getString(R.string.completed_colon, formatDate(it.deadline))
            )
        }
        showAssignee(binding, it)
        binding.icMore.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                listener?.onClickMore(getItem(adapterPosition).id)
            }
        }
        binding.editTask.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                listener?.onEdit(getItem(adapterPosition).id)
            }
        }
        binding.deleteTask.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                listener?.onDelete(getItem(adapterPosition).id)
            }
        }
        holder.itemView.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                val item = getItem(adapterPosition)
                val alertDialog = AlertDialog.Builder(context, R.style.AlertDialogTheme)
                    .setTitle(item.title)
                    .setMessage(item.description)
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }.create()

                alertDialog.show()
            }
        }
        if (nonTeamMember) {
            binding.editTask.visibility = View.GONE
            binding.deleteTask.visibility = View.GONE
            binding.icMore.visibility = View.GONE
            binding.checkbox.isClickable = false
            binding.checkbox.isFocusable = false
        } else {
            binding.checkbox.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
                listener?.onCheckChange(it.id, b)
            }
        }
    }

    private fun showAssignee(binding: RowTaskBinding, realmTeamTask: TeamTaskItem) {
        val assigneeId = realmTeamTask.assignee
        if (assigneeId.isNullOrEmpty()) {
            binding.assignee.setText(R.string.no_assignee)
            return
        }

        val name = assigneeCache[assigneeId]
        if (name != null) {
            binding.assignee.text = context.getString(R.string.assigned_to_colon, name)
        } else {
            binding.assignee.setText(R.string.no_assignee)
        }
    }

    class TeamsTasksViewHolder(val binding: RowTaskBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        val DIFF_CALLBACK = DiffUtils.itemCallback<TeamTaskItem>(
            areItemsTheSame = { old, new -> old.id == new.id },
            areContentsTheSame = { old, new ->
                old.title == new.title &&
                        old.description == new.description &&
                        old.deadline == new.deadline &&
                        old.completed == new.completed &&
                        old.assignee == new.assignee
            }
        )
    }
}
