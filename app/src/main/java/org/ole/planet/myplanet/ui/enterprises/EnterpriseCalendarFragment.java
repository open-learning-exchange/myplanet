package org.ole.planet.myplanet.ui.enterprises;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.marcohc.robotocalendar.RobotoCalendarView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmMeetup;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.ui.team.BaseTeamFragment;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Calendar;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 */
public class EnterpriseCalendarFragment extends BaseTeamFragment implements RobotoCalendarView.RobotoCalendarListener {

    RobotoCalendarView calendarView;
    List<RealmMeetup> list;
    public EnterpriseCalendarFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_enterprise_calendar, container, false);
        calendarView = v.findViewById(R.id.calendar);
        v.findViewById(R.id.add_event).setOnClickListener(view -> {
            showMeetupAlert();
        });
        return v;
    }

    private void showMeetupAlert() {
    View v = LayoutInflater.from(getActivity()).inflate(R.layout.add_meetup, null);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        calendarView.setRobotoCalendarListener(this);
        list = mRealm.where(RealmMeetup.class).equalTo("teamId", teamId).findAll();
        Utilities.log(list.size()+"");
        Calendar daySelected = Calendar.getInstance();
        daySelected.add(Calendar.DAY_OF_MONTH, 5);
        calendarView.markCircleImage2(Calendar.getInstance());
    }

    @Override
    public void onDaySelected(Calendar calendar) {

    }

    @Override
    public void onRightButtonClick() {

    }

    @Override
    public void onLeftButtonClick() {

    }
}
