package org.ole.planet.myplanet.ui.feedback

import android.content.Intent
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowFeedbackBinding
import org.ole.planet.myplanet.model.Feedback
import org.ole.planet.myplanet.ui.feedback.FeedbackAdapter.FeedbackViewHolder
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.TimeUtils.getFormattedDate

class FeedbackAdapter :
    ListAdapter<Feedback, FeedbackViewHolder>(
        DiffUtils.itemCallback(
            { oldItem, newItem ->
                oldItem.id == newItem.id
            },
            { oldItem, newItem ->
                oldItem.title == newItem.title &&
                    oldItem.type == newItem.type &&
                    oldItem.priority == newItem.priority &&
                    oldItem.status == newItem.status &&
                    oldItem.openTime == newItem.openTime
            }
        )
    ) {

    private var primaryColorStateList: ColorStateList? = null
    private var greyColorStateList: ColorStateList? = null
    private var statusText: String? = null
    private var priorityText: String? = null
    private var openDateText: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedbackViewHolder {
        if (primaryColorStateList == null || greyColorStateList == null) {
            val context = parent.context
            primaryColorStateList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.mainColor))
            greyColorStateList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.md_amber_500))
            statusText = context.getString(R.string.status)
            priorityText = context.getString(R.string.priority)
            openDateText = context.getString(R.string.open_date)
        }
        val binding = RowFeedbackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FeedbackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeedbackViewHolder, position: Int) {
        val feedback = getItem(position)
        val binding = holder.rowFeedbackBinding
        val context = binding.root.context

        binding.tvTitle.text = feedback.title
        binding.tvType.text = feedback.type
        binding.tvPriority.text = feedback.priority
        binding.tvStatus.text = feedback.status
        val formattedDate = getFormattedDate(feedback.openTime)
        val contentDescription = "${feedback.title}, ${feedback.type}, " +
                "${statusText}: ${feedback.status}, ${priorityText}: ${feedback.priority}, " +
                "${openDateText}: ${formattedDate}"
        binding.feedbackCardView.contentDescription = contentDescription

        ViewCompat.setBackgroundTintList(
            binding.tvPriority,
            if ("yes".equals(feedback.priority, ignoreCase = true)) primaryColorStateList else greyColorStateList
        )
        ViewCompat.setBackgroundTintList(
            binding.tvStatus,
            if ("open".equals(feedback.status, ignoreCase = true)) primaryColorStateList else greyColorStateList
        )
        binding.tvOpenDate.text = formattedDate
    }

    inner class FeedbackViewHolder(val rowFeedbackBinding: RowFeedbackBinding) :
        RecyclerView.ViewHolder(rowFeedbackBinding.root) {
        init {
            val context = rowFeedbackBinding.root.context
            rowFeedbackBinding.tvPriority.background = ContextCompat.getDrawable(context, R.drawable.bg_primary)
            rowFeedbackBinding.tvStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_primary)

            rowFeedbackBinding.root.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    val feedback = getItem(bindingAdapterPosition)
                    rowFeedbackBinding.root.contentDescription = feedback.title
                    context.startActivity(
                        Intent(context, FeedbackDetailActivity::class.java)
                            .putExtra("id", feedback.id)
                    )
                }
            }
        }
    }
}
