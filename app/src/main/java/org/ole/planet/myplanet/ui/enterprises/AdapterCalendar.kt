package org.ole.planet.myplanet.ui.enterprises

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowTeamCalendarBinding
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.ui.enterprises.AdapterCalendar.ViewHolderCalendar
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate

class AdapterCalendar(private val context: Context, private val list: List<RealmMeetup>) : RecyclerView.Adapter<ViewHolderCalendar>() {
    private lateinit var rowTeamCalendarBinding: RowTeamCalendarBinding
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderCalendar {
        rowTeamCalendarBinding = RowTeamCalendarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderCalendar(rowTeamCalendarBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderCalendar, position: Int) {
        val meetup = list[position]
        rowTeamCalendarBinding.tvTitle.text = meetup.title
        rowTeamCalendarBinding.tvDescription.text = meetup.description
        if (meetup.startDate == meetup.endDate) {
            rowTeamCalendarBinding.tvDate.text = formatDate(meetup.startDate,"")
        } else {
            rowTeamCalendarBinding.tvDate.text = context.getString(R.string.date_range, formatDate(meetup.startDate, ""), formatDate(meetup.endDate, ""))
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolderCalendar(rowTeamCalendarBinding: RowTeamCalendarBinding) : RecyclerView.ViewHolder(rowTeamCalendarBinding.root)
}
