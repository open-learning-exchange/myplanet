package org.ole.planet.myplanet.ui.exam;


import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmAnswer;
import org.ole.planet.myplanet.model.RealmCertification;
import org.ole.planet.myplanet.model.RealmExamQuestion;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.JsonParserUtils;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.KeyboardUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

import io.noties.markwon.Markwon;
import io.realm.RealmList;
import io.realm.RealmQuery;
import io.realm.Sort;

import org.ole.planet.myplanet.utilities.CameraUtils;

public class TakeExamFragment extends BaseExamFragment implements View.OnClickListener, CompoundButton.OnCheckedChangeListener, CameraUtils.ImageCaptureCallback {

    TextView tvQuestionCount, header, body;
    EditText etAnswer;
    Button btnSubmit;
    RadioGroup listChoices;
    LinearLayout llCheckbox;
    Markwon markwon;
    boolean isCertified;
    NestedScrollView container;

    public TakeExamFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_take_exam, parent, false);
        listAns = new HashMap<>();
        tvQuestionCount = view.findViewById(R.id.tv_question_count);
        header = view.findViewById(R.id.tv_header);
        body = view.findViewById(R.id.tv_body);
        llCheckbox = view.findViewById(R.id.ll_checkbox);
        etAnswer = view.findViewById(R.id.et_answer);
        btnSubmit = view.findViewById(R.id.btn_submit);
        listChoices = view.findViewById(R.id.group_choices);
        container = view.findViewById(R.id.container);
        markwon = Markwon.create(getActivity());
        UserProfileDbHandler dbHandler = new UserProfileDbHandler(getActivity());
        user = dbHandler.getUserModel();
        return view;
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initExam();
        questions = mRealm.where(RealmExamQuestion.class).equalTo("examId", exam.getId()).findAll();
        tvQuestionCount.setText(getString(R.string.Q1) + questions.size());
        RealmQuery q = mRealm.where(RealmSubmission.class)
                .equalTo("userId", user.getId())
                .equalTo("parentId", (!TextUtils.isEmpty(exam.getCourseId())) ? id + "@" + exam.getCourseId() : id)
                .sort("startTime", Sort.DESCENDING);
        if (type.equals("exam"))
            q = q.equalTo("status", "pending");

        sub = (RealmSubmission) q.findFirst();
        String courseId = exam.getCourseId();
        isCertified = RealmCertification.isCourseCertified(mRealm, courseId);
        if (questions.size() > 0) {
            createSubmission();
            Utilities.log("Current index " + currentIndex);
            startExam(questions.get(currentIndex));
        } else {
            container.setVisibility(View.GONE);
            btnSubmit.setVisibility(View.GONE);
            tvQuestionCount.setText(R.string.no_questions);
            Snackbar.make(tvQuestionCount, "No questions available", Snackbar.LENGTH_LONG).show();
        }
    }


    private void createSubmission() {
        startTransaction();
//        if (sub != null && sub.getStatus().equals("complete")) {
//            sub.setAnswers(new RealmList<>());
//        }
        sub = RealmSubmission.createSubmission(sub, mRealm);

        Utilities.log("Set parent id " + id);
        if (TextUtils.isEmpty(id)){
            sub.setParentId((!TextUtils.isEmpty(exam.getCourseId())) ? exam.getId() + "@" + exam.getCourseId() : exam.getId());
        }else{
            sub.setParentId((!TextUtils.isEmpty(exam.getCourseId())) ? id + "@" + exam.getCourseId() : id);
        }
        sub.setUserId(user.getId());
        sub.setStatus("pending");
        sub.setType(type);
        sub.setStartTime(new Date().getTime());
        if (sub.getAnswers() != null) {
            currentIndex = sub.getAnswers().size();
        }
        if (sub.getAnswers().size() == questions.size() && sub.getType().equals("survey")) {
            currentIndex = 0;
        }
        mRealm.commitTransaction();
    }

    @Override
    public void startExam(RealmExamQuestion question) {
        tvQuestionCount.setText(getString(R.string.Q) + (currentIndex + 1) + "/" + questions.size());
        setButtonText();
        listChoices.removeAllViews();
        llCheckbox.removeAllViews();
        etAnswer.setVisibility(View.GONE);
        listChoices.setVisibility(View.GONE);
        llCheckbox.setVisibility(View.GONE);
        clearAnswer();
        if (sub.getAnswers().size() > currentIndex) {
            ans = sub.getAnswers().get(currentIndex).getValue();
        }
        if (question.getType().equalsIgnoreCase("select")) {
            listChoices.setVisibility(View.VISIBLE);
            etAnswer.setVisibility(View.GONE);
            selectQuestion(question, ans);
        } else if (question.getType().equalsIgnoreCase("input") || question.getType().equalsIgnoreCase("textarea")) {
            setMarkdownViewAndShowInput(etAnswer, question.getType(), ans);
        } else if (question.getType().equalsIgnoreCase("selectMultiple")) {
            llCheckbox.setVisibility(View.VISIBLE);
            etAnswer.setVisibility(View.GONE);
            showCheckBoxes(question, ans);
        }
        header.setText(question.getHeader());
        // body.setText(question.getBody());
        markwon.setMarkdown(body, question.getBody());
        btnSubmit.setOnClickListener(this);
    }

    private void clearAnswer() {
        ans = "";
        etAnswer.setText("");
        listAns.clear();
    }

    public void setButtonText() {
        if (currentIndex == questions.size() - 1) {
            btnSubmit.setText(R.string.finish);
        } else {
            btnSubmit.setText(R.string.submit);
        }
    }

    private void showCheckBoxes(RealmExamQuestion question, String oldAnswer) {
        JsonArray choices = JsonParserUtils.getStringAsJsonArray(question.getChoices());
        for (int i = 0; i < choices.size(); i++) {
            addCompoundButton(choices.get(i).getAsJsonObject(), false, oldAnswer);
        }
    }

    private void selectQuestion(RealmExamQuestion question, String oldAnswer) {
        JsonArray choices = JsonParserUtils.getStringAsJsonArray(question.getChoices());
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).isJsonObject()) {
                addCompoundButton(choices.get(i).getAsJsonObject(), true, oldAnswer);
            } else {
                addRadioButton(JsonUtils.getString(choices, i), oldAnswer);
            }
        }
    }


    public void addRadioButton(String choice, String oldAnswer) {
        RadioButton rdBtn = (RadioButton) LayoutInflater.from(getActivity()).inflate(R.layout.item_radio_btn, null);
        rdBtn.setText(choice);
        rdBtn.setChecked(choice.equals(oldAnswer));
        rdBtn.setOnCheckedChangeListener(this);
        listChoices.addView(rdBtn);
    }

    public void addCompoundButton(JsonObject choice, boolean isRadio, String oldAnswer) {
        CompoundButton rdBtn = (CompoundButton) LayoutInflater.from(getActivity()).inflate(isRadio ? R.layout.item_radio_btn : R.layout.item_checkbox, null);
        rdBtn.setText(JsonUtils.getString("text", choice));
        rdBtn.setTag(JsonUtils.getString("id", choice));
        rdBtn.setChecked(JsonUtils.getString("id", choice).equals(oldAnswer));
        rdBtn.setOnCheckedChangeListener(this);
        if (isRadio)
            listChoices.addView(rdBtn);
        else
            llCheckbox.addView(rdBtn);
    }


    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_submit) {
            String type = questions.get(currentIndex).getType();
            showTextInput(type);
            if (showErrorMessage("Please select / write your answer to continue")) {
                return;
            }
            boolean cont = updateAnsDb();
            capturePhoto();
            KeyboardUtils.hideSoftKeyboard(getActivity());
            checkAnsAndContinue(cont);
        }
    }

    private void capturePhoto() {
        try {
            if (isCertified && !isMySurvey)
                CameraUtils.CapturePhoto(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showTextInput(String type) {
        if (type.equalsIgnoreCase("input") || type.equalsIgnoreCase("textarea")) {
            ans = etAnswer.getText().toString();
        }
    }


    private boolean updateAnsDb() {
        boolean flag;
        startTransaction();
        sub.setStatus(currentIndex == questions.size() - 1 ? "graded" : "pending");
        RealmList<RealmAnswer> list = sub.getAnswers();
        RealmAnswer answer = createAnswer(list);
        RealmExamQuestion que = mRealm.copyFromRealm(questions.get(currentIndex));
        answer.setQuestionId(que.getId());
        answer.setValue(ans);
        answer.setValueChoices(listAns, isLastAnsvalid);
        answer.setSubmissionId(sub.getId());
        Submit_id = answer.getSubmissionId();

        if (que.getCorrectChoice().size() == 0) {
            answer.setGrade(0);
            answer.setMistakes(0);
            flag = true;
        } else {
            flag = checkCorrectAns(answer, que);
        }
        removeOldAnswer(list);
        list.add(currentIndex, answer);
        sub.setAnswers(list);
        mRealm.commitTransaction();
        return flag;
    }

    private void removeOldAnswer(RealmList<RealmAnswer> list) {
        if (sub.getType().equals("survey") && list.size() > currentIndex)
            list.remove(currentIndex);
        else if (list.size() > currentIndex && !isLastAnsvalid) {
            list.remove(currentIndex);
        }
    }

    private boolean checkCorrectAns(RealmAnswer answer, RealmExamQuestion que) {
        boolean flag = false;
        answer.setPassed(que.getCorrectChoice().contains(ans.toLowerCase()));
        answer.setGrade(1);
        int mistake = answer.getMistakes();
        String[] selectedAns = listAns.values().toArray(new String[0]);
        String[] correctChoices = que.getCorrectChoice().toArray(new String[0]);
        if (!isEqual(selectedAns, correctChoices)) {
            mistake++;
        } else {
            flag = true;
        }
        answer.setMistakes(mistake);
        return flag;
    }


    public boolean isEqual(String[] ar1, String[] ar2) {
        Arrays.sort(ar1);
        Arrays.sort(ar2);
        Utilities.log(Arrays.toString(ar1) + " " + Arrays.toString(ar2));
        return Arrays.equals(ar1, ar2);
//        for (int i = 0; i < ar2.length; i++) {
//            if (!ar1[i].equalsIgnoreCase(ar2[i]))
//                return false;
//        }

        //   return true;
    }

    private void startTransaction() {
        if (!mRealm.isInTransaction()) {
            mRealm.beginTransaction();
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        mRealm.close();
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if (b) {
            addAnswer(compoundButton);
        } else if (compoundButton.getTag() != null) {
            listAns.remove(compoundButton.getText() + "");
        }
    }

}
