package org.ole.planet.myplanet.ui.mymeetup

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemMeetupBinding
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate

class AdapterMeetup(private val list: List<RealmMeetup>) : RecyclerView.Adapter<AdapterMeetup.ViewHolderMeetup>() {
    private lateinit var itemMeetupBinding: ItemMeetupBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderMeetup {
        itemMeetupBinding = ItemMeetupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderMeetup(itemMeetupBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderMeetup, position: Int) {
        val meetup = list[position]
        val ctx = itemMeetupBinding.root.context
        itemMeetupBinding.tvTitle.text = ctx.getString(R.string.message_placeholder, meetup.title)
        itemMeetupBinding.tvDescription.text = ctx.getString(R.string.message_placeholder, meetup.description)
        itemMeetupBinding.tvDateFrom.text = formatDate(meetup.startDate)
        itemMeetupBinding.tvDateTo.text = formatDate(meetup.endDate)
        itemMeetupBinding.tvTime.text = "${meetup.startTime} - ${meetup.endTime}"
        itemMeetupBinding.tvLocation.text = ctx.getString(R.string.message_placeholder, meetup.meetupLocation)
        itemMeetupBinding.tvLink.text = ctx.getString(R.string.message_placeholder, meetup.meetupLink)
        itemMeetupBinding.tvRecurring.text = ctx.getString(R.string.message_placeholder, meetup.recurring)
        itemMeetupBinding.tvCreator.text = ctx.getString(R.string.message_placeholder, meetup.creator)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolderMeetup(itemMeetupBinding: ItemMeetupBinding) : RecyclerView.ViewHolder(itemMeetupBinding.root)
}
