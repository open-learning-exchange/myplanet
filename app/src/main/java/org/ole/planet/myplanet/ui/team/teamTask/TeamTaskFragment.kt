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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.nex3z.togglebuttongroup.SingleSelectToggleGroup
import io.realm.RealmResults
import io.realm.Sort
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
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.TimeUtils.formatDateTZ
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Calendar
import java.util.Date
import java.util.UUID

class TeamTaskFragment : BaseTeamFragment(), OnCompletedListener {
    private lateinit var fragmentTeamTaskBinding: FragmentTeamTaskBinding
    private var deadline: Calendar? = null
    private var datePicker: TextView? = null
    var list: List<RealmTeamTask>? = null
    private var teamTaskList: RealmResults<RealmTeamTask>? = null
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
                    formatDate(it, "MMM d, yyy hh:mm a")
                }
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
        teamTaskList = mRealm.where(RealmTeamTask::class.java).equalTo("teamId", teamId)
            .notEqualTo("status", "archived").findAllAsync()

        teamTaskList?.addChangeListener { results ->
            updatedTeamTaskList(results)
        }

        return fragmentTeamTaskBinding.root
    }

    private fun showTaskAlert(t: RealmTeamTask?) {
        val alertTaskBinding = AlertTaskBinding.inflate(layoutInflater)
        datePicker = alertTaskBinding.tvPick
        if (t != null) {
            alertTaskBinding.etTask.setText(t.title)
            alertTaskBinding.etDescription.setText(t.description)
            datePicker?.text = formatDate(t.deadline)
            deadline = Calendar.getInstance()
            deadline?.time = Date(t.deadline)
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
                setAdapter()
                alertDialog.dismiss()
            }
        }
        alertDialog.window?.setBackgroundDrawableResource(R.color.card_bg)
    }

    private fun createOrUpdateTask(task: String, desc: String, teamTask: RealmTeamTask?) {
        var realmTeamTask = teamTask
        val isCreate = realmTeamTask == null
        if (!mRealm.isInTransaction) {
            mRealm.beginTransaction()
        }
        if (realmTeamTask == null) {
            realmTeamTask = mRealm.createObject(RealmTeamTask::class.java, "${UUID.randomUUID()}")
        }
        realmTeamTask?.title = task
        realmTeamTask?.description = desc
        realmTeamTask?.deadline = deadline?.timeInMillis!!
        realmTeamTask?.teamId = teamId
        realmTeamTask?.isUpdated = true
        val ob = JsonObject()
        ob.addProperty("teams", teamId)
        realmTeamTask?.link = Gson().toJson(ob)
        val obSync = JsonObject()
        obSync.addProperty("type", "local")
        obSync.addProperty("planetCode", user?.planetCode)
        realmTeamTask?.sync = Gson().toJson(obSync)
        mRealm.commitTransaction()
        if (fragmentTeamTaskBinding.rvTask.adapter != null) {
            fragmentTeamTaskBinding.rvTask.adapter?.notifyDataSetChanged()
            showNoData(fragmentTeamTaskBinding.tvNodata, fragmentTeamTaskBinding.rvTask.adapter?.itemCount, "tasks")
        }
        Utilities.toast(activity, String.format(getString(R.string.task_s_successfully), if (isCreate) getString(R.string.added) else getString(R.string.updated)))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentTeamTaskBinding.rvTask.layoutManager = LinearLayoutManager(activity)
        list = mRealm.where(RealmTeamTask::class.java).equalTo("teamId", teamId).findAll()
        setAdapter()
        showNoData(fragmentTeamTaskBinding.tvNodata, list?.size,"tasks")
        fragmentTeamTaskBinding.taskToggle.setOnCheckedChangeListener { _: SingleSelectToggleGroup?, checkedId: Int ->
            currentTab = checkedId
            when (checkedId) {
                R.id.btn_my -> {
                    myTasks()
                }
                R.id.btn_completed -> {
                    completedTasks()
                }
                else -> {
                    allTasks()
                }
            }
            setAdapter()
        }
    }

    private fun allTasks() {
       list = mRealm.where(RealmTeamTask::class.java).equalTo("teamId", teamId)
            .notEqualTo("status", "archived").sort("completed", Sort.ASCENDING).findAll()
    }

    private fun completedTasks() {
        list = mRealm.where(RealmTeamTask::class.java).equalTo("teamId", teamId)
            .notEqualTo("status", "archived").equalTo("completed", true)
            .sort("deadline", Sort.DESCENDING).findAll()
    }

    private fun myTasks() {
        list = mRealm.where(RealmTeamTask::class.java).equalTo("teamId", teamId)
            .notEqualTo("status", "archived").equalTo("completed", false)
            .equalTo("assignee", user?.id).sort("deadline", Sort.DESCENDING).findAll()
    }

    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    private fun setAdapter() {
        if (isAdded) {
            when (currentTab) {
                R.id.btn_my -> {
                    myTasks()
                }
                R.id.btn_completed -> {
                    completedTasks()
                }
                R.id.btn_all -> {
                    allTasks()
                }
            }
            if (list!!.isEmpty()){
                showNoData(fragmentTeamTaskBinding.tvNodata, list?.size, "tasks")
            }
            else {
                showNoData(fragmentTeamTaskBinding.tvNodata, list?.size, "")
            }
            adapterTask = AdapterTask(requireContext(), mRealm, list, !isMember())
            adapterTask.setListener(this)
            fragmentTeamTaskBinding.rvTask.adapter = adapterTask
        }
    }

    override fun onCheckChange(realmTeamTask: RealmTeamTask?, b: Boolean) {
        if (!mRealm.isInTransaction) mRealm.beginTransaction()
        realmTeamTask?.completed = b
        realmTeamTask?.isUpdated = true
        realmTeamTask?.completedTime = Date().time
        mRealm.commitTransaction()
        setAdapter()
    }

    override fun onEdit(task: RealmTeamTask?) {
        showTaskAlert(task)
    }

    override fun onDelete(task: RealmTeamTask?) {
        if (!mRealm.isInTransaction) {
            mRealm.beginTransaction()
        }
        task?.deleteFromRealm()
        Utilities.toast(activity, getString(R.string.task_deleted_successfully))
        mRealm.commitTransaction()
        setAdapter()
        showNoData(fragmentTeamTaskBinding.tvNodata, fragmentTeamTaskBinding.rvTask.adapter?.itemCount, "tasks")
    }

    override fun onClickMore(realmTeamTask: RealmTeamTask?) {
        val alertUsersSpinnerBinding = AlertUsersSpinnerBinding.inflate(LayoutInflater.from(requireActivity()))
        val userList: List<RealmUserModel> = getJoinedMember(teamId, mRealm)
        val filteredUserList = userList.filter { user -> user.getFullName().isNotBlank() }
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
                val userId = user.id
                if (!mRealm.isInTransaction) {
                    mRealm.beginTransaction()
                }
                realmTeamTask?.assignee = userId
                Utilities.toast(activity, getString(R.string.assign_task_to) + " " + user.name)
                mRealm.commitTransaction()
                adapter.notifyDataSetChanged()
                setAdapter()
            }.show()
    }

    private fun updatedTeamTaskList(updatedList: RealmResults<RealmTeamTask>) {
        activity?.runOnUiThread {
            adapterTask = AdapterTask(requireContext(), mRealm, updatedList, !isMember())
            adapterTask.setListener(this)
            fragmentTeamTaskBinding.rvTask.adapter = adapterTask
            adapterTask.notifyDataSetChanged()
        }
    }
}
