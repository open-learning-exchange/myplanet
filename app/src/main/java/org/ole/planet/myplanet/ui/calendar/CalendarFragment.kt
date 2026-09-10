package org.ole.planet.myplanet.ui.calendar

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import java.util.Calendar
import org.ole.planet.myplanet.base.BaseBindingFragment
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.FragmentCalendarBinding

class CalendarFragment : BaseBindingFragment<FragmentCalendarBinding>(FragmentCalendarBinding::inflate) {
    var listener: OnHomeItemClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnHomeItemClickListener) listener = context
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        val calendar = Calendar.getInstance()
        binding.calendarView.setDate(calendar.time)
        return view
    }
}
