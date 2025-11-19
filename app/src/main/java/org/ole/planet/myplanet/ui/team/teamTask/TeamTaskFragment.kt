package org.ole.planet.myplanet.ui.team.teamTask

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.DatePicker
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.nex3z.togglebuttongroup.SingleSelectToggleGroup
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertTaskBinding
import org.ole.planet.myplanet.databinding.AlertUsersSpinnerBinding
import org.ole.planet.myplanet.databinding.FragmentTeamTaskBinding
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getJoinedMember
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.myhealth.UserListArrayAdapter
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.ui.team.teamTask.AdapterTask.OnCompletedListener
import org.ole.planet.myplanet.utilities.TimeUtils
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.TimeUtils.formatDateTZ
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class TeamTaskFragment : BaseTeamFragment(), OnCompletedListener {
    private var _binding: FragmentTeamTaskBinding? = null
    private val binding get() = _binding!!
    private var deadline: Calendar? = null
    private var datePicker: TextView? = null
    var list: List<RealmTeamTask> = emptyList()
    private var currentTab = R.id.btn_all

    private lateinit var adapterTask: AdapterTask
    var listener = DatePickerDialog.OnDateSetListener { _: DatePicker?, year: Int, monthOfYear: Int, dayOfMonth: Int ->
            deadline = Calendar.getInstance()
            deadline?.set(Calendar.YEAR, year)
            deadline?.set(Calendar.MONTH, monthOfYear)
            deadline?.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            if (datePicker != null) {
                datePicker?.text = deadline?.timeInMillis?.let { formatDateTZ(it) }
            }
            timePicker()
        }

    private fun timePicker() {
        val timePickerDialog = TimePickerDialog(activity, { _: TimePicker?, hourOfDay: Int, minute: Int ->
            deadline?.set(Calendar.HOUR_OF_DAY, hourOfDay)
            deadline?.set(Calendar.MINUTE, minute)
            if (datePicker != null) {
                datePicker?.text = deadline?.timeInMillis?.let {
                    TimeUtils.getFormattedDateWithTime(it)
                }
            }
        }, deadline!![Calendar.HOUR_OF_DAY], deadline!![Calendar.MINUTE], true)
        timePickerDialog.show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeamTaskBinding.inflate(inflater, container, false)
        binding.fab.isVisible = false
        binding.fab.setOnClickListener { showTaskAlert(null) }
        return binding.root
    }

    private fun showTaskAlert(t: RealmTeamTask?) {
        val alertTaskBinding = AlertTaskBinding.inflate(layoutInflater)
        datePicker = alertTaskBinding.tvPick
        var selectedAssignee: RealmUserModel? = null

        if (t != null) {
            alertTaskBinding.etTask.setText(t.title)
            alertTaskBinding.etDescription.setText(t.description)
            datePicker?.text = formatDate(t.deadline)
            deadline = Calendar.getInstance()
            deadline?.time = Date(t.deadline)

            if (!t.assignee.isNullOrBlank()) {
                val assigneeUser = mRealm.where(RealmUserModel::class.java)
                    .equalTo("id", t.assignee)
                    .findFirst()
                if (assigneeUser != null) {
                    selectedAssignee = assigneeUser
                    val displayName = assigneeUser.getFullName().ifBlank {
                        assigneeUser.name ?: getString(R.string.no_assignee)
                    }
                    alertTaskBinding.tvAssignMember.text = displayName
                    alertTaskBinding.tvAssignMember.setTextColor(requireContext().getColor(R.color.daynight_textColor))
                }
            }
        }

        val myCalendar = Calendar.getInstance()
        datePicker?.setOnClickListener {
            val datePickerDialog = DatePickerDialog(requireContext(), listener, myCalendar[Calendar.YEAR], myCalendar[Calendar.MONTH], myCalendar[Calendar.DAY_OF_MONTH])
            datePickerDialog.datePicker.minDate = myCalendar.timeInMillis
            datePickerDialog.show()
        }

        // Handle member assignment
        alertTaskBinding.tvAssignMember.setOnClickListener {
            val userList: List<RealmUserModel> = getJoinedMember(teamId, mRealm)
            val filteredUserList = userList.filter { user -> user.getFullName().isNotBlank() || !user.name.isNullOrBlank() }

            if (filteredUserList.isEmpty()) {
                Toast.makeText(context, R.string.no_members_task, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val alertUsersSpinnerBinding = AlertUsersSpinnerBinding.inflate(LayoutInflater.from(requireActivity()))
            val adapter: ArrayAdapter<RealmUserModel> = UserListArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, filteredUserList)
            alertUsersSpinnerBinding.spnUser.adapter = adapter

            AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
                .setTitle(R.string.select_member)
                .setView(alertUsersSpinnerBinding.root)
                .setCancelable(false)
                .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                    val selectedItem = alertUsersSpinnerBinding.spnUser.selectedItem
                    if (selectedItem != null) {
                        selectedAssignee = selectedItem as RealmUserModel
                        val displayName = selectedAssignee.getFullName().ifBlank {
                            selectedAssignee.name ?: getString(R.string.no_assignee)
                        }
                        alertTaskBinding.tvAssignMember.text = displayName
                        alertTaskBinding.tvAssignMember.setTextColor(requireContext().getColor(R.color.daynight_textColor))
                    }
                }
                .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                    dialog.dismiss()
                }
                .show()
        }

        val titleView = TextView(requireActivity()).apply {
            text = getString(R.string.add_task)
            setTextColor(context.getColor(R.color.daynight_textColor))
            setPadding(75, 50, 0, 0)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        }
        val builder = AlertDialog.Builder(requireActivity()).setCustomTitle(titleView)
            .setView(alertTaskBinding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(getString(R.string.cancel), null)

        val alertDialog = builder.create()
        alertDialog.show()

        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val task = alertTaskBinding.etTask.text.toString()
            val desc = alertTaskBinding.etDescription.text.toString()
            if (task.isEmpty()) {
                Utilities.toast(activity, getString(R.string.task_title_is_required))
            } else if (deadline == null) {
                Utilities.toast(activity, getString(R.string.deadline_is_required))
            } else {
                createOrUpdateTask(task, desc, t, selectedAssignee?.id)
                alertDialog.dismiss()
            }
        }
        alertDialog.window?.setBackgroundDrawableResource(R.color.card_bg)
    }

    private fun createOrUpdateTask(task: String, desc: String, teamTask: RealmTeamTask?, assigneeId: String? = null) {
        val isCreate = teamTask == null
        val realmTeamTask = teamTask?.let { mRealm.copyFromRealm(it) } ?: RealmTeamTask().apply {
            id = UUID.randomUUID().toString()
        }
        realmTeamTask.title = task
        realmTeamTask.description = desc
        realmTeamTask.deadline = deadline?.timeInMillis!!
        realmTeamTask.teamId = teamId
        realmTeamTask.assignee = assigneeId
        realmTeamTask.isUpdated = true
        lifecycleScope.launch {
            teamRepository.upsertTask(realmTeamTask)

            if (!mRealm.isClosed) {
                mRealm.refresh()
            }

            if (binding.rvTask.adapter != null) {
                showNoData(binding.tvNodata, binding.rvTask.adapter?.itemCount, "tasks")
            }
            updateTasks()
            Utilities.toast(
                activity,
                String.format(
                    getString(R.string.task_s_successfully),
                    if (isCreate) getString(R.string.added) else getString(R.string.updated)
                )
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvTask.layoutManager = LinearLayoutManager(activity)
        adapterTask = AdapterTask(requireContext(), !isMemberFlow.value, viewLifecycleOwner.lifecycleScope, userRepository)
        adapterTask.setListener(this)
        binding.rvTask.adapter = adapterTask
        binding.taskToggle.setOnCheckedChangeListener { _: SingleSelectToggleGroup?, checkedId: Int ->
            currentTab = checkedId
            updateTasks()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    isMemberFlow.collectLatest { isMember ->
                        binding.fab.isVisible = isMember
                        adapterTask.nonTeamMember = !isMember
                        updateTasks()
                    }
                }
                launch {
                    teamRepository.getTasksByTeamId(teamId).collect { tasks ->
                        list = tasks
                        updateTasks()
                    }
                }
            }
        }
    }

    private fun allTasks(): List<RealmTeamTask> {
        return list.sortedWith(compareBy<RealmTeamTask> { it.completed }.thenByDescending { it.deadline })
    }

    private fun completedTasks(): List<RealmTeamTask> {
        return list.filter { it.completed }.sortedByDescending { it.deadline }
    }

    private fun myTasks(): List<RealmTeamTask> {
        return list.filter { !it.completed && it.assignee == user?.id }.sortedByDescending { it.deadline }
    }

    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    private fun updateTasks() {
        if (isAdded) {
            val taskList = when (currentTab) {
                R.id.btn_my -> myTasks()
                R.id.btn_completed -> completedTasks()
                else -> allTasks()
            }
            adapterTask.submitList(taskList)
            showNoData(binding.tvNodata, taskList.size, "tasks")
        }
    }

    override fun onCheckChange(realmTeamTask: RealmTeamTask?, completed: Boolean) {
        val taskId = realmTeamTask?.id ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            teamRepository.setTaskCompletion(taskId, completed)

            if (!mRealm.isClosed) {
                mRealm.refresh()
            }

            updateTasks()
        }
    }

    override fun onEdit(task: RealmTeamTask?) {
        showTaskAlert(task)
    }

    override fun onDelete(task: RealmTeamTask?) {
        val taskId = task?.id ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            teamRepository.deleteTask(taskId)

            if (!mRealm.isClosed) {
                mRealm.refresh()
            }

            Utilities.toast(activity, getString(R.string.task_deleted_successfully))
            updateTasks()
            showNoData(binding.tvNodata, binding.rvTask.adapter?.itemCount, "tasks")
        }
    }

    override fun onClickMore(realmTeamTask: RealmTeamTask?) {
        if (realmTeamTask?.completed == true) {
            Toast.makeText(context, R.string.cannot_assign_completed_task, Toast.LENGTH_SHORT).show()
            return
        }
        val alertUsersSpinnerBinding = AlertUsersSpinnerBinding.inflate(LayoutInflater.from(requireActivity()))
        val userList: List<RealmUserModel> = getJoinedMember(teamId, mRealm)
        val filteredUserList = userList.filter { user -> user.getFullName().isNotBlank() || !user.name.isNullOrBlank() }
        if (filteredUserList.isEmpty()) {
            Toast.makeText(context, R.string.no_members_task, Toast.LENGTH_SHORT).show()
            return
        }
        val adapter: ArrayAdapter<RealmUserModel> = UserListArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, filteredUserList)
        alertUsersSpinnerBinding.spnUser.adapter = adapter
        AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
            .setTitle(R.string.select_member)
            .setView(alertUsersSpinnerBinding.root).setCancelable(false)
            .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                val selectedItem = alertUsersSpinnerBinding.spnUser.selectedItem
                if (selectedItem == null) {
                    Toast.makeText(context, R.string.no_member_selected, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val user = selectedItem as RealmUserModel
                val taskId = realmTeamTask?.id
                if (taskId.isNullOrBlank()) {
                    Toast.makeText(context, R.string.no_tasks, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    teamRepository.assignTask(taskId, user.id)
                    Utilities.toast(activity, getString(R.string.assign_task_to) + " " + user.name)
                    adapter.notifyDataSetChanged()
                    updateTasks()
                }
            }
            .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvTask.adapter = null
        _binding = null
    }
}
