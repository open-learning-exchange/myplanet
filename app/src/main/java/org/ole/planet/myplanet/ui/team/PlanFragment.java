package org.ole.planet.myplanet.ui.team;


import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.text.Html;
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
            if (team.getType().equalsIgnoreCase("enterprise")) {
                description.setText(Html.fromHtml("<b>What is your enterprise's Mission?</b><br/>" + team.getDescription() +
                        "<br/><b>What are the Services your enterprise provides?</b><br/>" + team.getServices() + "<br/>" +
                        "<br/><b>What are the Rules of your enterprise?</b><br/>" + team.getRules() + "<br/>"
                ));
            } else {
                description.setText(team.getDescription());
            }
            date.setText(TimeUtils.formatDate(team.getCreatedDate()));
        }
    }
}
