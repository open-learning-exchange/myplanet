package org.ole.planet.myplanet.ui.team;


import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.ole.planet.myplanet.R;

/**
 * A simple {@link Fragment} subclass.
 */
public class TeamDetailFragment extends Fragment {

    TabLayout tabLayout;
    ViewPager viewPager;

    public TeamDetailFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_team_detail, container, false);
        tabLayout = v.findViewById(R.id.tab_layout);
        viewPager = v.findViewById(R.id.view_pager);
        viewPager.setAdapter(new TeamPagerAdapter(getChildFragmentManager()));
        tabLayout.setupWithViewPager(viewPager);
        return v;
    }


}
