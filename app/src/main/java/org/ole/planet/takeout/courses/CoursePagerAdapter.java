package org.ole.planet.takeout.courses;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

public class CoursePagerAdapter extends FragmentStatePagerAdapter {

    String[] steps;

    public CoursePagerAdapter(FragmentManager fm, String[] steps) {
        super(fm);
        this.steps = steps;
    }

    @Override
    public Fragment getItem(int position) {
        CourseStepFragment f = new CourseStepFragment();
        Bundle b = new Bundle();
        b.putString("stepId", steps[position]);
        f.setArguments(b);
        return f;
    }

    @Override
    public int getCount() {
        return steps.length;
    }
}
