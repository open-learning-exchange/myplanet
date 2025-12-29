package org.ole.planet.myplanet.ui.events

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemMeetupBinding
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate

class EventsAdapter : ListAdapter<RealmMeetup, EventsAdapter.ViewHolderEvent>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderEvent {
        val binding = ItemMeetupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderEvent(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderEvent, position: Int) {
        val meetup = getItem(position)
        val binding = holder.binding
        val context = binding.root.context
        binding.tvTitle.text = context.getString(R.string.message_placeholder, meetup.title)
        binding.tvDescription.text = context.getString(R.string.message_placeholder, meetup.description)
        binding.tvDateFrom.text = formatDate(meetup.startDate)
        binding.tvDateTo.text = formatDate(meetup.endDate)
        binding.tvTime.text = "${meetup.startTime} - ${meetup.endTime}"
        binding.tvLocation.text = context.getString(R.string.message_placeholder, meetup.meetupLocation)
        binding.tvLink.text = context.getString(R.string.message_placeholder, meetup.meetupLink)
        binding.tvRecurring.text = context.getString(R.string.message_placeholder, meetup.recurring)
        binding.tvCreator.text = context.getString(R.string.message_placeholder, meetup.creator)
    }

    class ViewHolderEvent(val binding: ItemMeetupBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        val DIFF_CALLBACK = DiffUtils.itemCallback<RealmMeetup>(
            areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )
    }
}
