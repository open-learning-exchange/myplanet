package org.ole.planet.myplanet.ui.team;


import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

/**
 * A simple {@link Fragment} subclass.
 */
public class PlanFragment extends BaseTeamFragment {

    TextView description, date;

    public PlanFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_plan, container, false);
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (team != null) {
            description = getView().findViewById(R.id.tv_description);
            date = getView().findViewById(R.id.tv_date);
            Utilities.log(team.getType());
            Utilities.log(team.getServices());
            Utilities.log(team.getRules());
            if (TextUtils.equals(team.getType(),"enterprise")) {
                String missionText =  ((team.getDescription().trim().isEmpty()) ? "" : ("<b>"+getString(R.string.entMission)+"</b><br/>" + team.getDescription()+"<br/><br/>"));
                String servicesText = ((team.getServices().trim().isEmpty()) ? "" : ("<b>"+getString(R.string.entServices)+"</b><br/>" + team.getServices()+"<br/><br/>"));
                String rulesText = ((team.getRules().trim().isEmpty()) ? "" : ("<b>"+getString(R.string.entRules)+"</b><br/>" + team.getRules()));
                description.setText(Html.fromHtml(missionText +servicesText+ rulesText));
            } else {
                description.setText(team.getDescription());
            }
            date.setText(getString(R.string.created_on) + " "+ TimeUtils.formatDate(team.getCreatedDate()));
        }
    }
}
