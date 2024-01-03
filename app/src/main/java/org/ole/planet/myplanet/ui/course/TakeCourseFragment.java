package org.ole.planet.myplanet.ui.course;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.FragmentTakeCourseBinding;
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
import org.ole.planet.myplanet.utilities.DialogUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

import io.realm.Realm;

public class TakeCourseFragment extends Fragment implements ViewPager.OnPageChangeListener, View.OnClickListener {
    private FragmentTakeCourseBinding fragmentTakeCourseBinding;
    DatabaseService dbService;
    Realm mRealm;
    String courseId;
    RealmMyCourse currentCourse;
    List<RealmCourseStep> steps;
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentTakeCourseBinding = FragmentTakeCourseBinding.inflate(inflater, container, false);
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        userModel = new UserProfileDbHandler(getActivity()).getUserModel();
        currentCourse = mRealm.where(RealmMyCourse.class).equalTo("courseId", courseId).findFirst();
        return fragmentTakeCourseBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        fragmentTakeCourseBinding.tvCourseTitle.setText(currentCourse.courseTitle);
        steps = RealmCourseStep.getSteps(mRealm, courseId);
        if (steps == null || steps.size() == 0) {
            fragmentTakeCourseBinding.nextStep.setVisibility(View.GONE);
            fragmentTakeCourseBinding.previousStep.setVisibility(View.GONE);
        }

        fragmentTakeCourseBinding.viewPagerCourse.setAdapter(new CoursePagerAdapter(getChildFragmentManager(), courseId, RealmCourseStep.getStepIds(mRealm, courseId)));
        fragmentTakeCourseBinding.viewPagerCourse.addOnPageChangeListener(this);
        if (fragmentTakeCourseBinding.viewPagerCourse.getCurrentItem() == 0) {
            fragmentTakeCourseBinding.previousStep.setVisibility(View.GONE);
        }

        setCourseData();
        setListeners();
        fragmentTakeCourseBinding.viewPagerCourse.setCurrentItem(position);
    }

    private void setListeners() {
        fragmentTakeCourseBinding.nextStep.setOnClickListener(this);
        fragmentTakeCourseBinding.previousStep.setOnClickListener(this);
        fragmentTakeCourseBinding.btnRemove.setOnClickListener(this);
        fragmentTakeCourseBinding.finishStep.setOnClickListener(this);
        fragmentTakeCourseBinding.courseProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                int currentProgress = RealmCourseProgress.getCurrentProgress(steps, mRealm, userModel.id, courseId);
                if (b && i <= currentProgress + 1) {
                    fragmentTakeCourseBinding.viewPagerCourse.setCurrentItem(i);
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
        fragmentTakeCourseBinding.tvStepTitle.setText(currentCourse.courseTitle);
        if (!currentCourse.getUserId().contains(userModel.id)) {
            fragmentTakeCourseBinding.btnRemove.setVisibility(View.VISIBLE);
            fragmentTakeCourseBinding.btnRemove.setText(getString(R.string.join));
            DialogUtils.getAlertDialog(getActivity(), getString(R.string.do_you_want_to_join_this_course), getString(R.string.join_this_course), (dialog, which) -> addRemoveCourse());
        } else {
            fragmentTakeCourseBinding.btnRemove.setVisibility(View.GONE);
        }
        RealmCourseActivity.createActivity(mRealm, userModel, currentCourse);
        fragmentTakeCourseBinding.tvStep.setText(getString(R.string.step) + " 0/" + steps.size());
        if (steps != null) fragmentTakeCourseBinding.courseProgress.setMax(steps.size());
        int i = RealmCourseProgress.getCurrentProgress(steps, mRealm, userModel.id, courseId);
        if (i < steps.size()) fragmentTakeCourseBinding.courseProgress.setSecondaryProgress(i + 1);
        fragmentTakeCourseBinding.courseProgress.setProgress(i);
        fragmentTakeCourseBinding.courseProgress.setVisibility(currentCourse.getUserId().contains(userModel.id) ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
        if (position > 0) {
            fragmentTakeCourseBinding.tvStepTitle.setText(steps.get(position - 1).stepTitle);
            Utilities.log("Po " + position + " " + steps.size());
            if ((position - 1) < steps.size()) changeNextButtonState(position);
        } else {
            fragmentTakeCourseBinding.nextStep.setClickable(true);
            fragmentTakeCourseBinding.nextStep.setTextColor(getResources().getColor(R.color.md_white_1000));
            fragmentTakeCourseBinding.tvStepTitle.setText(currentCourse.courseTitle);
        }
        int i = RealmCourseProgress.getCurrentProgress(steps, mRealm, userModel.id, courseId);
        if (i < steps.size()) fragmentTakeCourseBinding.courseProgress.setSecondaryProgress(i + 1);
        fragmentTakeCourseBinding.courseProgress.setProgress(i);
        fragmentTakeCourseBinding.tvStep.setText(String.format("Step %d/%d", position, steps.size()));
    }

    private void changeNextButtonState(int position) {
        Utilities.log(RealmSubmission.isStepCompleted(mRealm, steps.get(position - 1).id, userModel.id) + " is step completed");
        if (RealmSubmission.isStepCompleted(mRealm, steps.get(position - 1).id, userModel.id) || !Constants.showBetaFeature(Constants.KEY_EXAM, getActivity())) {
            fragmentTakeCourseBinding.nextStep.setClickable(true);
            fragmentTakeCourseBinding.nextStep.setTextColor(getResources().getColor(R.color.md_white_1000));
        } else {
            fragmentTakeCourseBinding.nextStep.setTextColor(getResources().getColor(R.color.md_grey_500));
            fragmentTakeCourseBinding.nextStep.setClickable(false);
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        Utilities.log("State " + state);
    }

    private void onClickNext() {
        if (fragmentTakeCourseBinding.viewPagerCourse.getCurrentItem() == steps.size()) {
            fragmentTakeCourseBinding.nextStep.setTextColor(getResources().getColor(R.color.md_grey_500));
            fragmentTakeCourseBinding.nextStep.setVisibility(View.GONE);
            fragmentTakeCourseBinding.finishStep.setVisibility(View.VISIBLE);
        }
    }

    private void onClickPrevious() {
        if (fragmentTakeCourseBinding.viewPagerCourse.getCurrentItem() - 1 == 0) {
            fragmentTakeCourseBinding.previousStep.setVisibility(View.GONE);
            fragmentTakeCourseBinding.nextStep.setVisibility(View.VISIBLE);
            fragmentTakeCourseBinding.finishStep.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.next_step:
                if (isValidClickRight()) {
                    fragmentTakeCourseBinding.viewPagerCourse.setCurrentItem(fragmentTakeCourseBinding.viewPagerCourse.getCurrentItem() + 1);
                    fragmentTakeCourseBinding.previousStep.setVisibility(View.VISIBLE);
                }
                onClickNext();
                break;
            case R.id.previous_step:
                onClickPrevious();
                if (isValidClickLeft()) {
                    fragmentTakeCourseBinding.viewPagerCourse.setCurrentItem(fragmentTakeCourseBinding.viewPagerCourse.getCurrentItem() - 1);
                }
                break;
            case R.id.finish_step:
                getActivity().getSupportFragmentManager().popBackStack();
                break;
            case R.id.btn_remove:
                addRemoveCourse();
                break;
        }
    }

    private void addRemoveCourse() {
        if (!mRealm.isInTransaction()) mRealm.beginTransaction();
        if (currentCourse.getUserId().contains(userModel.id)) {
            currentCourse.removeUserId(userModel.id);
            RealmRemovedLog.onRemove(mRealm, "courses", userModel.id, courseId);
        } else {
            currentCourse.setUserId(userModel.id);
            RealmRemovedLog.onAdd(mRealm, "courses", userModel.id, courseId);
        }
        Utilities.toast(getActivity(), "Course " + (currentCourse.getUserId().contains(userModel.id) ? getString(R.string.added_to) : getString(R.string.removed_from)) + " " + getString(R.string.my_courses));
        setCourseData();
    }

    private boolean isValidClickRight() {
        return fragmentTakeCourseBinding.viewPagerCourse.getAdapter() != null && fragmentTakeCourseBinding.viewPagerCourse.getCurrentItem() < fragmentTakeCourseBinding.viewPagerCourse.getAdapter().getCount();
    }

    public boolean isValidClickLeft() {
        return fragmentTakeCourseBinding.viewPagerCourse.getAdapter() != null && fragmentTakeCourseBinding.viewPagerCourse.getCurrentItem() > 0;
    }
}
