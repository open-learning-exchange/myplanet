package org.ole.planet.myplanet.ui.enterprises

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ReportListItemBinding
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.ImageViewerUtils
import org.ole.planet.myplanet.utils.TimeUtils

class EnterprisesReportsAdapter(
    private val context: Context,
    private val teamName: String?,
    private val onEdit: (MyTeam) -> Unit,
    private val onDelete: (MyTeam) -> Unit
) : ListAdapter<MyTeam, EnterprisesReportsAdapter.ReportsViewHolder>(diffCallback) {
    private var nonTeamMember = false
    private val attachmentExistsCache = HashMap<String, Boolean>()

    override fun onCurrentListChanged(
        previousList: MutableList<MyTeam>,
        currentList: MutableList<MyTeam>
    ) {
        super.onCurrentListChanged(previousList, currentList)
        attachmentExistsCache.clear()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportsViewHolder {
        val binding = ReportListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReportsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReportsViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            var unhandled = false
            payloads.forEach { payload ->
                if (payload == PAYLOAD_KEY_NON_TEAM_MEMBER_CHANGED) {
                    setNonTeamMemberVisibility(holder.binding)
                } else {
                    unhandled = true
                }
            }
            if (unhandled) {
                super.onBindViewHolder(holder, position, payloads)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: ReportsViewHolder, position: Int) {
        val binding = holder.binding
        setNonTeamMemberVisibility(binding)
        val report = getItem(position)
        binding.tvReportTitle.text = context.getString(R.string.team_financial_report, teamName)
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
            bindReportImage(binding, it)
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

    override fun onViewRecycled(holder: ReportsViewHolder) {
        super.onViewRecycled(holder)
        Glide.with(context).clear(holder.binding.reportImage)
        holder.binding.reportImage.setOnClickListener(null)
        holder.binding.edit.setOnClickListener(null)
        holder.binding.delete.setOnClickListener(null)
    }

    private fun bindReportImage(binding: ReportListItemBinding, report: MyTeam) {
        val imageFile = MyTeam.getAttachmentFile(context, report._id, report.imageName)
        if (imageFile != null && attachmentExistsCache.getOrPut(imageFile.absolutePath) { imageFile.exists() }) {
            binding.reportImage.visibility = View.VISIBLE
            Glide.with(context)
                .load(imageFile)
                .placeholder(R.drawable.ic_loading)
                .error(R.drawable.ic_loading)
                .into(binding.reportImage)
            binding.reportImage.setOnClickListener {
                ImageViewerUtils.showZoomableImage(context, imageFile.absolutePath)
            }
        } else {
            binding.reportImage.visibility = View.GONE
        }
    }

    fun setNonTeamMember(nonTeamMember: Boolean) {
        if (this.nonTeamMember == nonTeamMember) return
        this.nonTeamMember = nonTeamMember
        notifyItemRangeChanged(0, itemCount, PAYLOAD_KEY_NON_TEAM_MEMBER_CHANGED)
    }

    private fun setNonTeamMemberVisibility(binding: ReportListItemBinding) {
        if (nonTeamMember) {
            binding.edit.visibility = View.GONE
            binding.delete.visibility = View.GONE
        } else {
            binding.edit.visibility = View.VISIBLE
            binding.delete.visibility = View.VISIBLE
        }
    }

    class ReportsViewHolder(val binding: ReportListItemBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        const val PAYLOAD_KEY_NON_TEAM_MEMBER_CHANGED = "PAYLOAD_KEY_NON_TEAM_MEMBER_CHANGED"
        val diffCallback = DiffUtils.itemCallback<MyTeam>(
            areItemsTheSame = { oldItem, newItem -> oldItem._id == newItem._id },
            areContentsTheSame = { oldItem, newItem ->
                oldItem.startDate == newItem.startDate &&
                    oldItem.endDate == newItem.endDate &&
                    oldItem.beginningBalance == newItem.beginningBalance &&
                    oldItem.sales == newItem.sales &&
                    oldItem.otherIncome == newItem.otherIncome &&
                    oldItem.wages == newItem.wages &&
                    oldItem.otherExpenses == newItem.otherExpenses &&
                    oldItem.description == newItem.description &&
                    oldItem.createdDate == newItem.createdDate &&
                    oldItem.updatedDate == newItem.updatedDate &&
                    oldItem.imageName == newItem.imageName
            }
        )
    }
}
