package org.ole.planet.myplanet.ui.mymeetup

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemMeetupBinding
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate

class AdapterMeetup(private val list: List<RealmMeetup>) : RecyclerView.Adapter<AdapterMeetup.ViewHolderMeetup>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderMeetup {
        val binding = ItemMeetupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderMeetup(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderMeetup, position: Int) {
        val meetup = list[position]
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

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolderMeetup(val binding: ItemMeetupBinding) : RecyclerView.ViewHolder(binding.root)
}
