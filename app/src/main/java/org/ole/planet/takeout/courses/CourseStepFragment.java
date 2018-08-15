package org.ole.planet.takeout.courses;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;

import org.ole.planet.takeout.Data.realm_courseSteps;
import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.Data.realm_stepExam;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.base.BaseContainerFragment;
import org.ole.planet.takeout.courses.exam.TakeExamFragment;
import org.ole.planet.takeout.datamanager.DatabaseService;

import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * A simple {@link Fragment} subclass.
 */
public class CourseStepFragment extends BaseContainerFragment {

    TextView tvTitle;
    WebView wvDesc;
    String stepId;
    Button btnResource, btnExam;
    DatabaseService dbService;
    Realm mRealm;
    realm_courseSteps step;
    List<realm_myLibrary> resources;
    List<realm_stepExam> stepExams;

    public CourseStepFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            stepId = getArguments().getString("stepId");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_course_step, container, false);
        tvTitle = v.findViewById(R.id.tv_title);
        wvDesc = v.findViewById(R.id.wv_desc);
        btnExam = v.findViewById(R.id.btn_take_test);
        btnResource = v.findViewById(R.id.btn_resources);
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        return v;
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

    private void setListeners() {
        final RealmResults offlineResources = mRealm.where(realm_myLibrary.class)
                .equalTo("stepId", stepId)
                .equalTo("resourceOffline", false)
                .findAll();
        btnResource.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (offlineResources.size() > 0) {
                    showDownloadDialog(offlineResources);
                }
            }
        });
        btnExam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (stepExams.size() > 0){
                    Fragment takeExam  = new TakeExamFragment();
                    Bundle b = new Bundle();
                    b.putString("stepId", stepId);
                    takeExam.setArguments(b);
                    homeItemClickListener.openCallFragment(takeExam);
                }
            }
        });
    }
}
