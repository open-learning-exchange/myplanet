package org.ole.planet.myplanet.ui.feedback

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
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

    companion object {
        private var bgPrimary: Drawable? = null
        private var bgGrey: Drawable? = null
    }

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

        if (bgPrimary == null) {
            bgPrimary = ContextCompat.getDrawable(context, R.drawable.bg_primary)
        }
        if (bgGrey == null) {
            bgGrey = ContextCompat.getDrawable(context, R.drawable.bg_primary)?.mutate()
            bgGrey?.let {
                DrawableCompat.setTint(it, ContextCompat.getColor(context, R.color.md_amber_500))
            }
        }

        if ("yes".equals(feedback.priority, ignoreCase = true)) {
            binding.tvPriority.background = bgPrimary
        } else {
            binding.tvPriority.background = bgGrey
        }
        binding.tvStatus.background = if ("open".equals(feedback.status, ignoreCase = true)) bgPrimary else bgGrey

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
