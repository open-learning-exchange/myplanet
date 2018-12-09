package org.ole.planet.myplanet.courses;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.ole.planet.myplanet.Data.realm_UserModel;
import org.ole.planet.myplanet.Data.realm_courseProgress;
import org.ole.planet.myplanet.Data.realm_courseSteps;
import org.ole.planet.myplanet.Data.realm_myCourses;
import org.ole.planet.myplanet.Data.realm_submissions;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.userprofile.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.CustomViewPager;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class TakeCourseFragment extends Fragment implements ViewPager.OnPageChangeListener, View.OnClickListener {

    CustomViewPager mViewPager;
    TextView tvCourseTitle, tvCompleted, tvStepTitle, tvSteps;
    SeekBar courseProgress;
    DatabaseService dbService;
    Realm mRealm;
    String courseId;
    realm_myCourses currentCourse;
    List<realm_courseSteps> steps;
    Button btnAddRemove;
    ImageView next, previous;
    realm_UserModel userModel;

    public TakeCourseFragment() {
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            courseId = getArguments().getString("id");
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
        userModel = new UserProfileDbHandler(getActivity()).getUserModel();
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
        btnAddRemove = v.findViewById(R.id.btn_remove);
        courseProgress = v.findViewById(R.id.course_progress);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        tvCourseTitle.setText(currentCourse.getCourseTitle());
        steps = realm_courseSteps.getSteps(mRealm, courseId);
        if (steps == null || steps.size() == 0) {
            next.setVisibility(View.GONE);
            previous.setVisibility(View.GONE);
        }
        mViewPager.setAdapter(new CoursePagerAdapter(getChildFragmentManager(), courseId, realm_courseSteps.getStepIds(mRealm, courseId)));
        mViewPager.addOnPageChangeListener(this);
        setCourseData();
        next.setOnClickListener(this);
        previous.setOnClickListener(this);
        btnAddRemove.setOnClickListener(this);
    }


    private void setCourseData() {
        tvStepTitle.setText(currentCourse.getCourseTitle());
        btnAddRemove.setText(!TextUtils.equals(currentCourse.getUserId(), userModel.getId()) ? "Add To My Courses" : "Remove");
        tvSteps.setText("Step 0/" + steps.size());
        if (steps != null)
            courseProgress.setMax(steps.size());
        int i = realm_courseProgress.getCurrentProgress(steps, mRealm, courseId);
        courseProgress.setProgress(i);
        courseProgress.setVisibility(TextUtils.equals(currentCourse.getUserId(), userModel.getId()) ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {
        if (position > 0) {
            tvStepTitle.setText(steps.get(position - 1).getStepTitle());
            if ((position - 1) < steps.size())
                changeNextButtonState(position);
        } else {
            next.setClickable(true);
            next.setColorFilter(getResources().getColor(R.color.md_white_1000));
        }
        tvSteps.setText(String.format("Step %d/%d", position, steps.size()));
    }

    private void changeNextButtonState(int position) {
        if (realm_submissions.isStepCompleted(mRealm, steps.get(position - 1).getId(), userModel.getId())) {
            next.setClickable(true);
            next.setColorFilter(getResources().getColor(R.color.md_white_1000));
        } else {
            next.setColorFilter(getResources().getColor(R.color.md_grey_500));
            next.setClickable(false);
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        Utilities.log("State " + state);
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
            case R.id.btn_remove:
                addRemoveCourse();
                break;
        }
    }

    private void addRemoveCourse() {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        currentCourse.setUserId(TextUtils.isEmpty(currentCourse.getUserId()) ? userModel.getId() : "");
        mRealm.commitTransaction();
        Utilities.toast(getActivity(), "Course " + (TextUtils.equals(currentCourse.getUserId(), userModel.getId()) ? " added to" : " removed from ") + " my courses");
        setCourseData();
    }

    private boolean isValidClickRight() {
        return mViewPager.getAdapter() != null && mViewPager.getCurrentItem() < mViewPager.getAdapter().getCount();
    }

    public boolean isValidClickLeft() {
        return mViewPager.getAdapter() != null && mViewPager.getCurrentItem() > 0;
    }
}
