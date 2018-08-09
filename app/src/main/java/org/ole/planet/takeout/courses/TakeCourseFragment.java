package org.ole.planet.takeout.courses;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.ole.planet.takeout.Data.realm_courseSteps;
import org.ole.planet.takeout.Data.realm_myCourses;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.datamanager.DatabaseService;

import java.util.List;

import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class TakeCourseFragment extends Fragment implements ViewPager.OnPageChangeListener, View.OnClickListener {

    ViewPager mViewPager;
    TextView tvCourseTitle, tvCompleted, tvStepTitle, tvSteps;
    SeekBar courseProgress;
    DatabaseService dbService;
    Realm mRealm;
    String courseId;
    realm_myCourses currentCourse;
    List<realm_courseSteps> steps;
    ImageView next, previous;

    public TakeCourseFragment() {
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            courseId = getArguments().getString("courseId");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_take_course, container, false);
        mViewPager = v.findViewById(R.id.view_pager_course);
        initView(v);
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        currentCourse = mRealm.where(realm_myCourses.class).equalTo("courseId", courseId).findFirst();
        return v;
    }

    private void initView(View v) {
        tvCourseTitle = v.findViewById(R.id.tv_course_title);
        tvStepTitle = v.findViewById(R.id.tv_step_title);
        tvCompleted = v.findViewById(R.id.tv_percentage_complete);
        tvSteps = v.findViewById(R.id.tv_step);
        next = v.findViewById(R.id.next_step);
        previous = v.findViewById(R.id.previous_step);
        courseProgress = v.findViewById(R.id.course_progress);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        tvCourseTitle.setText(currentCourse.getCourseTitle());
        steps = realm_courseSteps.getSteps(mRealm, courseId);
        mViewPager.setAdapter(new CoursePagerAdapter(getChildFragmentManager(), courseId, realm_courseSteps.getStepIds(mRealm, courseId)));
        mViewPager.addOnPageChangeListener(this);
        setCourseData();
        next.setOnClickListener(this);
        previous.setOnClickListener(this);
    }

    private void setCourseData() {
        tvStepTitle.setText(currentCourse.getCourseTitle());
        tvSteps.setText("Step 0/" + steps.size());
        if (steps != null)
            courseProgress.setMax(steps.size());
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {
        if (position > 0) {
            tvStepTitle.setText(steps.get(position - 1).getStepTitle());
        }
        tvSteps.setText(String.format("Step %d/%d", position, steps.size()));

    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.next_step:
                if (isValidClickRight()) {
                    mViewPager.setCurrentItem(mViewPager.getCurrentItem() + 1);
                }
                break;
            case R.id.previous_step:
                if (isValidClickLeft()) {
                    mViewPager.setCurrentItem(mViewPager.getCurrentItem() - 1);
                }
                break;
        }
    }

    private boolean isValidClickRight() {
        return mViewPager.getAdapter() != null && mViewPager.getCurrentItem() < mViewPager.getAdapter().getCount();
    }

    public boolean isValidClickLeft() {
        return mViewPager.getAdapter() != null && mViewPager.getCurrentItem() > 0;
    }
}
