package org.ole.planet.myplanet.ui.teams.tasks

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnTaskCompletedListener
import org.ole.planet.myplanet.databinding.RowTaskBinding
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.TaskStatus
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.model.effectiveStatus
import org.ole.planet.myplanet.ui.teams.InlineCommentsAdapter
import org.ole.planet.myplanet.ui.teams.tasks.TeamsTasksAdapter.TeamsTasksViewHolder
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.TimeUtils.formatDate

class TeamsTasksAdapter(
    private val context: Context,
    nonTeamMember: Boolean
) : ListAdapter<TeamTask, TeamsTasksViewHolder>(DIFF_CALLBACK) {
    var nonTeamMember: Boolean = nonTeamMember
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }
    private val assigneeCache: MutableMap<String, String> = mutableMapOf()
    private val commentsMap: MutableMap<String, List<News>> = mutableMapOf()
    private val expandedTaskIds: MutableSet<String> = mutableSetOf()
    private var currentUserId: String? = null
    private var isLeader: Boolean = false
    private var listener: OnTaskCompletedListener? = null
    private var onSendCommentListener: ((taskId: String, message: String) -> Unit)? = null
    private var onDeleteCommentListener: ((News) -> Unit)? = null

    fun setListener(listener: OnTaskCompletedListener?) {
        this.listener = listener
    }

    fun setOnCommentActions(
        onSend: (taskId: String, message: String) -> Unit,
        onDelete: (News) -> Unit
    ) {
        this.onSendCommentListener = onSend
        this.onDeleteCommentListener = onDelete
    }

    fun setCurrentUser(userId: String?, leader: Boolean) {
        currentUserId = userId
        isLeader = leader
        notifyDataSetChanged()
    }

    fun updateComments(newCommentsMap: Map<String, List<News>>) {
        commentsMap.clear()
        commentsMap.putAll(newCommentsMap)
        notifyDataSetChanged()
    }

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
        binding.title.text = it.title

        val status = it.effectiveStatus()
        val statusText = statusLabel(status)
        val statusColor = when (status) {
            TaskStatus.COMPLETED.value -> ContextCompat.getColor(context, R.color.md_green_600)
            TaskStatus.IN_PROGRESS.value -> ContextCompat.getColor(context, R.color.md_amber_700)
            else -> ContextCompat.getColor(context, R.color.md_blue_600)
        }
        binding.statusBadge.text = if (nonTeamMember) statusText else context.getString(R.string.status_dropdown, statusText)
        binding.statusBadge.backgroundTintList = ColorStateList.valueOf(statusColor)
        binding.statusBadge.alpha = if (nonTeamMember) 0.6f else 1.0f

        binding.statusBadge.isClickable = !nonTeamMember
        binding.statusBadge.setOnClickListener { anchor ->
            if (nonTeamMember) return@setOnClickListener
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                showStatusMenu(anchor, getItem(adapterPosition))
            }
        }

        if (!it.completed || it.completedTime <= 0) {
            binding.deadline.text =
                context.getString(R.string.deadline_colon, formatDate(it.deadline))
        } else {
            binding.deadline.text = context.getString(
                R.string.two_strings,
                context.getString(R.string.deadline_colon, formatDate(it.deadline)),
                context.getString(R.string.completed_colon, formatDate(it.completedTime))
            )
        }
        showAssignee(binding, it)
        binding.icMore.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                listener?.onClickMore(getItem(adapterPosition))
            }
        }
        binding.editTask.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                listener?.onEdit(getItem(adapterPosition))
            }
        }
        binding.deleteTask.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                listener?.onDelete(getItem(adapterPosition))
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

        val memberVisibility = if (nonTeamMember) View.GONE else View.VISIBLE
        binding.editTask.visibility = memberVisibility
        binding.deleteTask.visibility = memberVisibility
        binding.icMore.visibility = memberVisibility
        binding.llCommentInput.visibility = memberVisibility

        val taskId = it.id ?: ""
        val comments = commentsMap[taskId] ?: emptyList()
        binding.tvCommentCount.text = context.getString(R.string.comments_count, comments.size)

        val isExpanded = expandedTaskIds.contains(taskId)
        binding.llCommentsContainer.isVisible = isExpanded
        binding.ivExpandComments.setImageResource(
            if (isExpanded) R.drawable.ic_keyboard_arrow_up_black_24dp
            else R.drawable.ic_keyboard_arrow_down_black_24dp
        )

        val commentsAdapter = InlineCommentsAdapter(
            currentUserId = currentUserId,
            isLeader = isLeader,
            onDeleteComment = { comment -> onDeleteCommentListener?.invoke(comment) }
        )
        binding.rvComments.layoutManager = LinearLayoutManager(context)
        binding.rvComments.adapter = commentsAdapter
        commentsAdapter.submitList(comments)

        binding.llCommentToggle.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            val currentId: String = if (adapterPosition != RecyclerView.NO_POSITION && adapterPosition < itemCount) {
                this@TeamsTasksAdapter.getItem(adapterPosition).id ?: ""
            } else {
                taskId
            }

            if (expandedTaskIds.contains(currentId)) {
                expandedTaskIds.remove(currentId)
            } else {
                expandedTaskIds.add(currentId)
            }
            if (adapterPosition != RecyclerView.NO_POSITION && adapterPosition < itemCount) {
                notifyItemChanged(adapterPosition)
            } else {
                val isExp = expandedTaskIds.contains(currentId)
                binding.llCommentsContainer.isVisible = isExp
                binding.ivExpandComments.setImageResource(
                    if (isExp) R.drawable.ic_keyboard_arrow_up_black_24dp
                    else R.drawable.ic_keyboard_arrow_down_black_24dp
                )
            }
        }

        binding.btnSendComment.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            val currentId: String = if (adapterPosition != RecyclerView.NO_POSITION && adapterPosition < itemCount) {
                this@TeamsTasksAdapter.getItem(adapterPosition).id ?: ""
            } else {
                taskId
            }

            val commentText = binding.etComment.text?.toString()?.trim().orEmpty()
            if (commentText.isNotEmpty()) {
                onSendCommentListener?.invoke(currentId, commentText)
                binding.etComment.setText("")
            }
        }
    }

    private fun showStatusMenu(anchor: View, task: TeamTask) {
        val current = task.effectiveStatus()
        val popup = PopupMenu(context, anchor)
        TaskStatus.entries.forEachIndexed { index, status ->
            popup.menu.add(Menu.NONE, index, index, statusLabel(status.value))
        }
        popup.setOnMenuItemClickListener { item ->
            val selected = TaskStatus.entries[item.itemId].value
            if (selected != current) listener?.onStatusChange(task, selected)
            true
        }
        popup.show()
    }

    private fun statusLabel(status: String): String = when (status) {
        TaskStatus.COMPLETED.value -> context.getString(R.string.status_completed)
        TaskStatus.IN_PROGRESS.value -> context.getString(R.string.status_in_progress)
        else -> context.getString(R.string.status_todo)
    }

    private fun showAssignee(binding: RowTaskBinding, realmTeamTask: TeamTask) {
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
        private val DIFF_CALLBACK = DiffUtils.itemCallback<TeamTask>(
            areItemsTheSame = { old, new -> old.id == new.id },
            areContentsTheSame = { old, new ->
                old.title == new.title &&
                        old.description == new.description &&
                        old.deadline == new.deadline &&
                        old.completed == new.completed &&
                        old.effectiveStatus() == new.effectiveStatus() &&
                        old.assignee == new.assignee
            }
        )
    }
}
