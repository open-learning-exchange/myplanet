package org.ole.planet.myplanet.ui.course;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseContainerFragment;
import org.ole.planet.myplanet.callback.OnRatingChangeListener;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmCourseStep;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmRating;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;

import java.util.List;

import br.tiagohm.markdownview.MarkdownView;
import io.realm.Realm;
import io.realm.RealmResults;


public class CourseDetailFragment extends BaseContainerFragment implements OnRatingChangeListener {
    TextView subjectLevel, gradeLevel, method, language, noOfExams;
    LinearLayout llRating;
    MarkdownView description;
    DatabaseService dbService;
    Realm mRealm;
    RealmMyCourse courses;
    RealmUserModel user;
    String id;
    Button btnResources, btnOpen;
    RecyclerView rv_step_list;

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
        courses = mRealm.where(RealmMyCourse.class).equalTo("courseId", id).findFirst();
        user = new UserProfileDbHandler(getActivity()).getUserModel();
        initView(v);
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initRatingView("course", courses.getCourseId(), courses.getCourseTitle(), this);
        setCourseData();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    private void initView(View v) {
        description = v.findViewById(R.id.description);
        subjectLevel = v.findViewById(R.id.subject_level);
        gradeLevel = v.findViewById(R.id.grade_level);
        language = v.findViewById(R.id.language);
        rv_step_list = v.findViewById(R.id.steps_list);
        method = v.findViewById(R.id.method);
        noOfExams = v.findViewById(R.id.no_of_exams);
        btnResources = v.findViewById(R.id.btn_resources);
        btnOpen = v.findViewById(R.id.btn_open);
        llRating = v.findViewById(R.id.ll_rating);
//        llRating.setVisibility(Constants.showBetaFeature(Constants.KEY_RATING, getActivity()) ? View.VISIBLE : View.GONE);
    }


    private void setCourseData() {
        subjectLevel.setText(courses.getSubjectLevel());
        method.setText(courses.getMethod());
        gradeLevel.setText(courses.getGradeLevel());
        language.setText(courses.getLanguageOfInstruction());
        description.loadMarkdown(courses.getDescription());
        noOfExams.setText(RealmStepExam.getNoOfExam(mRealm, id) + "");
        final RealmResults resources = mRealm.where(RealmMyLibrary.class)
                .equalTo("courseId", id)
                .equalTo("resourceOffline", false)
                .isNotNull("resourceLocalAddress")
                .findAll();
        setResourceButton(resources, btnResources);
        final List<RealmMyLibrary> downloadedResources = mRealm.where(RealmMyLibrary.class)
                .equalTo("resourceOffline", true)
                .equalTo("courseId", id)
                .isNotNull("resourceLocalAddress")
                .findAll();
        setOpenResourceButton(downloadedResources, btnOpen);
        onRatingChanged();
        setStepsList();
    }

    private void setStepsList() {
        List<RealmCourseStep> steps = RealmCourseStep.getSteps(mRealm, courses.getCourseId());
        rv_step_list.setLayoutManager(new LinearLayoutManager(getActivity()));
        rv_step_list.setAdapter(new AdapterSteps(getActivity(), steps, mRealm));

    }


    @Override
    public void onRatingChanged() {
        JsonObject object = RealmRating.getRatingsById(mRealm, "course", courses.getCourseId(), user.getId());
        setRatings(object);
    }

    @Override
    public void onDownloadComplete() {
        super.onDownloadComplete();
        setCourseData();
    }
}
