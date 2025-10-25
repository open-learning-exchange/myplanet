package org.ole.planet.myplanet.ui.team.teamTask

import android.app.AlertDialog
import android.content.Context
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
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate

class AdapterTask(
    private val context: Context,
    private val realm: Realm,
    private val list: List<RealmTeamTask>?,
    private val nonTeamMember: Boolean
) : RecyclerView.Adapter<ViewHolderTask>() {
    private var listener: OnCompletedListener? = null
    private val assigneeCache: MutableMap<String, String> = mutableMapOf()

    init {
        preloadAssigneeCache(list)
    }
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

    private fun showAssignee(binding: RowTaskBinding, realmTeamTask: RealmTeamTask) {
        val assigneeId = realmTeamTask.assignee
        if (assigneeId.isNullOrBlank()) {
            binding.assignee.setText(R.string.no_assignee)
            return
        }

        val assigneeName = assigneeCache[assigneeId] ?: fetchAndCacheAssignee(assigneeId)

        if (!assigneeName.isNullOrBlank()) {
            binding.assignee.text = context.getString(R.string.assigned_to_colon, assigneeName)
        } else {
            binding.assignee.setText(R.string.no_assignee)
        }
    }

    private fun preloadAssigneeCache(tasks: List<RealmTeamTask>?) {
        assigneeCache.clear()
        val assigneeIds = tasks
            ?.mapNotNull { it.assignee?.takeIf { id -> id.isNotBlank() } }
            ?.distinct()
            ?: emptyList()

        if (assigneeIds.isEmpty()) {
            return
        }

        val users = realm.copyFromRealm(
            realm.where(RealmUserModel::class.java)
                .`in`("id", assigneeIds.toTypedArray())
                .findAll()
        )

        users.forEach { user ->
            val id = user.id
            val name = getDisplayName(user)
            if (!id.isNullOrBlank() && !name.isNullOrBlank()) {
                assigneeCache[id] = name
            }
        }
    }

    private fun fetchAndCacheAssignee(assigneeId: String): String? {
        val model = realm.where(RealmUserModel::class.java).equalTo("id", assigneeId).findFirst()
            ?: return null
        val unmanagedModel = realm.copyFromRealm(model)
        val name = getDisplayName(unmanagedModel)
        if (!name.isNullOrBlank()) {
            assigneeCache[assigneeId] = name
        }
        return name
    }

    private fun getDisplayName(user: RealmUserModel): String? {
        val nameFromParts = listOfNotNull(
            user.firstName?.trim(),
            user.middleName?.trim(),
            user.lastName?.trim()
        )
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }

        val username = user.name?.trim()?.takeIf { it.isNotBlank() }

        return nameFromParts ?: username
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

    class ViewHolderTask(val binding: RowTaskBinding) : RecyclerView.ViewHolder(binding.root)
}
