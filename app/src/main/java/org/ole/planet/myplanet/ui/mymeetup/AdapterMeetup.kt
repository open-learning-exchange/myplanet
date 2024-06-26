package org.ole.planet.myplanet.ui.mymeetup

import android.view.*
import androidx.recyclerview.widget.RecyclerView
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
        itemMeetupBinding.tvTitle.text = "${meetup.title}"
        itemMeetupBinding.tvDescription.text = "${meetup.description}"
        itemMeetupBinding.tvDateFrom.text = formatDate(meetup.startDate)
        itemMeetupBinding.tvDateTo.text = formatDate(meetup.endDate)
        itemMeetupBinding.tvLocation.text = "${meetup.meetupLocation}"
        itemMeetupBinding.tvRecurring.text = "${meetup.recurring}"
        itemMeetupBinding.tvCreator.text = "${meetup.creator}"
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolderMeetup(itemMeetupBinding: ItemMeetupBinding) : RecyclerView.ViewHolder(itemMeetupBinding.root)
}
