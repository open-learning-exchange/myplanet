package org.ole.planet.myplanet.ui.mymeetup

import android.graphics.drawable.GradientDrawable
import android.view.*
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemMeetupBinding
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import android.view.View
import androidx.core.content.ContextCompat


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
        setCardTitle(holder, meetup)
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
            itemMeetupBinding.date.text = "deadline: "
            itemMeetupBinding.creator.text = "assigneed to: "
            if(meetup.assignee == null){
                itemMeetupBinding.tvCreator.text = context.getString(R.string.none)
            } else{
                val splitUserId = meetup.assignee!!.split(".")
                if(splitUserId.size < 2) {
                    itemMeetupBinding.tvCreator.text = context.getString(R.string.none)
                } else {
                    val lastItem = splitUserId.last()
                    val assigneeUserName = lastItem.split(":")[1]
                    itemMeetupBinding.tvCreator.text = context.getString(R.string.message_placeholder, assigneeUserName)
                }
            }
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

    private fun setCardTitle(holder: ViewHolderMeetup, meetup: Any){
        var color: Int = ContextCompat.getColor(context, R.color.primary)
        if (meetup is RealmMeetup) {
            itemMeetupBinding.type.text = "Event"
        } else if(meetup is RealmTeamTask){
            if(meetup.completed){
                color = ContextCompat.getColor(context, R.color.disable_color)
                itemMeetupBinding.type.text = "Completed Task"
            } else{
                color = ContextCompat.getColor(context, R.color.uncompleted_task)
                itemMeetupBinding.type.text = "Uncompleted Task"
            }
        }

        val radius = holder.itemView.resources
            .getDimension(R.dimen._10dp)

        val headerBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadii = floatArrayOf(
                radius, radius,
                radius, radius,
                0f,    0f,
                0f,    0f
            )
        }

        itemMeetupBinding.ltType.background = headerBg
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolderMeetup(itemMeetupBinding: ItemMeetupBinding) : RecyclerView.ViewHolder(itemMeetupBinding.root)
}
