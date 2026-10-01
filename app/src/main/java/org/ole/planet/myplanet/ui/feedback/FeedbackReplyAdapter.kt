package org.ole.planet.myplanet.ui.feedback

import android.content.Context
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.time.ZoneId
import java.util.Locale
import org.ole.planet.myplanet.databinding.RowFeedbackReplyBinding
import org.ole.planet.myplanet.model.FeedbackReply
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.TimeUtils.getFormattedDateWithTime

class FeedbackReplyAdapter(var context: Context) : ListAdapter<FeedbackReply, FeedbackReplyAdapter.ReplyViewHolder>(DIFF_CALLBACK) {
    private val dateCache = LruCache<String, String>(DATE_CACHE_SIZE)
    private var cacheZone: ZoneId? = null
    private var cacheLocale: Locale? = null

    private fun formattedDate(raw: String): String {
        val zone = ZoneId.systemDefault()
        val locale = Locale.getDefault()
        if (zone != cacheZone || locale != cacheLocale) {
            dateCache.evictAll()
            cacheZone = zone
            cacheLocale = locale
        }
        return dateCache.get(raw) ?: getFormattedDateWithTime(raw.toLong()).also { dateCache.put(raw, it) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReplyViewHolder {
        val rowFeedbackReplyBinding = RowFeedbackReplyBinding.inflate(LayoutInflater.from(context), parent, false)
        return ReplyViewHolder(rowFeedbackReplyBinding)
    }

    override fun onBindViewHolder(holder: ReplyViewHolder, position: Int) {
        val feedbackReply = getItem(position)
        holder.binding.tvDate.text = formattedDate(feedbackReply.date)
        holder.binding.tvUser.text = feedbackReply.user
        holder.binding.tvMessage.text = feedbackReply.message
    }

    inner class ReplyViewHolder(val binding: RowFeedbackReplyBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val DATE_CACHE_SIZE = 128

        val DIFF_CALLBACK = DiffUtils.itemCallback<FeedbackReply>(
            areItemsTheSame = { oldItem, newItem -> oldItem.date == newItem.date && oldItem.user == newItem.user },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )
    }
}
