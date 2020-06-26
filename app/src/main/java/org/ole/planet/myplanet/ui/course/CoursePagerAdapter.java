package org.ole.planet.myplanet.ui.course;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

public class CoursePagerAdapter extends FragmentStatePagerAdapter {

    private String[] steps;
    private String courseId;

    public CoursePagerAdapter(FragmentManager fm, String courseId, String[] steps) {
        super(fm);
        this.steps = steps;
        this.courseId = courseId;
    }

    @Override
    public Fragment getItem(int position) {
        Bundle b = new Bundle();
        Fragment f;
        if (position == 0) {
            f = new CourseDetailFragment();
            b.putString("courseId", courseId);

        } else {
            f = new CourseStepFragment();
            b.putString("stepId", steps[position - 1]);
            b.putInt("stepNumber", position);
        }
        f.setArguments(b);
        return f;
    }

    @Override
    public int getCount() {
        return steps.length + 1;
    }
}
