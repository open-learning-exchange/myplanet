package org.ole.planet.myplanet.ui.mymeetup

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemMeetupBinding
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate

class AdapterMeetup : ListAdapter<RealmMeetup, AdapterMeetup.ViewHolderMeetup>(
    DiffUtils.itemCallback(
        areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
        areContentsTheSame = { oldItem, newItem ->
            oldItem.title == newItem.title &&
                oldItem.description == newItem.description &&
                oldItem.startDate == newItem.startDate &&
                oldItem.endDate == newItem.endDate &&
                oldItem.startTime == newItem.startTime &&
                oldItem.endTime == newItem.endTime &&
                oldItem.meetupLocation == newItem.meetupLocation &&
                oldItem.meetupLink == newItem.meetupLink &&
                oldItem.recurring == newItem.recurring &&
                oldItem.creator == newItem.creator
        }
    )
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderMeetup {
        val itemMeetupBinding = ItemMeetupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderMeetup(itemMeetupBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderMeetup, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolderMeetup(private val itemMeetupBinding: ItemMeetupBinding) :
        RecyclerView.ViewHolder(itemMeetupBinding.root) {
        fun bind(meetup: RealmMeetup) {
            itemMeetupBinding.tvTitle.text = context.getString(R.string.message_placeholder, meetup.title)
            itemMeetupBinding.tvDescription.text = context.getString(R.string.message_placeholder, meetup.description)
            itemMeetupBinding.tvDateFrom.text = formatDate(meetup.startDate)
            itemMeetupBinding.tvDateTo.text = formatDate(meetup.endDate)
            itemMeetupBinding.tvTime.text = "${meetup.startTime} - ${meetup.endTime}"
            itemMeetupBinding.tvLocation.text = context.getString(R.string.message_placeholder, meetup.meetupLocation)
            itemMeetupBinding.tvLink.text = context.getString(R.string.message_placeholder, meetup.meetupLink)
            itemMeetupBinding.tvRecurring.text = context.getString(R.string.message_placeholder, meetup.recurring)
            itemMeetupBinding.tvCreator.text = context.getString(R.string.message_placeholder, meetup.creator)
        }
    }
}
