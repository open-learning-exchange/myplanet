package org.ole.planet.myplanet.courses;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;

import org.ole.planet.myplanet.Data.realm_UserModel;
import org.ole.planet.myplanet.Data.realm_courseProgress;
import org.ole.planet.myplanet.Data.realm_courseSteps;
import org.ole.planet.myplanet.Data.realm_myCourses;
import org.ole.planet.myplanet.Data.realm_myLibrary;
import org.ole.planet.myplanet.Data.realm_stepExam;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseContainerFragment;
import org.ole.planet.myplanet.courses.exam.TakeExamFragment;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.userprofile.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * A simple {@link Fragment} subclass.
 */
public class CourseStepFragment extends BaseContainerFragment {

    TextView tvTitle;
    WebView wvDesc;
    String stepId;
    Button btnResource, btnExam, btnOpen;
    DatabaseService dbService;
    Realm mRealm;
    realm_courseSteps step;
    List<realm_myLibrary> resources;
    List<realm_stepExam> stepExams;
    realm_UserModel user;
    int stepNumber;

    public CourseStepFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            stepId = getArguments().getString("stepId");
            stepNumber = getArguments().getInt("stepNumber");
        }
        setUserVisibleHint(false);
    }

    @Override
    public void playVideo(String videoType, realm_myLibrary items) {

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_course_step, container, false);
        tvTitle = v.findViewById(R.id.tv_title);
        wvDesc = v.findViewById(R.id.wv_desc);
        btnExam = v.findViewById(R.id.btn_take_test);
        btnOpen = v.findViewById(R.id.btn_open);
        btnResource = v.findViewById(R.id.btn_resources);
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        user = new UserProfileDbHandler(getActivity()).getUserModel();
        btnExam.setVisibility(Constants.showBetaFeature(Constants.KEY_EXAM, getActivity()) ? View.VISIBLE : View.GONE);
        return v;
    }

    public void saveCourseProgress() {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        realm_courseProgress courseProgress = mRealm.where(realm_courseProgress.class)
                .equalTo("courseId", step.getCourseId())
                .equalTo("userId", user.getId())
                .equalTo("stepNum", stepNumber)
                .findFirst();
        if (courseProgress == null) {
            courseProgress = mRealm.createObject(realm_courseProgress.class, UUID.randomUUID().toString());
        }
        courseProgress.setCourseId(step.getCourseId());
        courseProgress.setStepNum(stepNumber);
        courseProgress.setPassed(stepExams.size() <= 0);
        courseProgress.setCreatedOn(new Date().getTime());
        courseProgress.setParentCode(user.getParentCode());
        courseProgress.setUserId(user.getId());
        mRealm.commitTransaction();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mRealm.close();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        step = mRealm.where(realm_courseSteps.class).equalTo("id", stepId).findFirst();
        resources = mRealm.where(realm_myLibrary.class).equalTo("stepId", stepId).findAll();
        stepExams = mRealm.where(realm_stepExam.class).equalTo("stepId", stepId).findAll();
        if (resources != null)
            btnResource.setText("Resources [" + resources.size() + "]");
        if (stepExams != null)
            btnExam.setText("Take Test [" + stepExams.size() + "]");
        tvTitle.setText(step.getStepTitle());
        wvDesc.loadDataWithBaseURL(null, step.getDescription(), "text/html", "utf-8", null);
        setListeners();

    }

    @Override
    public void setMenuVisibility(final boolean visible) {
        super.setMenuVisibility(visible);
        if (visible && realm_myCourses.isMyCourse(user.getId(), step.getCourseId(), mRealm)) {
            saveCourseProgress();
        }
    }

    private void setListeners() {
        final RealmResults offlineResources = mRealm.where(realm_myLibrary.class)
                .equalTo("stepId", stepId)
                .equalTo("resourceOffline", false)
                .isNotNull("resourceLocalAddress")
                .findAll();
//        if (offlineResources == null || offlineResources.size() == 0) {
//            btnResource.setVisibility(View.GONE);
//        }
//        btnResource.setOnClickListener(view -> {
//            if (offlineResources.size() > 0) {
//                showDownloadDialog(offlineResources);
//            }
//        });
        setResourceButton(offlineResources, btnResource);

        btnExam.setOnClickListener(view -> {
            if (stepExams.size() > 0) {
                Fragment takeExam = new TakeExamFragment();
                Bundle b = new Bundle();
                b.putString("stepId", stepId);
                b.putInt("stepNum", stepNumber);
                takeExam.setArguments(b);
                homeItemClickListener.openCallFragment(takeExam);
            }
        });
        final List<realm_myLibrary> downloadedResources = mRealm.where(realm_myLibrary.class)
                .equalTo("stepId", stepId)
                .equalTo("resourceOffline", true)
                .isNotNull("resourceLocalAddress")
                .findAll();
//
//        if (downloadedResources == null || downloadedResources.size() == 0) {
//            btnOpen.setVisibility(View.GONE);
//        } else {
//            btnOpen.setOnClickListener(view -> {
//                showResourceList(downloadedResources);
//            });
//        }
        setOpenResourceButton(downloadedResources, btnOpen);

    }

    @Override
    public void onDownloadComplete() {
        super.onDownloadComplete();
        Utilities.log("On download complete");
        setListeners();
    }
}
