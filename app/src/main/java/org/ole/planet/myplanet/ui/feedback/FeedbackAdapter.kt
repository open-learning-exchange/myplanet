package org.ole.planet.myplanet.ui.feedback

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowFeedbackBinding
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.ui.feedback.FeedbackAdapter.FeedbackViewHolder
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.TimeUtils.getFormattedDate

class FeedbackAdapter :
    ListAdapter<RealmFeedback, FeedbackViewHolder>(
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedbackViewHolder {
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
        val contentDescription = "${feedback.title}, ${feedback.type}, " +
                "${context.getString(R.string.status)}: ${feedback.status}, ${context.getString(R.string.priority)}: ${feedback.priority}, " +
                "${context.getString(R.string.open_date)}: ${getFormattedDate(feedback.openTime)}"
        binding.feedbackCardView.contentDescription = contentDescription

        if ("yes".equals(feedback.priority, ignoreCase = true)) {
            binding.tvPriority.background =
                ResourcesCompat.getDrawable(context.resources, R.drawable.bg_primary, null)
        } else {
            binding.tvPriority.background =
                ResourcesCompat.getDrawable(context.resources, R.drawable.bg_grey, null)
        }
        binding.tvStatus.background = ResourcesCompat.getDrawable(
            context.resources,
            if ("open".equals(feedback.status, ignoreCase = true)) {
                R.drawable.bg_primary
            } else {
                R.drawable.bg_grey
            },
            null
        )
        binding.tvOpenDate.text = getFormattedDate(feedback.openTime)
        binding.root.setOnClickListener {
            binding.root.contentDescription = feedback.title
            context.startActivity(
                Intent(context, FeedbackDetailActivity::class.java)
                    .putExtra("id", feedback.id)
            )
        }
    }

    class FeedbackViewHolder(val rowFeedbackBinding: RowFeedbackBinding) :
        RecyclerView.ViewHolder(rowFeedbackBinding.root)
}
