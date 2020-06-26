package org.ole.planet.myplanet.ui.exam;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmAnswer;
import org.ole.planet.myplanet.model.RealmCourseProgress;
import org.ole.planet.myplanet.model.RealmExamQuestion;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.model.RealmSubmitPhotos;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.CameraUtils;
import org.ole.planet.myplanet.utilities.NetworkUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

import io.noties.markwon.Markwon;
import io.noties.markwon.editor.MarkwonEditor;
import io.noties.markwon.editor.MarkwonEditorTextWatcher;
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
    String Submit_id = "";


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
                if (sub.getParentId().contains("@"))
                    id = sub.getParentId().split("@")[0];
                else
                    id = sub.getParentId();
            }
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

    boolean isLastAnsvalid;
    public void checkAnsAndContinue(boolean cont) {
        if (cont) {
            isLastAnsvalid = true;
            currentIndex = currentIndex + 1;
            continueExam();
        } else {
            isLastAnsvalid = false;
            Utilities.toast(getActivity(), getString(R.string.incorrect_ans));
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
        if (!isMySurvey && !exam.isFromNation()) {
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


    public void insert_into_submitPhotos(String submit_id) {
        mRealm.beginTransaction();
        RealmSubmitPhotos submit = mRealm.createObject(RealmSubmitPhotos.class, UUID.randomUUID().toString());
        submit.setSubmission_id(submit_id);
        submit.setExam_id(exam.getId());
        submit.setCourse_id(exam.getCourseId());
        submit.setMember_id(user.getId());
        submit.setDate(date);
        submit.setMac_address(mac_addr);
        submit.setPhoto_location(photo_path);
        submit.setUploaded(false);
        Utilities.log(submit.getPhoto_location());
        Utilities.log("insert_into_submitPhotos");
        mRealm.commitTransaction();


    }

    @Override
    public void onImageCapture(String fileUri) {

        photo_path = fileUri;
        insert_into_submitPhotos(Submit_id);
        Utilities.log(photo_path);
    }


    public void setMarkdownViewAndShowInput(EditText etAnswer, String type, String oldAnswer) {
        etAnswer.setVisibility(View.VISIBLE);
        final Markwon markwon = Markwon.create(getActivity());
        final MarkwonEditor editor = MarkwonEditor.create(markwon);
        if (type.equalsIgnoreCase("textarea")) {
            etAnswer.addTextChangedListener(MarkwonEditorTextWatcher.withProcess(editor));
        } else {
            etAnswer.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }

                @Override
                public void afterTextChanged(Editable editable) {

                }
            });
        }

        etAnswer.setText(oldAnswer);
    }

}
