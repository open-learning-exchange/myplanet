package org.ole.planet.myplanet.ui.course;


import static org.ole.planet.myplanet.MainApplication.context;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseContainerFragment;
import org.ole.planet.myplanet.databinding.FragmentCourseStepBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmCourseProgress;
import org.ole.planet.myplanet.model.RealmCourseStep;
import org.ole.planet.myplanet.model.RealmExamQuestion;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.exam.TakeExamFragment;
import org.ole.planet.myplanet.utilities.CameraUtils;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.noties.markwon.Markwon;
import io.noties.markwon.image.ImagesPlugin;
import io.noties.markwon.image.gif.GifMediaDecoder;
import io.noties.markwon.image.svg.SvgPictureMediaDecoder;
import io.noties.markwon.movement.MovementMethodPlugin;
import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmResults;

public class CourseStepFragment extends BaseContainerFragment implements CameraUtils.ImageCaptureCallback {
    private FragmentCourseStepBinding fragmentCourseStepBinding;
    String stepId;
    DatabaseService dbService;
    Realm mRealm;
    RealmCourseStep step;
    List<RealmMyLibrary> resources;
    List<RealmStepExam> stepExams;
    RealmUserModel user;
    int stepNumber;
    private Markwon markwon;

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
        markwon = Markwon.builder(context)
                .usePlugin(ImagesPlugin.create())
                .usePlugin(ImagesPlugin.create(plugin -> {
                    plugin.addMediaDecoder(SvgPictureMediaDecoder.create());
                }))
                .usePlugin(ImagesPlugin.create(plugin -> {
                    plugin.addMediaDecoder(GifMediaDecoder.create());
                }))
                .build();
    }

    @Override
    public void playVideo(String videoType, RealmMyLibrary items) {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentCourseStepBinding = FragmentCourseStepBinding.inflate(inflater, container, false);
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        user = new UserProfileDbHandler(getActivity()).getUserModel();
        fragmentCourseStepBinding.btnTakeTest.setVisibility(Constants.showBetaFeature(Constants.KEY_EXAM, getActivity()) ? View.VISIBLE : View.GONE);
        return fragmentCourseStepBinding.getRoot();
    }

    public void saveCourseProgress() {
        if (!mRealm.isInTransaction()) mRealm.beginTransaction();
        RealmCourseProgress courseProgress = mRealm.where(RealmCourseProgress.class).equalTo("courseId", step.getCourseId()).equalTo("userId", user.getId()).equalTo("stepNum", stepNumber).findFirst();
        if (courseProgress == null) {
            courseProgress = mRealm.createObject(RealmCourseProgress.class, UUID.randomUUID().toString());
            courseProgress.setCreatedDate(new Date().getTime());
        }
        courseProgress.setCourseId(step.getCourseId());
        courseProgress.setStepNum(stepNumber);

        if (stepExams.size() == 0) {
            courseProgress.setPassed(true);
        }
        courseProgress.setCreatedOn(user.getPlanetCode());
        courseProgress.setUpdatedDate(new Date().getTime());
        courseProgress.setParentCode(user.getParentCode());
        courseProgress.setUserId(user.getId());
        mRealm.commitTransaction();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mRealm != null) {
            mRealm.close();
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        step = mRealm.where(RealmCourseStep.class).equalTo("id", stepId).findFirst();
        resources = mRealm.where(RealmMyLibrary.class).equalTo("stepId", stepId).findAll();
        stepExams = mRealm.where(RealmStepExam.class).equalTo("stepId", stepId).findAll();
        if (resources != null) fragmentCourseStepBinding.btnResources.setText(getString(R.string.resources) + " ["+ resources.size() + "]");
        hideTestIfNoQuestion();
        fragmentCourseStepBinding.tvTitle.setText(step.getStepTitle());
        String markdownContentWithBaseUrl = prependBaseUrlToImages(step.getDescription(), Utilities.getUrl()+"/");
        Log.d("markdownContent", Utilities.getHeader());
        markwon.setMarkdown(fragmentCourseStepBinding.description, markdownContentWithBaseUrl);

//        markwon.setMarkdown(fragmentCourseStepBinding.description, step.getDescription());
        if (!RealmMyCourse.isMyCourse(user.getId(), step.getCourseId(), mRealm)) {
            fragmentCourseStepBinding.btnTakeTest.setVisibility(View.INVISIBLE);
        }
        setListeners();
    }

    private void hideTestIfNoQuestion() {
        fragmentCourseStepBinding.btnTakeTest.setVisibility(View.INVISIBLE);
        if (stepExams != null && stepExams.size() > 0) {
            String first_step_id = stepExams.get(0).getId();
            RealmResults<RealmExamQuestion> questions = mRealm.where(RealmExamQuestion.class).equalTo("examId", first_step_id).findAll();
            long submissionsCount = mRealm.where(RealmSubmission.class).contains("parentId", step.getCourseId()).notEqualTo("status", "pending", Case.INSENSITIVE).count();

            if (questions != null && questions.size() > 0) {
                fragmentCourseStepBinding.btnTakeTest.setText((submissionsCount > 0 ? getString(R.string.retake_test) : getString(R.string.take_test)) + " [" + stepExams.size() + "]");
                fragmentCourseStepBinding.btnTakeTest.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void setMenuVisibility(final boolean visible) {
        super.setMenuVisibility(visible);
        try {
            if (visible && RealmMyCourse.isMyCourse(user.getId(), step.getCourseId(), mRealm)) {
                saveCourseProgress();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setListeners() {
        final RealmResults notDownloadedResources = mRealm.where(RealmMyLibrary.class).equalTo("stepId", stepId).equalTo("resourceOffline", false).isNotNull("resourceLocalAddress").findAll();
        setResourceButton(notDownloadedResources, fragmentCourseStepBinding.btnResources);

        fragmentCourseStepBinding.btnTakeTest.setOnClickListener(view -> {
            if (stepExams.size() > 0) {
                Fragment takeExam = new TakeExamFragment();
                Bundle b = new Bundle();
                b.putString("stepId", stepId);
                b.putInt("stepNum", stepNumber);
                takeExam.setArguments(b);
                homeItemClickListener.openCallFragment(takeExam);
                CameraUtils.CapturePhoto(this);
            }
        });
        final List<RealmMyLibrary> downloadedResources = mRealm.where(RealmMyLibrary.class).equalTo("stepId", stepId).equalTo("resourceOffline", true).isNotNull("resourceLocalAddress").findAll();

        setOpenResourceButton(downloadedResources, fragmentCourseStepBinding.btnOpen);

    }

    @Override
    public void onDownloadComplete() {
        super.onDownloadComplete();
        setListeners();
    }

    @Override
    public void onImageCapture(String fileUri) {
    }

    private String prependBaseUrlToImages(String markdownContent, String baseUrl) {
        String pattern = "!\\[.*?\\]\\((.*?)\\)";
        Pattern imagePattern = Pattern.compile(pattern);
        Matcher matcher = imagePattern.matcher(markdownContent);

        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String relativePath = matcher.group(1);
            String fullUrl = baseUrl + relativePath;
            matcher.appendReplacement(result, "![](" + fullUrl + ")");
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
