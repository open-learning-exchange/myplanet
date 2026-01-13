package org.ole.planet.myplanet.ui.enterprises

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ReportListItemBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils

class EnterprisesReportsAdapter(
    private val context: Context,
    private val prefData: SharedPrefManager,
    private val onEdit: (RealmMyTeam) -> Unit,
    private val onDelete: (RealmMyTeam) -> Unit
) : ListAdapter<RealmMyTeam, EnterprisesReportsAdapter.ReportsViewHolder>(diffCallback) {
    private var nonTeamMember = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportsViewHolder {
        val binding = ReportListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReportsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReportsViewHolder, position: Int) {
        val binding = holder.binding
        if (nonTeamMember) {
            binding.edit.visibility = View.GONE
            binding.delete.visibility = View.GONE
        } else {
            binding.edit.visibility = View.VISIBLE
            binding.delete.visibility = View.VISIBLE
        }
        val report = getItem(position)
        binding.tvReportTitle.text = context.getString(R.string.team_financial_report, prefData.getTeamName())
        report?.let {
            with(binding) {
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

        binding.edit.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                getItem(adapterPosition)?.let { onEdit(it) }
            }
        }

        binding.delete.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                getItem(adapterPosition)?.let { onDelete(it) }
            }
        }
    }

    fun setNonTeamMember(nonTeamMember: Boolean) {
        this.nonTeamMember = nonTeamMember
    }

    class ReportsViewHolder(val binding: ReportListItemBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        val diffCallback = DiffUtils.itemCallback<RealmMyTeam>(
            areItemsTheSame = { oldItem, newItem -> oldItem._id == newItem._id },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )
    }
}
