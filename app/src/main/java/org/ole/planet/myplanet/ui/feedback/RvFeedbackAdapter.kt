package org.ole.planet.myplanet.ui.feedback

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.RowFeedbackReplyBinding
import org.ole.planet.myplanet.model.FeedbackReply
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.TimeUtils

class RvFeedbackAdapter : ListAdapter<FeedbackReply, RvFeedbackAdapter.ReplyViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReplyViewHolder {
        val rowFeedbackReplyBinding = RowFeedbackReplyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReplyViewHolder(rowFeedbackReplyBinding)
    }

    override fun onBindViewHolder(holder: ReplyViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ReplyViewHolder(private val rowFeedbackReplyBinding: RowFeedbackReplyBinding) : RecyclerView.ViewHolder(rowFeedbackReplyBinding.root) {
        fun bind(reply: FeedbackReply?) {
            reply?.let {
                rowFeedbackReplyBinding.tvDate.text = it.date?.let { date ->
                    TimeUtils.getFormattedDateWithTime(date.toLong())
                }
                rowFeedbackReplyBinding.tvUser.text = it.user
                rowFeedbackReplyBinding.tvMessage.text = it.message
            }
        }
    }

    companion object {
        val DIFF_CALLBACK = DiffUtils.itemCallback<FeedbackReply>(
            areItemsTheSame = { oldItem, newItem -> oldItem.date == newItem.date && oldItem.user == newItem.user },
            areContentsTheSame = { oldItem, newItem -> oldItem.date == newItem.date && oldItem.user == newItem.user && oldItem.message == newItem.message }
        )
    }
}