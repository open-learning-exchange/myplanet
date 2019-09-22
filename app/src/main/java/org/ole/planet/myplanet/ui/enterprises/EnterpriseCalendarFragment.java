package org.ole.planet.myplanet.ui.enterprises;


import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.events.calendar.views.EventsCalendar;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmMeetup;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.ui.team.BaseTeamFragment;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

/**
 * A simple {@link Fragment} subclass.
 */
public class EnterpriseCalendarFragment extends BaseTeamFragment {

    EventsCalendar calendarView;
    List<RealmMeetup> list;
    TextView startDate, startTime, endDate, endTime;
    Calendar start, end;

    public EnterpriseCalendarFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_enterprise_calendar, container, false);
        start = Calendar.getInstance();
        end = Calendar.getInstance();
        calendarView = v.findViewById(R.id.calendar);
        v.findViewById(R.id.add_event).setOnClickListener(view -> showMeetupAlert());
        return v;
    }

    private void showMeetupAlert() {
        View v = LayoutInflater.from(getActivity()).inflate(R.layout.add_meetup, null);
        TextView title = v.findViewById(R.id.et_title);
        TextView location = v.findViewById(R.id.et_location);
        TextView description = v.findViewById(R.id.et_description);
        RadioGroup radioGroup = v.findViewById(R.id.rg_recuring);
        startDate = v.findViewById(R.id.tv_start_date);
        startTime = v.findViewById(R.id.tv_start_time);
        endDate = v.findViewById(R.id.tv_end_date);
        endTime = v.findViewById(R.id.tv_end_time);
        setDatePickerListener(startDate, start);
        setDatePickerListener(endDate, end);
        setTimePicker(startTime);
        setTimePicker(endTime);

        new AlertDialog.Builder(getActivity()).setView(v)
                .setPositiveButton("Save", (dialogInterface, i) -> {
                    String ttl = title.getText().toString();
                    String desc = description.getText().toString();
                    String loc = location.getText().toString();
                    if (ttl.isEmpty()) {
                        Utilities.toast(getActivity(), "Title is required");
                    } else if (desc.isEmpty()) {
                        Utilities.toast(getActivity(), "Description is required");
                    } else if (start == null) {
                        Utilities.toast(getActivity(), "Start time is required");
                    } else {
                        if (!mRealm.isInTransaction())
                            mRealm.beginTransaction();
                        RealmMeetup meetup = mRealm.createObject(RealmMeetup.class, UUID.randomUUID().toString());
                        meetup.setTitle(ttl);
                        meetup.setDescription(desc);
                        meetup.setMeetupLocation(loc);
                        meetup.setCreator(user.getId());
                        meetup.setStartDate(start.getTimeInMillis());
                        if (end != null)
                            meetup.setEndDate(end.getTimeInMillis());
                        meetup.setEndTime(endTime.getText().toString());
                        meetup.setStartTime(startTime.getText().toString());
                        RadioButton rb = v.findViewById(radioGroup.getCheckedRadioButtonId());
                        if (rb != null) {
                            meetup.setRecurring(rb.getText().toString());
                        }
                        JsonObject ob = new JsonObject();
                        ob.addProperty("teams", teamId);
                        meetup.setLinks(new Gson().toJson(ob));
                        meetup.setTeamId(teamId);
                        mRealm.commitTransaction();
                        Utilities.toast(getActivity(), "Meetup added");
                    }
                }).setNegativeButton("Cancel", null).show();
    }


    private void setDatePickerListener(TextView view, Calendar date) {
        Calendar c = Calendar.getInstance();
        view.setOnClickListener(v -> {

            new DatePickerDialog(getActivity(), (vi, year, monthOfYear, dayOfMonth) -> {
                date.set(Calendar.YEAR, year);
                date.set(Calendar.MONTH, monthOfYear);
                date.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                if (view != null)
                    view.setText(TimeUtils.formatDate(date.getTimeInMillis(), "yyyy-MM-dd"));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();

        });
    }


    private void setTimePicker(TextView time) {
        Calendar c = Calendar.getInstance();
        time.setOnClickListener(v -> {
            TimePickerDialog timePickerDialog = new TimePickerDialog(getActivity(),
                    (view, hourOfDay, minute) -> {
                        time.setText(String.format("%02d:%02d", hourOfDay, minute));
                    }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true);
            timePickerDialog.show();
        });

    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        list = mRealm.where(RealmMeetup.class).equalTo("teamId", teamId).findAll();
        Utilities.log(list.size() + "");
        Calendar daySelected = Calendar.getInstance();
        daySelected.add(Calendar.DAY_OF_MONTH, 5);
        List<Calendar> li = new ArrayList<>();
        for (RealmMeetup meetup : list
        ) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(meetup.getStartDate());
        }
    }


}
