package org.ole.planet.myplanet.ui.enterprises

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.realm.RealmResults
import org.ole.planet.myplanet.databinding.ReportListItemBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.utilities.TimeUtils

class AdapterReports(private var list: RealmResults<RealmMyTeam>) : RecyclerView.Adapter<AdapterReports.ViewHolderReports>() {
    private lateinit var reportListItemBinding: ReportListItemBinding
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderReports {
        reportListItemBinding = ReportListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderReports(reportListItemBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderReports, position: Int) {
        val report = list[position]
        report?.let {
            with(reportListItemBinding) {
                val totalIncome = report.sales + report.otherIncome
                val totalExpenses = report.wages + report.otherExpenses
                val profitLoss = totalIncome - totalExpenses

                date.text = "${TimeUtils.formatDate(it.startDate, " MMM dd, yyyy")} - ${TimeUtils.formatDate(it.endDate, "MMM dd, yyyy")}"
                beginningBalanceValue.text = "${it.beginningBalance}"
                salesValue.text = "${it.sales}"
                otherValue.text = "${it.otherIncome}"
                totalIncomeValue.text = "$totalIncome"
                personnelValue.text = "${it.wages}"
                nonPersonnelValue.text = "${it.otherExpenses}"
                totalExpensesValue.text = "$totalExpenses"
                profitLossValue.text = "$profitLoss"
                endingBalanceValue.text = "${(profitLoss + it.beginningBalance)}"
                tvReportDetails.text = it.description
                createUpdate.text = "Report created on: ${TimeUtils.formatDate(it.createdDate, "MMM dd, yyyy")} | Updated on: ${TimeUtils.formatDate(it.updatedDate, "MMM dd, yyyy")}"
            }
        }

    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolderReports(reportListItemBinding: ReportListItemBinding) : RecyclerView.ViewHolder(reportListItemBinding.root)
}
