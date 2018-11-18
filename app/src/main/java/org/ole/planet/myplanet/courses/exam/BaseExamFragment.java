package org.ole.planet.myplanet.courses.exam;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.widget.CompoundButton;


import org.ole.planet.myplanet.Data.realm_UserModel;
import org.ole.planet.myplanet.Data.realm_answer;
import org.ole.planet.myplanet.Data.realm_courseProgress;
import org.ole.planet.myplanet.Data.realm_examQuestion;
import org.ole.planet.myplanet.Data.realm_stepExam;
import org.ole.planet.myplanet.Data.realm_submissions;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.utilities.CameraUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.HashMap;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmResults;

public abstract class BaseExamFragment extends Fragment implements CameraUtils.ImageCaptureCallback {
    realm_stepExam exam;
    DatabaseService db;
    Realm mRealm;
    String stepId;
    String id = "";
    String type = "exam";
    int currentIndex = 0;
    int stepNumber;
    RealmResults<realm_examQuestion> questions;
    String ans = "";
    realm_UserModel user;
    realm_submissions sub;
    HashMap<String, String> listAns;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            stepId = getArguments().getString("stepId");
            stepNumber = getArguments().getInt("stepNum");
            checkId();
            checkType();
        }
    }

    private void checkId() {
        if (TextUtils.isEmpty(stepId)) {
            id = getArguments().getString("id");
        }
    }

    private void checkType() {
        if (getArguments().containsKey("type")) {
            type = getArguments().getString("type");
        }
    }

    public void initExam() {
        if (!TextUtils.isEmpty(stepId)) {
            exam = mRealm.where(realm_stepExam.class).equalTo("stepId", stepId).findFirst();
        } else {
            exam = mRealm.where(realm_stepExam.class).equalTo("id", id).findFirst();
        }
    }

    public void checkAnsAndContinue(boolean cont) {
        if (cont) {
            Utilities.log("Update current index");
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
                    .setPositiveButton("Finish", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            getActivity().onBackPressed();
                            try {
                                CameraUtils.CapturePhoto(BaseExamFragment.this);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }).show();
        }
    }

    private void saveCourseProgress() {
        realm_courseProgress progress = mRealm.where(realm_courseProgress.class)
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
        UserInformationFragment f = new UserInformationFragment();
        Bundle b = new Bundle();
        b.putString("sub_id", sub.getId());
        f.setArguments(b);
        f.show(getChildFragmentManager(), "");
    }


    public boolean showErrorMessage(String s) {
        if (ans.isEmpty() && listAns.isEmpty()) {
            Utilities.toast(getActivity(), "Please select answer");
            return true;
        }
        return false;
    }

    public realm_answer createAnswer(RealmList<realm_answer> list) {
        realm_answer answer;
        if (list == null) {
            list = new RealmList<>();
        }
        if (list.size() > currentIndex) {
            answer = list.get(currentIndex);
        } else {
            answer = mRealm.createObject(realm_answer.class, UUID.randomUUID().toString());
        }
        return answer;
    }

    public void addAnswer(CompoundButton compoundButton) {
        if (compoundButton.getTag() != null) {
            Utilities.log("Tag " + compoundButton.getTag());
            listAns.put(compoundButton.getText() + "", compoundButton.getTag() + "");
        } else {
            ans = compoundButton.getText() + "";
        }
    }

    abstract void startExam(realm_examQuestion question);

    @Override
    public void onImageCapture(String fileUri) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        sub.setLocalUserImageUri(fileUri);
        mRealm.close();
    }
}
