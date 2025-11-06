package org.ole.planet.myplanet.ui.team.teamTask

import android.app.AlertDialog
import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowTaskBinding
import org.ole.planet.myplanet.ui.team.teamTask.AdapterTask.ViewHolderTask
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate

class AdapterTask(
    private val context: Context,
    private val list: List<Task>?,
    private val nonTeamMember: Boolean
) : RecyclerView.Adapter<ViewHolderTask>() {
    private var listener: OnCompletedListener? = null
    fun setListener(listener: OnCompletedListener?) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderTask {
        val binding = RowTaskBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderTask(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderTask, position: Int) {
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
            showAssignee(binding, it)
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

    private fun showAssignee(binding: RowTaskBinding, task: Task) {
        if (!TextUtils.isEmpty(task.assigneeName)) {
            binding.assignee.text = context.getString(R.string.assigned_to_colon, task.assigneeName)
        } else {
            binding.assignee.setText(R.string.no_assignee)
        }
    }

    override fun getItemCount(): Int {
        return list?.size ?: 0
    }

    interface OnCompletedListener {
        fun onCheckChange(realmTeamTask: Task?, b: Boolean)
        fun onEdit(task: Task?)
        fun onDelete(task: Task?)
        fun onClickMore(realmTeamTask: Task?)
    }

    class ViewHolderTask(val binding: RowTaskBinding) : RecyclerView.ViewHolder(binding.root)
}
