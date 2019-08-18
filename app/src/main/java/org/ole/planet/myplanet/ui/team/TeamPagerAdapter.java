package org.ole.planet.myplanet.ui.team;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.R;

import java.util.ArrayList;
import java.util.List;

public class TeamPagerAdapter extends FragmentStatePagerAdapter {

    private List<String> list;
    public TeamPagerAdapter(FragmentManager fm) {
        super(fm);
        list = new ArrayList<>();
        list.add(MainApplication.context.getString(R.string.discussion));
        list.add(MainApplication.context.getString(R.string.joined_members));
        list.add(MainApplication.context.getString(R.string.requested_member));
        list.add(MainApplication.context.getString(R.string.courses));
        list.add(MainApplication.context.getString(R.string.resources));
    }

    @Override
    public Fragment getItem(int position) {
        if (position == 0)
            return new DiscussionListFragment();
        else if(position == 1)
            return new MembersFragment();
        else if(position == 2)
            return new JoinedMemberFragment();
        else if(position == 3)
            return new TeamCourseFragment();
        else if(position == 4)
            return new TeamResourceFragment();
        return null;
    }

    @Override
    public int getCount() {
        return list.size();
    }



}
