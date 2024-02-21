package org.ole.planet.myplanet.ui.calendar

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.applandeo.materialcalendarview.CalendarDay
import com.applandeo.materialcalendarview.listeners.OnCalendarDayClickListener
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.FragmentCalendarBinding
import java.util.Calendar

class CalendarFragment : Fragment() {
    private lateinit var calendarBinding: FragmentCalendarBinding
    var listener: OnHomeItemClickListener? = null
    fun addEvents() {
        val events: MutableList<CalendarDay> = ArrayList()
        val calendar = Calendar.getInstance()
        events.add(CalendarDay(calendar))
        calendarBinding.calendarView.setCalendarDays(events)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnHomeItemClickListener) listener = context
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        calendarBinding = FragmentCalendarBinding.inflate(inflater, container, false)
        calendarBinding.calendarView.setOnCalendarDayClickListener(object : OnCalendarDayClickListener {
            override fun onClick(calendarDay: CalendarDay) {
            }
        })
        val calendar = Calendar.getInstance()
        calendarBinding.calendarView.setDate(calendar.time)
        return calendarBinding.root
    }
}
