package org.ole.planet.myplanet.ui.course;


import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmCourseActivity;
import org.ole.planet.myplanet.model.RealmCourseProgress;
import org.ole.planet.myplanet.model.RealmCourseStep;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmRemovedLog;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.CustomViewPager;
import org.ole.planet.myplanet.utilities.DialogUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

import io.realm.Realm;

public class TakeCourseFragment extends Fragment implements ViewPager.OnPageChangeListener, View.OnClickListener {

    CustomViewPager mViewPager;
    TextView tvCourseTitle, tvCompleted, tvStepTitle, tvSteps;
    SeekBar courseProgress;
    DatabaseService dbService;
    Realm mRealm;
    String courseId;
    RealmMyCourse currentCourse;
    List<RealmCourseStep> steps;
    Button btnAddRemove;
    TextView next, previous;
    RealmUserModel userModel;
    int position = 0;

    public TakeCourseFragment() {
    }

    public static TakeCourseFragment newInstance(Bundle b) {
        TakeCourseFragment takeCourseFragment = new TakeCourseFragment();
        takeCourseFragment.setArguments(b);
        return takeCourseFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            courseId = getArguments().getString("id");
            if (getArguments().containsKey("position")) {
                position = getArguments().getInt("position");
            }
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
        currentCourse = mRealm.where(RealmMyCourse.class).equalTo("courseId", courseId).findFirst();
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
        steps = RealmCourseStep.getSteps(mRealm, courseId);
        if (steps == null || steps.size() == 0) {
            next.setVisibility(View.GONE);
            previous.setVisibility(View.GONE);
        }


        mViewPager.setAdapter(new CoursePagerAdapter(getChildFragmentManager(), courseId, RealmCourseStep.getStepIds(mRealm, courseId)));
        mViewPager.addOnPageChangeListener(this);
        if (mViewPager.getCurrentItem() == 0) {
            previous.setTextColor(getResources().getColor(R.color.md_grey_500));
        }

        setCourseData();
        setListeners();
        mViewPager.setCurrentItem(position);


    }

    private void setListeners() {
        next.setOnClickListener(this);
        previous.setOnClickListener(this);
        btnAddRemove.setOnClickListener(this);
        courseProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                int currentProgress = RealmCourseProgress.getCurrentProgress(steps, mRealm, userModel.getId(), courseId);
                if (b && i <= currentProgress + 1) {
                    mViewPager.setCurrentItem(i);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    private void setCourseData() {
        tvStepTitle.setText(currentCourse.getCourseTitle());
        if (!currentCourse.getUserId().contains(userModel.getId())) {
            btnAddRemove.setVisibility(View.VISIBLE);
            btnAddRemove.setText(getString(R.string.join));
            DialogUtils.getAlertDialog(getActivity(), "Do you want to join this course?", "Join this course", (dialog, which) -> addRemoveCourse());
        } else {
            btnAddRemove.setVisibility(View.GONE);
        }
        RealmCourseActivity.createActivity(mRealm, userModel, currentCourse);
        tvSteps.setText("Step 0/" + steps.size());
        if (steps != null)
            courseProgress.setMax(steps.size());
        int i = RealmCourseProgress.getCurrentProgress(steps, mRealm, userModel.getId(), courseId);
        if (i < steps.size())
            courseProgress.setSecondaryProgress(i + 1);
        courseProgress.setProgress(i);
        courseProgress.setVisibility(currentCourse.getUserId().contains(userModel.getId()) ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
        if (position > 0) {
            tvStepTitle.setText(steps.get(position - 1).getStepTitle());
            Utilities.log("Po " + position + " " + steps.size());
            if ((position - 1) < steps.size())
                changeNextButtonState(position);
        } else {
            next.setClickable(true);
            next.setTextColor(getResources().getColor(R.color.md_white_1000));
            tvStepTitle.setText(currentCourse.getCourseTitle());
        }
        int i = RealmCourseProgress.getCurrentProgress(steps, mRealm, userModel.getId(), courseId);
        if (i < steps.size())
            courseProgress.setSecondaryProgress(i + 1);
        courseProgress.setProgress(i);
        tvSteps.setText(String.format("Step %d/%d", position, steps.size()));
    }

    private void changeNextButtonState(int position) {
        Utilities.log(RealmSubmission.isStepCompleted(mRealm, steps.get(position - 1).getId(), userModel.getId()) + " is step completed");
        if (RealmSubmission.isStepCompleted(mRealm, steps.get(position - 1).getId(), userModel.getId()) || !Constants.showBetaFeature(Constants.KEY_EXAM, getActivity())) {
            next.setClickable(true);
            next.setTextColor(getResources().getColor(R.color.md_white_1000));
        } else {
            next.setTextColor(getResources().getColor(R.color.md_grey_500));
            next.setClickable(false);
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        Utilities.log("State " + state);
    }

    private void onClickNext() {
        if (mViewPager.getCurrentItem() == steps.size()) {
            next.setTextColor(getResources().getColor(R.color.md_grey_500));
        }
    }

    private void onClickPrevious() {
        if (mViewPager.getCurrentItem() - 1 == 0) {
            previous.setTextColor(getResources().getColor(R.color.md_grey_500));
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.next_step:
                if (isValidClickRight()) {
                    mViewPager.setCurrentItem(mViewPager.getCurrentItem() + 1);
                    previous.setTextColor(getResources().getColor(R.color.md_white_1000));
                }
                onClickNext();
                break;
            case R.id.previous_step:
                onClickPrevious();
                if (isValidClickLeft()) {
                    mViewPager.setCurrentItem(mViewPager.getCurrentItem() - 1);
                }
                break;
            case R.id.btn_remove:
                addRemoveCourse();
                break;
        }
    }

    private void addRemoveCourse() {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        if (currentCourse.getUserId().contains(userModel.getId())) {
            currentCourse.removeUserId(userModel.getId());
            RealmRemovedLog.onRemove(mRealm, "courses", userModel.getId(), courseId);
        } else {
            currentCourse.setUserId(userModel.getId());
            RealmRemovedLog.onAdd(mRealm, "courses", userModel.getId(), courseId);
        }
        Utilities.toast(getActivity(), "Course " + (currentCourse.getUserId().contains(userModel.getId()) ? getString(R.string.added_to) : getString(R.string.removed_from)) + " " + getString(R.string.my_courses));
        setCourseData();
    }

    private boolean isValidClickRight() {
        return mViewPager.getAdapter() != null && mViewPager.getCurrentItem() < mViewPager.getAdapter().getCount();
    }

    public boolean isValidClickLeft() {
        return mViewPager.getAdapter() != null && mViewPager.getCurrentItem() > 0;
    }


}
