package org.ole.planet.myplanet.ui.enterprises

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
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
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.Utilities
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
        prefData = SharedPrefManager(requireContext())
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

        fragmentReportsBinding.exportCSV.setOnClickListener {
            val currentDate = Date()
            val dateFormat = SimpleDateFormat("EEE_MMM_dd_yyyy", Locale.US)
            val formattedDate = dateFormat.format(currentDate)
            val teamName = prefData.getTEAMNAME()?.replace(" ", "_")

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/csv"
                putExtra(Intent.EXTRA_TITLE, "Report_of_${teamName}_Financial_Report_Summary_on_${formattedDate}")
            }
            startActivityForResult(intent, CREATE_FILE_REQUEST_CODE)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_FILE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_CANCELED) {
                Utilities.toast(requireContext(), "export cancelled.")
            } else if (resultCode == Activity.RESULT_OK) {
                data?.data?.also { uri ->
                    try {
                        val reports = mRealm.where(RealmMyTeam::class.java).equalTo("teamId", teamId)
                            .equalTo("docType", "report")
                            .sort("date", Sort.DESCENDING).findAll()
                        val csvBuilder = StringBuilder()
                        csvBuilder.append("${prefData.getTEAMNAME()} Financial Report Summary\n\n")
                        csvBuilder.append("Start Date, End Date, Created Date, Updated Date, Beginning Balance, Sales, Other Income, Wages, Other Expenses, Profit/Loss, Ending Balance\n")
                        for (report in reports) {
                            val dateFormat = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z (z)", Locale.US)
                            val totalIncome = report.sales + report.otherIncome
                            val totalExpenses = report.wages + report.otherExpenses
                            val profitLoss = totalIncome - totalExpenses
                            val endingBalance = profitLoss + report.beginningBalance
                            csvBuilder.append("${dateFormat.format(report.startDate)}, ${dateFormat.format(report.endDate)}, ${dateFormat.format(report.createdDate)}, ${dateFormat.format(report.updatedDate)}, ${report.beginningBalance}, ${report.sales}, ${report.otherIncome}, ${report.wages}, ${report.otherExpenses}, $profitLoss, $endingBalance\n")
                        }

                        val outputStream: OutputStream? = requireContext().contentResolver.openOutputStream(uri)
                        outputStream?.write(csvBuilder.toString().toByteArray())
                        outputStream?.close()
                        Utilities.toast(requireContext(), "CSV file saved successfully.")
                    } catch (e: IOException) {
                        Utilities.toast(requireContext(), "Failed to save CSV file.")
                    }
                }
            }
        }
    }

    companion object {
        private const val CREATE_FILE_REQUEST_CODE = 1
    }
}