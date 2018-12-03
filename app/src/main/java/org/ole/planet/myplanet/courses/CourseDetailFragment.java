package org.ole.planet.myplanet.courses;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.Data.realm_myCourses;
import org.ole.planet.myplanet.Data.realm_myLibrary;
import org.ole.planet.myplanet.Data.realm_rating;
import org.ole.planet.myplanet.Data.realm_stepExam;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseContainerFragment;
import org.ole.planet.myplanet.callback.OnRatingChangeListener;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.library.LibraryDetailFragment;
import org.ole.planet.myplanet.userprofile.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * A simple {@link Fragment} subclass.
 */
public class CourseDetailFragment extends BaseContainerFragment implements OnRatingChangeListener {
    TextView description, subjectLevel, gradeLevel, method, language, noOfExams;

    DatabaseService dbService;
    Realm mRealm;
    realm_myCourses courses;
    String id;
    Button btnResources, btnOpen;

    public CourseDetailFragment() {
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            id = getArguments().getString("courseId");
        }
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_course_detail, container, false);
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        initView(v);
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        courses = mRealm.where(realm_myCourses.class).equalTo("courseId", id).findFirst();
        setCourseData();
    }

    private void initView(View v) {
        description = v.findViewById(R.id.description);
        subjectLevel = v.findViewById(R.id.subject_level);
        gradeLevel = v.findViewById(R.id.grade_level);
        language = v.findViewById(R.id.language);
        method = v.findViewById(R.id.method);
        noOfExams = v.findViewById(R.id.no_of_exams);
        btnResources = v.findViewById(R.id.btn_resources);
        btnOpen = v.findViewById(R.id.btn_open);
        v.findViewById(R.id.ll_rating).setOnClickListener(view -> homeItemClickListener.showRatingDialog("course", courses.getCourseId(), courses.getCourseTitle(), CourseDetailFragment.this));
        initRatingView(v);
    }


    private void setCourseData() {
        subjectLevel.setText(courses.getSubjectLevel());
        method.setText(courses.getMethod());
        gradeLevel.setText(courses.getGradeLevel());
        language.setText(courses.getLanguageOfInstruction());
        description.setText(courses.getDescription());
        noOfExams.setText(realm_stepExam.getNoOfExam(mRealm, id) + "");
        final RealmResults resources = mRealm.where(realm_myLibrary.class)
                .equalTo("courseId", id)
                .equalTo("resourceOffline", false)
                .isNotNull("resourceLocalAddress")
                .findAll();
        setResourceButton(resources, btnResources);
        final List<realm_myLibrary> downloadedResources = mRealm.where(realm_myLibrary.class)
                .equalTo("resourceOffline", true)
                .equalTo("courseId", id)
                .isNotNull("resourceLocalAddress")
                .findAll();
        setOpenResourceButton(downloadedResources, btnOpen);
        onRatingChanged();
    }


    @Override
    public void onRatingChanged() {
        JsonObject object = realm_rating.getRatingsById(mRealm, "course", courses.getCourseId());
        setRatings(object);
    }
    @Override
    public void onDownloadComplete() {
        super.onDownloadComplete();
        Utilities.log("On download complete");
        setCourseData();
    }
}
