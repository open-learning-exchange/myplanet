package org.ole.planet.myplanet.ui.course;


import static org.ole.planet.myplanet.MainApplication.context;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.base.BaseContainerFragment;
import org.ole.planet.myplanet.callback.OnRatingChangeListener;
import org.ole.planet.myplanet.databinding.FragmentCourseDetailBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmCourseStep;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmRating;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;

import java.util.List;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonPlugin;
import io.noties.markwon.image.ImagesPlugin;
import io.noties.markwon.image.file.FileSchemeHandler;
import io.noties.markwon.movement.MovementMethodPlugin;
import io.realm.Realm;
import io.realm.RealmResults;

public class CourseDetailFragment extends BaseContainerFragment implements OnRatingChangeListener {
    private FragmentCourseDetailBinding fragmentCourseDetailBinding;
    DatabaseService dbService;
    Realm mRealm;
    RealmMyCourse courses;
    RealmUserModel user;
    String id;
    private Markwon markwon;

    public CourseDetailFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            id = getArguments().getString("courseId");
        }
        markwon = Markwon.builder(context)
                .usePlugin(ImagesPlugin.create())
                .usePlugin(MovementMethodPlugin.none())
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configure(@NonNull MarkwonPlugin.Registry registry) {
                        registry.require(ImagesPlugin.class, imagesPlugin ->
                                imagesPlugin.addSchemeHandler(FileSchemeHandler.create())
                        );
                    }
                })
                .build();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentCourseDetailBinding = FragmentCourseDetailBinding.inflate(inflater, container, false);
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        courses = mRealm.where(RealmMyCourse.class).equalTo("courseId", id).findFirst();
        user = new UserProfileDbHandler(getActivity()).getUserModel();
        return fragmentCourseDetailBinding.getRoot();
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

    private void setCourseData() {
        setTextViewVisibility(fragmentCourseDetailBinding.subjectLevel, courses.getSubjectLevel(), fragmentCourseDetailBinding.ltSubjectLevel);
        setTextViewVisibility(fragmentCourseDetailBinding.method, courses.getMethod(), fragmentCourseDetailBinding.ltMethod);
        setTextViewVisibility(fragmentCourseDetailBinding.gradeLevel, courses.getGradeLevel(), fragmentCourseDetailBinding.ltGradeLevel);
        setTextViewVisibility(fragmentCourseDetailBinding.language, courses.getLanguageOfInstruction(), fragmentCourseDetailBinding.ltLanguage);
        String markdownContentWithLocalPaths = CourseStepFragment.prependBaseUrlToImages(courses.getDescription(), "file://" + MainApplication.context.getExternalFilesDir(null) + "/ole/");
        markwon.setMarkdown(fragmentCourseDetailBinding.description, markdownContentWithLocalPaths);
        fragmentCourseDetailBinding.noOfExams.setText(RealmStepExam.getNoOfExam(mRealm, id) + "");
        final RealmResults resources = mRealm.where(RealmMyLibrary.class).equalTo("courseId", id).equalTo("resourceOffline", false).isNotNull("resourceLocalAddress").findAll();
        setResourceButton(resources, fragmentCourseDetailBinding.btnResources);
        final List<RealmMyLibrary> downloadedResources = mRealm.where(RealmMyLibrary.class).equalTo("resourceOffline", true).equalTo("courseId", id).isNotNull("resourceLocalAddress").findAll();
        setOpenResourceButton(downloadedResources, fragmentCourseDetailBinding.btnOpen);
        onRatingChanged();
        setStepsList();
    }

    private void setTextViewVisibility(TextView textView, String content, View layout) {
        if (content.isEmpty()) {
            layout.setVisibility(View.GONE);
        } else {
            textView.setText(content);
        }
    }

    private void setStepsList() {
        List<RealmCourseStep> steps = RealmCourseStep.getSteps(mRealm, courses.getCourseId());
        fragmentCourseDetailBinding.stepsList.setLayoutManager(new LinearLayoutManager(getActivity()));
        fragmentCourseDetailBinding.stepsList.setAdapter(new AdapterSteps(getActivity(), steps, mRealm));
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
