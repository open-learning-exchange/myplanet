package org.ole.planet.myplanet.ui.events

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemMeetupBinding
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.TimeUtils.formatDate

class EventsAdapter : ListAdapter<RealmMeetup, EventsAdapter.EventsViewHolder>(
    DiffUtils.itemCallback<RealmMeetup>(
        areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
        areContentsTheSame = { oldItem, newItem -> oldItem.id == newItem.id && oldItem.title == newItem.title && oldItem.description == newItem.description && oldItem.startDate == newItem.startDate && oldItem.endDate == newItem.endDate && oldItem.startTime == newItem.startTime && oldItem.endTime == newItem.endTime && oldItem.meetupLocation == newItem.meetupLocation && oldItem.meetupLink == newItem.meetupLink && oldItem.recurring == newItem.recurring && oldItem.creator == newItem.creator }
    )
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventsViewHolder {
        val binding = ItemMeetupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventsViewHolder, position: Int) {
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

    class EventsViewHolder(val binding: ItemMeetupBinding) : RecyclerView.ViewHolder(binding.root)

}
