package org.ole.planet.myplanet.ui.feedback

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.RowFeedbackReplyBinding
import org.ole.planet.myplanet.model.FeedbackReply
import org.ole.planet.myplanet.ui.feedback.RvFeedbackAdapter.ReplyViewHolder
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.TimeUtils.getFormattedDateWithTime

class RvFeedbackAdapter :
    ListAdapter<FeedbackReply, ReplyViewHolder>(
        DiffUtils.itemCallback(
            { oldItem, newItem ->
                oldItem.date == newItem.date
            },
            { oldItem, newItem ->
                oldItem.message == newItem.message && oldItem.user == newItem.user
            }
        )
    ) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReplyViewHolder {
        val rowFeedbackReplyBinding = RowFeedbackReplyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReplyViewHolder(rowFeedbackReplyBinding)
    }

    override fun onBindViewHolder(holder: ReplyViewHolder, position: Int) {
        val reply = getItem(position)
        holder.rowFeedbackReplyBinding.tvDate.text = reply.date.let {
            getFormattedDateWithTime(it.toLong())
        }
        holder.rowFeedbackReplyBinding.tvUser.text = reply.user
        holder.rowFeedbackReplyBinding.tvMessage.text = reply.message
    }

    class ReplyViewHolder(val rowFeedbackReplyBinding: RowFeedbackReplyBinding) : RecyclerView.ViewHolder(rowFeedbackReplyBinding.root)
}
