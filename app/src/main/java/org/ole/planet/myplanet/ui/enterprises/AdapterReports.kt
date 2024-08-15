package org.ole.planet.myplanet.ui.enterprises

import android.app.DatePickerDialog
import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmResults
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.DialogAddReportBinding
import org.ole.planet.myplanet.databinding.ReportListItemBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils
import java.util.Calendar

class AdapterReports(private val context: Context, private var list: RealmResults<RealmMyTeam>) : RecyclerView.Adapter<AdapterReports.ViewHolderReports>() {
    private lateinit var reportListItemBinding: ReportListItemBinding
    private var startTimeStamp: String? = null
    private var endTimeStamp: String? = null
    lateinit var prefData: SharedPrefManager
    private var mRealm: Realm = Realm.getDefaultInstance()
    private var nonTeamMember = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderReports {
        reportListItemBinding = ReportListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        prefData = SharedPrefManager(context)
        return ViewHolderReports(reportListItemBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderReports, position: Int) {
        if (nonTeamMember) {
            reportListItemBinding.edit.visibility = View.GONE
            reportListItemBinding.delete.visibility = View.GONE
        }
        val report = list[position]
        reportListItemBinding.tvReportTitle.text = context.getString(R.string.team_financial_report, prefData.getTeamName())
        report?.let {
            with(reportListItemBinding) {
                val totalIncome = report.sales + report.otherIncome
                val totalExpenses = report.wages + report.otherExpenses
                val profitLoss = totalIncome - totalExpenses

                date.text = context.getString(R.string.string_range, TimeUtils.formatDate(it.startDate, " MMM dd, yyyy"), TimeUtils.formatDate(it.endDate, "MMM dd, yyyy"))
                beginningBalanceValue.text = context.getString(R.string.number_placeholder, it.beginningBalance)
                salesValue.text = context.getString(R.string.number_placeholder, it.sales)
                otherValue.text = context.getString(R.string.number_placeholder, it.otherIncome)
                totalIncomeValue.text = context.getString(R.string.number_placeholder, totalIncome)
                personnelValue.text = context.getString(R.string.number_placeholder, it.wages)
                nonPersonnelValue.text = context.getString(R.string.number_placeholder, it.otherExpenses)
                totalExpensesValue.text = context.getString(R.string.number_placeholder, totalExpenses)
                profitLossValue.text = context.getString(R.string.number_placeholder, profitLoss)
                endingBalanceValue.text = context.getString(R.string.number_placeholder, profitLoss + it.beginningBalance)
                tvReportDetails.text = context.getString(R.string.message_placeholder, it.description)
                createUpdate.text = context.getString(R.string.report_date_details, TimeUtils.formatDate(it.createdDate, "MMM dd, yyyy"), TimeUtils.formatDate(it.updatedDate, "MMM dd, yyyy"))
            }
        }

        reportListItemBinding.edit.setOnClickListener {
            val dialogAddReportBinding = DialogAddReportBinding.inflate(LayoutInflater.from(context))
            val v: View = dialogAddReportBinding.root
            val builder = AlertDialog.Builder(context, R.style.AlertDialogTheme)
            builder.setTitle("Edit Report")
                .setView(v)
                .setPositiveButton("submit", null)
                .setNegativeButton("cancel", null)
            val dialog = builder.create()
            dialog.show()
            val submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_MONTH, 1)

            dialogAddReportBinding.startDate.text = context.getString(R.string.message_placeholder, report?.let { it1 -> TimeUtils.formatDate(it1.startDate, " MMM dd, yyyy") })
            dialogAddReportBinding.endDate.text = context.getString(R.string.message_placeholder, report?.let { it1 -> TimeUtils.formatDate(it1.endDate, " MMM dd, yyyy") })
            dialogAddReportBinding.summary.setText(context.getString(R.string.message_placeholder, report?.description))
            dialogAddReportBinding.beginningBalance.setText(context.getString(R.string.number_placeholder, report?.beginningBalance))
            dialogAddReportBinding.sales.setText(context.getString(R.string.number_placeholder, report?.sales))
            dialogAddReportBinding.otherIncome.setText(context.getString(R.string.number_placeholder, report?.otherIncome))
            dialogAddReportBinding.personnel.setText(context.getString(R.string.number_placeholder, report?.wages))
            dialogAddReportBinding.nonPersonnel.setText(context.getString(R.string.number_placeholder, report?.otherExpenses))

            dialogAddReportBinding.ltStartDate.setOnClickListener {
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                val day = calendar.get(Calendar.DAY_OF_MONTH)

                val dpd = DatePickerDialog(context, { _, selectedYear, selectedMonth, selectedDay ->
                    dialogAddReportBinding.startDate.text = context.getString(R.string.formatted_date, selectedDay, selectedMonth + 1, selectedYear)
                    calendar.set(Calendar.YEAR, selectedYear)
                    calendar.set(Calendar.MONTH, selectedMonth)
                    calendar.set(Calendar.DAY_OF_MONTH, selectedDay)

                    startTimeStamp = context.getString(R.string.number_placeholder, calendar.timeInMillis)
                }, year, month, day)

                dpd.show()
            }

            dialogAddReportBinding.ltEndDate.setOnClickListener {
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                val day = calendar.get(Calendar.DAY_OF_MONTH)

                val dpd = DatePickerDialog(context, { _, selectedYear, selectedMonth, selectedDay ->
                    dialogAddReportBinding.endDate.text = context.getString(R.string.formatted_date, selectedDay, selectedMonth + 1, selectedYear)
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
                        if (report != null) {
                            addProperty("_id", report._id)
                        }
                        addProperty("description", "${dialogAddReportBinding.summary.text}")
                        addProperty("beginningBalance", "${dialogAddReportBinding.beginningBalance.text}")
                        addProperty("sales", "${dialogAddReportBinding.sales.text}")
                        addProperty("otherIncome", "${dialogAddReportBinding.otherIncome.text}")
                        addProperty("wages", "${dialogAddReportBinding.personnel.text}")
                        addProperty("otherExpenses", "${dialogAddReportBinding.nonPersonnel.text}")
                        addProperty("startDate", startTimeStamp)
                        addProperty("endDate", endTimeStamp)
                        addProperty("updatedDate", System.currentTimeMillis())
                        addProperty("updated", true)
                    }
                    RealmMyTeam.updateReports(doc, mRealm)
                    dialog.dismiss()
                }
            }

            cancel.setOnClickListener { dialog.dismiss() }
        }

        reportListItemBinding.delete.setOnClickListener {
            report?._id?.let { reportId ->
                val builder = AlertDialog.Builder(context)
                builder.setTitle("Delete Report")
                    .setMessage(R.string.delete_record)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        RealmMyTeam.deleteReport(reportId, mRealm)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

        }

    }

    override fun getItemCount(): Int {
        return list.size
    }

    fun setNonTeamMember(nonTeamMember: Boolean) {
        this.nonTeamMember = nonTeamMember
    }

    class ViewHolderReports(reportListItemBinding: ReportListItemBinding) : RecyclerView.ViewHolder(reportListItemBinding.root)
}
