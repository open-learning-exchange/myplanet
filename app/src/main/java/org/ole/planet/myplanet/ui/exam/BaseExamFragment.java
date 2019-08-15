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
import org.ole.planet.myplanet.model.RealmSubmitPhotos;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.CameraUtils;
import org.ole.planet.myplanet.utilities.Utilities;
import org.ole.planet.myplanet.utilities.NetworkUtils;

import java.util.HashMap;
import java.util.UUID;
import java.util.Date;

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
    String mac_addr = NetworkUtils.getMacAddr();
    String date = new Date().toString();
    String photo_path = "";


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


    public void insert_into_submitPhotos(RealmStepExam exam, String sub)
    {
          String exam_id = exam.getId();
          String course_id = exam.getCourseId();
          String member_id = user.getId();
          String submission_id = sub;


          if(!mRealm.isInTransaction())
          {
              mRealm.beginTransaction();
              RealmSubmitPhotos submit = mRealm.createObject(RealmSubmitPhotos.class);
              submit.setId(UUID.randomUUID().toString());
              submit.setSubmission_id(submission_id);
              submit.setCourse_id(course_id);
              submit.setExam_id(exam_id);
              submit.setMember_id(member_id);
              submit.setMac_address(mac_addr);
              submit.setDate(date);
              submit.setPhoto_file_path(photo_path);
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
        photo_path = fileUri;
        mRealm.close();
    }
}
