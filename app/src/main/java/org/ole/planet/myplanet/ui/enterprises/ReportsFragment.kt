package org.ole.planet.myplanet.ui.enterprises

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.JsonObject
import io.realm.RealmResults
import io.realm.Sort
import org.ole.planet.myplanet.databinding.DialogAddReportBinding
import org.ole.planet.myplanet.databinding.FragmentReportsBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.insertReports
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import java.util.Calendar

class ReportsFragment : BaseTeamFragment() {
    private lateinit var fragmentReportsBinding: FragmentReportsBinding
    var list: RealmResults<RealmMyTeam>? = null
    private lateinit var adapterReports: AdapterReports
    private var startTimeStamp: String? = null
    private var endTimeStamp: String? = null
    lateinit var teamType: String

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentReportsBinding = FragmentReportsBinding.inflate(inflater, container, false)
        mRealm = DatabaseService(requireActivity()).realmInstance
        fragmentReportsBinding.addReports.setOnClickListener{
            val dialogAddReportBinding = DialogAddReportBinding.inflate(LayoutInflater.from(requireContext()))
            val v: View = dialogAddReportBinding.root
            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle("add report")
                .setView(v)
                .setPositiveButton("submit", null)
                .setNegativeButton("cancel", null)
            val dialog = builder.create()
            dialog.show()
            val submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            val firstDayOfMonth = "${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.YEAR)}"
            startTimeStamp = "${calendar.timeInMillis}"

            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            val lastDayOfMonth = "${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.YEAR)}"
            endTimeStamp = "${calendar.timeInMillis}"

            dialogAddReportBinding.startDate.text = firstDayOfMonth
            dialogAddReportBinding.endDate.text = lastDayOfMonth

            dialogAddReportBinding.ltStartDate.setOnClickListener {
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                val day = calendar.get(Calendar.DAY_OF_MONTH)

                val dpd = DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
                    dialogAddReportBinding.startDate.text = "$selectedDay/${selectedMonth + 1}/$selectedYear"
                    calendar.set(Calendar.YEAR, selectedYear)
                    calendar.set(Calendar.MONTH, selectedMonth)
                    calendar.set(Calendar.DAY_OF_MONTH, selectedDay)

                    startTimeStamp = "${calendar.timeInMillis}"
                }, year, month, day)

                dpd.show()
            }

            dialogAddReportBinding.ltEndDate.setOnClickListener {
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                val day = calendar.get(Calendar.DAY_OF_MONTH)

                val dpd = DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
                    dialogAddReportBinding.endDate.text = "$selectedDay/${selectedMonth + 1}/$selectedYear"
                    calendar.set(Calendar.YEAR, selectedYear)
                    calendar.set(Calendar.MONTH, selectedMonth)
                    calendar.set(Calendar.DAY_OF_MONTH, selectedDay)

                    endTimeStamp = "${calendar.timeInMillis}"
                }, year, month, day)

                dpd.show()
            }

            submit.setOnClickListener {
                if (dialogAddReportBinding.startDate.text == "Start Date"){
                    dialogAddReportBinding.startDate.error = "start date is required"
                } else if (dialogAddReportBinding.endDate.text == "End Date"){
                    dialogAddReportBinding.endDate.error = "start date is required"
                } else if (TextUtils.isEmpty("${dialogAddReportBinding.summary.text}")) {
                    dialogAddReportBinding.summary.error = "summary is required"
                } else if (TextUtils.isEmpty("${dialogAddReportBinding.beginningBalance.text}")) {
                    dialogAddReportBinding.beginningBalance.error = "beginning balance is required"
                } else if (TextUtils.isEmpty("${dialogAddReportBinding.sales.text}")) {
                    dialogAddReportBinding.sales.error = "sales is required"
                } else if (TextUtils.isEmpty("${dialogAddReportBinding.otherIncome.text}")) {
                    dialogAddReportBinding.otherIncome.error = "other income is required"
                } else if (TextUtils.isEmpty("${dialogAddReportBinding.personnel.text}")) {
                    dialogAddReportBinding.personnel.error = "personnel is required"
                } else if (TextUtils.isEmpty("${dialogAddReportBinding.nonPersonnel.text}")) {
                    dialogAddReportBinding.nonPersonnel.error = "non-personnel is required"
                } else {
                    val doc = JsonObject().apply {
                        addProperty("createdDate", System.currentTimeMillis())
                        addProperty("description", "${dialogAddReportBinding.summary.text}")
                        addProperty("beginningBalance", "${dialogAddReportBinding.beginningBalance.text}")
                        addProperty("sales", "${dialogAddReportBinding.sales.text}")
                        addProperty("otherIncome", "${dialogAddReportBinding.otherIncome.text}")
                        addProperty("wages", "${dialogAddReportBinding.personnel.text}")
                        addProperty("otherExpenses", "${dialogAddReportBinding.nonPersonnel.text}")
                        addProperty("startDate", startTimeStamp)
                        addProperty("endDate", endTimeStamp)
                        addProperty("updatedDate", System.currentTimeMillis())
                        addProperty("teamId", teamId)
                        addProperty("teamType", team.teamType)
                        addProperty("teamPlanetCode", team.teamPlanetCode)
                        addProperty("docType", "report")
                        addProperty("updated", true)
                    }
                    insertReports(doc, mRealm)
                    dialog.dismiss()
                }
            }

            cancel.setOnClickListener { dialog.dismiss() }
        }

        list = mRealm.where(RealmMyTeam::class.java).equalTo("teamId", teamId)
            .equalTo("docType", "report")
            .sort("date", Sort.DESCENDING).findAllAsync()

        list?.addChangeListener { results ->
            updatedReportsList(results)
        }

        return fragmentReportsBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        list = mRealm.where(RealmMyTeam::class.java).equalTo("teamId", teamId)
            .equalTo("docType", "report")
            .sort("date", Sort.DESCENDING).findAll()
        updatedReportsList(list as RealmResults<RealmMyTeam>)
    }

    private fun updatedReportsList(results: RealmResults<RealmMyTeam>) {
        activity?.runOnUiThread {
            adapterReports = AdapterReports(requireContext(), results)
            fragmentReportsBinding.rvReports.layoutManager = LinearLayoutManager(activity)
            fragmentReportsBinding.rvReports.adapter = adapterReports
            adapterReports.notifyDataSetChanged()
        }
    }
}