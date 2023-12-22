package org.ole.planet.myplanet.ui.team;

import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.FragmentPlanBinding;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

public class PlanFragment extends BaseTeamFragment {
    private FragmentPlanBinding fragmentPlanBinding;
    String missionText, servicesText, rulesText = "";

    public PlanFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentPlanBinding = FragmentPlanBinding.inflate(inflater, container, false);
        return fragmentPlanBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (team != null) {
            Utilities.log(team.type);
            Utilities.log(team.services);
            Utilities.log(team.rules);
            if (TextUtils.equals(team.type, "enterprise")) {
                missionText = ((team.description.trim().isEmpty()) ? "" : ("<b>" + getString(R.string.entMission) + "</b><br/>" + team.description + "<br/><br/>"));
                servicesText = ((team.services.trim().isEmpty()) ? "" : ("<b>" + getString(R.string.entServices) + "</b><br/>" + team.services + "<br/><br/>"));
                rulesText = ((team.rules.trim().isEmpty()) ? "" : ("<b>" + getString(R.string.entRules) + "</b><br/>" + team.rules));
                fragmentPlanBinding.tvDescription.setText(Html.fromHtml(missionText + servicesText + rulesText));
                if (fragmentPlanBinding.tvDescription.getText().toString().isEmpty())
                    fragmentPlanBinding.tvDescription.setText(Html.fromHtml("<br/>" + getString(R.string.entEmptyDescription) + "<br/>"));
            } else {
                fragmentPlanBinding.tvDescription.setText(team.description);
            }
            fragmentPlanBinding.tvDate.setText(getString(R.string.created_on) + " " + TimeUtils.formatDate(team.createdDate));
        }
    }
}
