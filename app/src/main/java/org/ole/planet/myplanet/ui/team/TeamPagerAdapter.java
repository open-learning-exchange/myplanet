package org.ole.planet.myplanet.ui.team;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.ui.team.teamCourse.TeamCourseFragment;
import org.ole.planet.myplanet.ui.team.teamDiscussion.DiscussionListFragment;
import org.ole.planet.myplanet.ui.team.teamMember.JoinedMemberFragment;
import org.ole.planet.myplanet.ui.team.teamMember.MembersFragment;
import org.ole.planet.myplanet.ui.team.teamResource.TeamResourceFragment;

import java.util.ArrayList;
import java.util.List;

public class TeamPagerAdapter extends FragmentStatePagerAdapter {

    private List<String> list;
    String teamId;
    public TeamPagerAdapter(FragmentManager fm, String id, boolean isMyTeam) {
        super(fm);
        this.teamId = id;
        list = new ArrayList<>();
        list.add(MainApplication.context.getString(R.string.plan));
        list.add(MainApplication.context.getString(R.string.joined_members));
        if (isMyTeam){
            list.add(MainApplication.context.getString(R.string.discussion));
            list.add(MainApplication.context.getString(R.string.requested_member));
            list.add(MainApplication.context.getString(R.string.courses));
            list.add(MainApplication.context.getString(R.string.resources));
        }
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        return list.get(position);
    }

    @Override
    public Fragment getItem(int position) {
        Fragment f = null;
        if (position == 0)
            f =  new PlanFragment();
        else if(position == 1)
            f =  new JoinedMemberFragment();
        else if(position == 2)
            f =  new DiscussionListFragment();
        else if(position == 3)
            f =  new MembersFragment();
        else if(position == 4)
            f =  new TeamCourseFragment();
        else if(position == 5)
            f=  new TeamResourceFragment();
        Bundle b = new Bundle();
        b.putString("id", teamId);
        f.setArguments(b);
        return f;
    }

    @Override
    public int getCount() {
        return list.size();
    }



}
