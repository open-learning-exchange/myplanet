package org.ole.planet.myplanet.ui.mymeetup

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemMeetupBinding
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import android.view.View


class AdapterMeetup(private val list: List<Any>) : RecyclerView.Adapter<AdapterMeetup.ViewHolderMeetup>() {
    private lateinit var itemMeetupBinding: ItemMeetupBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderMeetup {
        itemMeetupBinding = ItemMeetupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderMeetup(itemMeetupBinding)
    }

    private fun setFieldOrHide(view: View, value: String?) {
        if (!value.isNullOrEmpty()) {
            when (view) {
                is androidx.appcompat.widget.AppCompatTextView -> view.text = value
            }
            view.visibility = View.VISIBLE
            (view.parent as? View)?.visibility = View.VISIBLE
        } else {
            (view.parent as? View)?.visibility = View.GONE
        }
    }

    override fun onBindViewHolder(holder: ViewHolderMeetup, position: Int) {
        val meetup = list[position]
        if (meetup is RealmMeetup) {
            itemMeetupBinding.tvTitle.text = context.getString(R.string.message_placeholder, meetup.title)
            setFieldOrHide(itemMeetupBinding.tvDescription, context.getString(R.string.message_placeholder, meetup.description))
            itemMeetupBinding.tvDateFrom.text = formatDate(meetup.startDate)
            itemMeetupBinding.tvDateTo.text = formatDate(meetup.endDate)
            itemMeetupBinding.tvTime.text = "${meetup.startTime} - ${meetup.endTime}"
            setFieldOrHide(itemMeetupBinding.tvLocation,context.getString(R.string.message_placeholder, meetup.meetupLocation))
            setFieldOrHide(itemMeetupBinding.tvLink,context.getString(R.string.message_placeholder, meetup.meetupLink))
            itemMeetupBinding.tvRecurring.text = context.getString(R.string.message_placeholder, meetup.recurring)
            itemMeetupBinding.tvCreator.text = context.getString(R.string.message_placeholder, meetup.creator)
        } else if(meetup is RealmTeamTask){
            itemMeetupBinding.date.text = "Deadline:"
            itemMeetupBinding.ltDateTo.visibility = View.GONE
            itemMeetupBinding.ltTime.visibility = View.GONE
            itemMeetupBinding.ltLink.visibility = View.GONE
            itemMeetupBinding.ltRecurring.visibility = View.GONE
            itemMeetupBinding.ltLocation.visibility = View.GONE
            itemMeetupBinding.tvTitle.text = context.getString(R.string.message_placeholder, meetup.title)
            setFieldOrHide(itemMeetupBinding.tvDescription, context.getString(R.string.message_placeholder, meetup.description))
            itemMeetupBinding.tvDateFrom.text = formatDate(meetup.deadline)
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolderMeetup(itemMeetupBinding: ItemMeetupBinding) : RecyclerView.ViewHolder(itemMeetupBinding.root)
}
