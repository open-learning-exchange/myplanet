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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nex3z.togglebuttongroup.SingleSelectToggleGroup
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertTaskBinding
import org.ole.planet.myplanet.databinding.AlertUsersSpinnerBinding
import org.ole.planet.myplanet.databinding.FragmentTeamTaskBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.ui.myhealth.UserListArrayAdapter
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.ui.team.teamTask.AdapterTask.OnCompletedListener
import org.ole.planet.myplanet.utilities.TimeUtils
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.TimeUtils.formatDateTZ
import org.ole.planet.myplanet.utilities.Utilities

class TeamTaskFragment : BaseTeamFragment(), OnCompletedListener {
    private lateinit var fragmentTeamTaskBinding: FragmentTeamTaskBinding
    private var deadline: Calendar? = null
    private var datePicker: TextView? = null
    var list: List<RealmTeamTask> = emptyList()
    private var currentTab = R.id.btn_all
    @Inject
    lateinit var teamRepository: TeamRepository
    private lateinit var adapterTask: AdapterTask
    var listener = DatePickerDialog.OnDateSetListener { _: DatePicker?, year: Int, monthOfYear: Int, dayOfMonth: Int ->
            deadline = Calendar.getInstance()
            deadline?.set(Calendar.YEAR, year)
            deadline?.set(Calendar.MONTH, monthOfYear)
            deadline?.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            datePicker?.text = deadline?.timeInMillis?.let { formatDateTZ(it) }
            timePicker()
        }

    private fun timePicker() {
        val timePickerDialog = TimePickerDialog(activity, { _: TimePicker?, hourOfDay: Int, minute: Int ->
            deadline?.set(Calendar.HOUR_OF_DAY, hourOfDay)
            deadline?.set(Calendar.MINUTE, minute)
            datePicker?.text = deadline?.timeInMillis?.let {
                TimeUtils.getFormattedDateWithTime(it)
            }
        }, deadline!![Calendar.HOUR_OF_DAY], deadline!![Calendar.MINUTE], true)
        timePickerDialog.show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentTeamTaskBinding = FragmentTeamTaskBinding.inflate(inflater, container, false)
        if (!isMember()) {
            fragmentTeamTaskBinding.fab.visibility = View.GONE
        }
        fragmentTeamTaskBinding.fab.setOnClickListener { showTaskAlert(null) }
        loadTasks()
        return fragmentTeamTaskBinding.root
    }

    private fun loadTasks() {
        lifecycleScope.launch {
            val filter = when (currentTab) {
                R.id.btn_my -> org.ole.planet.myplanet.repository.TaskFilter.MY_TASKS
                R.id.btn_completed -> org.ole.planet.myplanet.repository.TaskFilter.COMPLETED
                else -> org.ole.planet.myplanet.repository.TaskFilter.ALL
            }
            list = teamRepository.getTasks(teamId, filter, user?.id ?: "")
            setAdapter()
        }
    }

    private fun showTaskAlert(t: RealmTeamTask?) {
        val alertTaskBinding = AlertTaskBinding.inflate(layoutInflater)
        datePicker = alertTaskBinding.tvPick
        if (t != null) {
            alertTaskBinding.etTask.setText(t.title)
            alertTaskBinding.etDescription.setText(t.description)
            datePicker?.text = formatDate(t.deadline)
            deadline = Calendar.getInstance()
            deadline?.time = java.util.Date(t.deadline)
        }
        val myCalendar = Calendar.getInstance()
        datePicker?.setOnClickListener {
            val datePickerDialog = DatePickerDialog(requireContext(), listener, myCalendar[Calendar.YEAR], myCalendar[Calendar.MONTH], myCalendar[Calendar.DAY_OF_MONTH])
            datePickerDialog.datePicker.minDate = myCalendar.timeInMillis
            datePickerDialog.show()
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
                createOrUpdateTask(task, desc, t)
                alertDialog.dismiss()
            }
        }
        alertDialog.window?.setBackgroundDrawableResource(R.color.card_bg)
    }

    private fun createOrUpdateTask(task: String, desc: String, teamTask: RealmTeamTask?) {
        lifecycleScope.launch {
            teamRepository.createOrUpdateTask(
                teamTask?._id, task, desc, deadline?.timeInMillis!!, teamId, user?.planetCode ?: "", user?.parentCode ?: ""
            )
            Utilities.toast(activity, String.format(getString(R.string.task_s_successfully), if (teamTask == null) getString(R.string.added) else getString(R.string.updated)))
            loadTasks()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentTeamTaskBinding.rvTask.layoutManager = LinearLayoutManager(activity)
        loadTasks()
        showNoData(fragmentTeamTaskBinding.tvNodata, list.size,"tasks")
        fragmentTeamTaskBinding.taskToggle.setOnCheckedChangeListener { _: SingleSelectToggleGroup?, checkedId: Int ->
            currentTab = checkedId
            loadTasks()
        }
    }

    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    private fun setAdapter() {
        if (isAdded) {
            if (list.isEmpty()){
                showNoData(fragmentTeamTaskBinding.tvNodata, list.size, "tasks")
            } else {
                showNoData(fragmentTeamTaskBinding.tvNodata, list.size, "")
            }
            adapterTask = AdapterTask(requireContext(), list, !isMember())
            adapterTask.setListener(this)
            fragmentTeamTaskBinding.rvTask.adapter = adapterTask
            lifecycleScope.launch {
                val users = teamRepository.getJoinedMembers(teamId)
                val userMap = users.associateBy({ it.id }, { it.name })
                adapterTask.setUsers(userMap)
            }
        }
    }

    override fun onCheckChange(realmTeamTask: RealmTeamTask?, b: Boolean) {
        lifecycleScope.launch {
            realmTeamTask?._id?.let {
                teamRepository.setTaskCompleted(it, b)
                loadTasks()
            }
        }
    }

    override fun onEdit(task: RealmTeamTask?) {
        showTaskAlert(task)
    }

    override fun onDelete(task: RealmTeamTask?) {
        lifecycleScope.launch {
            task?._id?.let {
                teamRepository.deleteTask(it)
                Utilities.toast(activity, getString(R.string.task_deleted_successfully))
                loadTasks()
            }
        }
        showNoData(fragmentTeamTaskBinding.tvNodata, list.size - 1, "tasks")
    }

    override fun onClickMore(realmTeamTask: RealmTeamTask?) {
        lifecycleScope.launch {
            val userList = teamRepository.getJoinedMembers(teamId)
            val filteredUserList = userList.filter { user -> user.getFullName().isNotBlank() }
            if (filteredUserList.isEmpty()) {
                Toast.makeText(context, R.string.no_members_task, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val alertUsersSpinnerBinding = AlertUsersSpinnerBinding.inflate(LayoutInflater.from(requireActivity()))
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
                    lifecycleScope.launch {
                        realmTeamTask?._id?.let {
                            teamRepository.assignTask(it, user.id)
                            Utilities.toast(activity, getString(R.string.assign_task_to) + " " + user.name)
                            loadTasks()
                        }
                    }
                }.show()
        }
    }
}
