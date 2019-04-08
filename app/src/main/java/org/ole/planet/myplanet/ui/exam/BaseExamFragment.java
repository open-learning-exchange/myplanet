package org.ole.planet.myplanet.ui.exam;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.widget.CompoundButton;

import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmAnswer;
import org.ole.planet.myplanet.model.RealmCourseProgress;
import org.ole.planet.myplanet.model.RealmExamQuestion;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.CameraUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.HashMap;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmResults;

public abstract class BaseExamFragment extends Fragment implements CameraUtils.ImageCaptureCallback {
    RealmStepExam exam;
    DatabaseService db;
    Realm mRealm;
    String stepId;
    String id = "";
    String type = "exam";
    int currentIndex = 0;
    int stepNumber;
    RealmResults<RealmExamQuestion> questions;
    String ans = "";
    RealmUserModel user;
    RealmSubmission sub;
    HashMap<String, String> listAns;
    boolean isMySurvey;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DatabaseService(getActivity());
        mRealm = db.getRealmInstance();
        if (getArguments() != null) {
            stepId = getArguments().getString("stepId");
            stepNumber = getArguments().getInt("stepNum");
            isMySurvey = getArguments().getBoolean("isMySurvey");
            checkId();
            checkType();
        }
    }

    private void checkId() {
        if (TextUtils.isEmpty(stepId)) {
            id = getArguments().getString("id");
            if (isMySurvey) {
                sub = mRealm.where(RealmSubmission.class).equalTo("id", id).findFirst();
                id = sub.getParentId();
            }
            Utilities.log("Id " + id);
        }
    }

    private void checkType() {
        if (getArguments().containsKey("type")) {
            type = getArguments().getString("type");
        }
    }

    public void initExam() {
        if (!TextUtils.isEmpty(stepId)) {
            exam = mRealm.where(RealmStepExam.class).equalTo("stepId", stepId).findFirst();
        } else {
            exam = mRealm.where(RealmStepExam.class).equalTo("id", id).findFirst();
        }
    }

    public void checkAnsAndContinue(boolean cont) {
        if (cont) {
            currentIndex = currentIndex + 1;
            continueExam();
        } else {
            Utilities.toast(getActivity(), "Invalid answer");
        }
    }

    private void continueExam() {

        if (currentIndex < questions.size()) {
            startExam(questions.get(currentIndex));
        } else if (type.startsWith("survey")) {
            showUserInfoDialog();
        } else {
            saveCourseProgress();
            new AlertDialog.Builder(getActivity())
                    .setTitle("Thank you for taking this " + type + ". We wish you all the best")
                    .setPositiveButton("Finish", (dialogInterface, i) -> {
                        getActivity().onBackPressed();
                        try {
                            CameraUtils.CapturePhoto(BaseExamFragment.this);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).show();
        }
    }

    private void saveCourseProgress() {
        RealmCourseProgress progress = mRealm.where(RealmCourseProgress.class)
                .equalTo("courseId", exam.getCourseId()).equalTo("stepNum", stepNumber)
                .findFirst();
        if (progress != null) {
            if (!mRealm.isInTransaction())
                mRealm.beginTransaction();
            progress.setPassed(sub.getStatus().equals("graded"));
            mRealm.commitTransaction();
        }
    }

    private void showUserInfoDialog() {
        if (!isMySurvey) {
            UserInformationFragment.getInstance(sub.getId()).show(getChildFragmentManager(), "");
        } else {
            if (!mRealm.isInTransaction())
                mRealm.beginTransaction();
            sub.setStatus("complete");
            mRealm.commitTransaction();
            Utilities.toast(getActivity(), "Thank you for taking this survey.");
            getActivity().onBackPressed();
        }

    }


    public boolean showErrorMessage(String s) {
        if (ans.isEmpty() && listAns.isEmpty()) {
            Utilities.toast(getActivity(), s);
            return true;
        }
        return false;
    }

    public RealmAnswer createAnswer(RealmList<RealmAnswer> list) {
        RealmAnswer answer;
        if (list == null) {
            list = new RealmList<>();
        }
        if (list.size() > currentIndex) {
            answer = list.get(currentIndex);
        } else {
            answer = mRealm.createObject(RealmAnswer.class, UUID.randomUUID().toString());
        }
        return answer;
    }

    public void addAnswer(CompoundButton compoundButton) {
        if (compoundButton.getTag() != null) {
            listAns.put(compoundButton.getText() + "", compoundButton.getTag() + "");
        } else {
            ans = compoundButton.getText() + "";
        }
    }

    abstract void startExam(RealmExamQuestion question);

    @Override
    public void onImageCapture(String fileUri) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        sub.setLocalUserImageUri(fileUri);
        mRealm.close();
    }
}
