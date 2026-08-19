package org.ole.planet.myplanet.ui.events

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemMeetupBinding
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.ui.teams.InlineCommentsAdapter
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.TimeUtils.formatDate

class EventsAdapter(
    var nonTeamMember: Boolean = false,
    private val onMeetupClick: ((Meetup) -> Unit)? = null
) : ListAdapter<Meetup, EventsAdapter.EventsViewHolder>(
    DiffUtils.itemCallback<Meetup>(
        areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
        areContentsTheSame = { oldItem, newItem -> oldItem.id == newItem.id && oldItem.title == newItem.title && oldItem.description == newItem.description && oldItem.startDate == newItem.startDate && oldItem.endDate == newItem.endDate && oldItem.startTime == newItem.startTime && oldItem.endTime == newItem.endTime && oldItem.meetupLocation == newItem.meetupLocation && oldItem.meetupLink == newItem.meetupLink && oldItem.recurring == newItem.recurring && oldItem.creator == newItem.creator },
        getChangePayload = { oldItem, newItem ->
            val changes = mutableSetOf<String>()
            if (oldItem.title != newItem.title) changes.add("TITLE")
            if (oldItem.description != newItem.description) changes.add("DESCRIPTION")
            if (oldItem.startDate != newItem.startDate) changes.add("START_DATE")
            if (oldItem.endDate != newItem.endDate) changes.add("END_DATE")
            if (oldItem.startTime != newItem.startTime || oldItem.endTime != newItem.endTime) changes.add("TIME")
            if (oldItem.meetupLocation != newItem.meetupLocation) changes.add("MEETUP_LOCATION")
            if (oldItem.meetupLink != newItem.meetupLink) changes.add("MEETUP_LINK")
            if (oldItem.recurring != newItem.recurring) changes.add("RECURRING")
            if (oldItem.creator != newItem.creator) changes.add("CREATOR")
            if (changes.isNotEmpty()) changes else null
        }
    )
) {
    private val dateCache = mutableMapOf<Long, String>()
    private val commentsMap: MutableMap<String, List<News>> = mutableMapOf()
    private val expandedMeetupIds: MutableSet<String> = mutableSetOf()
    private var currentUserId: String? = null
    private var isLeader: Boolean = false
    private var onSendCommentListener: ((meetupId: String, message: String) -> Unit)? = null
    private var onDeleteCommentListener: ((News) -> Unit)? = null

    fun setCurrentUser(userId: String?, leader: Boolean) {
        currentUserId = userId
        isLeader = leader
        notifyDataSetChanged()
    }

    fun updateComments(newCommentsMap: Map<String, List<News>>) {
        commentsMap.clear()
        commentsMap.putAll(newCommentsMap)
        notifyDataSetChanged()
    }

    fun setOnCommentActions(
        onSend: (meetupId: String, message: String) -> Unit,
        onDelete: (News) -> Unit
    ) {
        this.onSendCommentListener = onSend
        this.onDeleteCommentListener = onDelete
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventsViewHolder {
        val binding = ItemMeetupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventsViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            val meetup = getItem(position)
            val binding = holder.binding
            val context = binding.root.context

            for (payload in payloads) {
                if (payload is Set<*>) {
                    payload.forEach { field ->
                        when (field) {
                            "TITLE" -> binding.tvTitle.text = context.getString(R.string.message_placeholder, meetup.title)
                            "DESCRIPTION" -> binding.tvDescription.text = context.getString(R.string.message_placeholder, meetup.description)
                            "START_DATE" -> binding.tvDateFrom.text = formatDate(meetup.startDate)
                            "END_DATE" -> binding.tvDateTo.text = formatDate(meetup.endDate)
                            "TIME" -> binding.tvTime.text = "${meetup.startTime} - ${meetup.endTime}"
                            "MEETUP_LOCATION" -> binding.tvLocation.text = context.getString(R.string.message_placeholder, meetup.meetupLocation)
                            "MEETUP_LINK" -> binding.tvLink.text = context.getString(R.string.message_placeholder, meetup.meetupLink)
                            "RECURRING" -> binding.tvRecurring.text = context.getString(R.string.message_placeholder, meetup.recurring)
                            "CREATOR" -> binding.tvCreator.text = context.getString(R.string.message_placeholder, meetup.creator)
                        }
                    }
                }
            }
            bindCommentSection(holder, meetup)
            binding.root.setOnClickListener {
                onMeetupClick?.invoke(meetup)
            }
        }
    }

    override fun onBindViewHolder(holder: EventsViewHolder, position: Int) {
        val meetup = getItem(position)
        val binding = holder.binding
        val context = binding.root.context
        binding.tvTitle.text = context.getString(R.string.message_placeholder, meetup.title)
        binding.tvDescription.text = context.getString(R.string.message_placeholder, meetup.description)
        binding.tvDateFrom.text = dateCache.getOrPut(meetup.startDate) { formatDate(meetup.startDate) }
        binding.tvDateTo.text = dateCache.getOrPut(meetup.endDate) { formatDate(meetup.endDate) }
        binding.tvTime.text = "${meetup.startTime} - ${meetup.endTime}"
        binding.tvLocation.text = context.getString(R.string.message_placeholder, meetup.meetupLocation)
        binding.tvLink.text = context.getString(R.string.message_placeholder, meetup.meetupLink)
        binding.tvRecurring.text = context.getString(R.string.message_placeholder, meetup.recurring)
        binding.tvCreator.text = context.getString(R.string.message_placeholder, meetup.creator)
        bindCommentSection(holder, meetup)
        binding.root.setOnClickListener {
            onMeetupClick?.invoke(meetup)
        }
    }

    private fun bindCommentSection(holder: EventsViewHolder, meetup: Meetup) {
        val binding = holder.binding
        val context = binding.root.context
        val meetupId = meetup.id ?: ""
        val comments = commentsMap[meetupId] ?: emptyList()

        binding.tvCommentCount.text = context.getString(R.string.comments_count, comments.size)

        if (nonTeamMember) {
            binding.llCommentInput.visibility = View.GONE
        } else {
            binding.llCommentInput.visibility = View.VISIBLE
        }

        val isExpanded = expandedMeetupIds.contains(meetupId)
        binding.llCommentsContainer.isVisible = isExpanded
        binding.ivExpandComments.setImageResource(
            if (isExpanded) R.drawable.ic_keyboard_arrow_up_black_24dp
            else R.drawable.ic_keyboard_arrow_down_black_24dp
        )

        val commentsAdapter = InlineCommentsAdapter(
            currentUserId = currentUserId,
            isLeader = isLeader,
            onDeleteComment = { comment -> onDeleteCommentListener?.invoke(comment) }
        )
        binding.rvComments.layoutManager = LinearLayoutManager(context)
        binding.rvComments.adapter = commentsAdapter
        commentsAdapter.submitList(comments)

        binding.llCommentToggle.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            val currentId: String = if (adapterPosition != RecyclerView.NO_POSITION && adapterPosition < itemCount) {
                this@EventsAdapter.getItem(adapterPosition).id
            } else {
                meetup.id
            }

            if (expandedMeetupIds.contains(currentId)) {
                expandedMeetupIds.remove(currentId)
            } else {
                expandedMeetupIds.add(currentId)
            }
            if (adapterPosition != RecyclerView.NO_POSITION && adapterPosition < itemCount) {
                notifyItemChanged(adapterPosition)
            } else {
                val isExp = expandedMeetupIds.contains(currentId)
                binding.llCommentsContainer.isVisible = isExp
                binding.ivExpandComments.setImageResource(
                    if (isExp) R.drawable.ic_keyboard_arrow_up_black_24dp
                    else R.drawable.ic_keyboard_arrow_down_black_24dp
                )
            }
        }

        binding.btnSendComment.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            val currentId: String = if (adapterPosition != RecyclerView.NO_POSITION && adapterPosition < itemCount) {
                this@EventsAdapter.getItem(adapterPosition).id
            } else {
                meetup.id
            }

            val message = binding.etComment.text?.toString()?.trim().orEmpty()
            if (message.isNotEmpty()) {
                onSendCommentListener?.invoke(currentId, message)
                binding.etComment.setText("")
            }
        }
    }

    class EventsViewHolder(val binding: ItemMeetupBinding) : RecyclerView.ViewHolder(binding.root)
}
