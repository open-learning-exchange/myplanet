package org.ole.planet.myplanet.ui.exam;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RadioButton;

import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.FragmentTakeExamBinding;
import org.ole.planet.myplanet.model.RealmAnswer;
import org.ole.planet.myplanet.model.RealmCertification;
import org.ole.planet.myplanet.model.RealmExamQuestion;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.CameraUtils;
import org.ole.planet.myplanet.utilities.JsonParserUtils;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.KeyboardUtils;
import org.ole.planet.myplanet.utilities.Markdown;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

import io.realm.RealmList;
import io.realm.RealmQuery;
import io.realm.Sort;

public class TakeExamFragment extends BaseExamFragment implements View.OnClickListener, CompoundButton.OnCheckedChangeListener, CameraUtils.ImageCaptureCallback {
    private FragmentTakeExamBinding fragmentTakeExamBinding;
    boolean isCertified;
    NestedScrollView container;

    public TakeExamFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        fragmentTakeExamBinding = FragmentTakeExamBinding.inflate(inflater, parent, false);
        listAns = new HashMap<>();
        UserProfileDbHandler dbHandler = new UserProfileDbHandler(getActivity());
        user = dbHandler.getUserModel();
        return fragmentTakeExamBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initExam();
        questions = mRealm.where(RealmExamQuestion.class).equalTo("examId", exam.getId()).findAll();
        fragmentTakeExamBinding.tvQuestionCount.setText(getString(R.string.Q1) + questions.size());
        RealmQuery q = mRealm.where(RealmSubmission.class).equalTo("userId", user.getId()).equalTo("parentId", (!TextUtils.isEmpty(exam.getCourseId())) ? id + "@" + exam.getCourseId() : id).sort("startTime", Sort.DESCENDING);
        if (type.equals("exam")) q = q.equalTo("status", "pending");

        sub = (RealmSubmission) q.findFirst();
        String courseId = exam.getCourseId();
        isCertified = RealmCertification.isCourseCertified(mRealm, courseId);
        if (questions.size() > 0) {
            createSubmission();
            Utilities.log("Current index " + currentIndex);
            startExam(questions.get(currentIndex));
        } else {
            container.setVisibility(View.GONE);
            fragmentTakeExamBinding.btnSubmit.setVisibility(View.GONE);
            fragmentTakeExamBinding.tvQuestionCount.setText(R.string.no_questions);
            Snackbar.make(fragmentTakeExamBinding.tvQuestionCount, R.string.no_questions_available, Snackbar.LENGTH_LONG).show();
        }
    }

    private void createSubmission() {
        startTransaction();
        sub = RealmSubmission.createSubmission(sub, mRealm);

        Utilities.log("Set parent id " + id);
        if (TextUtils.isEmpty(id)) {
            sub.setParentId((!TextUtils.isEmpty(exam.getCourseId())) ? exam.getId() + "@" + exam.getCourseId() : exam.getId());
        } else {
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
        fragmentTakeExamBinding.tvQuestionCount.setText(getString(R.string.Q) + (currentIndex + 1) + "/" + questions.size());
        setButtonText();
        fragmentTakeExamBinding.groupChoices.removeAllViews();
        fragmentTakeExamBinding.llCheckbox.removeAllViews();
        fragmentTakeExamBinding.etAnswer.setVisibility(View.GONE);
        fragmentTakeExamBinding.groupChoices.setVisibility(View.GONE);
        fragmentTakeExamBinding.llCheckbox.setVisibility(View.GONE);
        clearAnswer();
        if (sub.getAnswers().size() > currentIndex) {
            ans = sub.getAnswers().get(currentIndex).value;
        }
        if (question.getType().equalsIgnoreCase("select")) {
            fragmentTakeExamBinding.groupChoices.setVisibility(View.VISIBLE);
            fragmentTakeExamBinding.etAnswer.setVisibility(View.GONE);
            selectQuestion(question, ans);
        } else if (question.getType().equalsIgnoreCase("input") || question.getType().equalsIgnoreCase("textarea")) {
            setMarkdownViewAndShowInput(fragmentTakeExamBinding.etAnswer, question.getType(), ans);
        } else if (question.getType().equalsIgnoreCase("selectMultiple")) {
            fragmentTakeExamBinding.llCheckbox.setVisibility(View.VISIBLE);
            fragmentTakeExamBinding.etAnswer.setVisibility(View.GONE);
            showCheckBoxes(question, ans);
        }
        fragmentTakeExamBinding.tvHeader.setText(question.getHeader());
        Markdown.INSTANCE.setMarkdownText(fragmentTakeExamBinding.tvBody, question.getBody());
        fragmentTakeExamBinding.btnSubmit.setOnClickListener(this);
    }

    private void clearAnswer() {
        ans = "";
        fragmentTakeExamBinding.etAnswer.setText("");
        listAns.clear();
    }

    public void setButtonText() {
        if (currentIndex == questions.size() - 1) {
            fragmentTakeExamBinding.btnSubmit.setText(R.string.finish);
        } else {
            fragmentTakeExamBinding.btnSubmit.setText(R.string.submit);
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
        fragmentTakeExamBinding.groupChoices.addView(rdBtn);
    }

    public void addCompoundButton(JsonObject choice, boolean isRadio, String oldAnswer) {
        CompoundButton rdBtn = (CompoundButton) LayoutInflater.from(getActivity()).inflate(isRadio ? R.layout.item_radio_btn : R.layout.item_checkbox, null);
        rdBtn.setText(JsonUtils.getString("text", choice));
        rdBtn.setTag(JsonUtils.getString("id", choice));
        rdBtn.setChecked(JsonUtils.getString("id", choice).equals(oldAnswer));
        rdBtn.setOnCheckedChangeListener(this);
        if (isRadio) fragmentTakeExamBinding.groupChoices.addView(rdBtn);
        else fragmentTakeExamBinding.llCheckbox.addView(rdBtn);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_submit) {
            String type = questions.get(currentIndex).getType();
            showTextInput(type);
            if (showErrorMessage(getString(R.string.please_select_write_your_answer_to_continue))) {
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
            if (isCertified && !isMySurvey) CameraUtils.CapturePhoto(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showTextInput(String type) {
        if (type.equalsIgnoreCase("input") || type.equalsIgnoreCase("textarea")) {
            ans = fragmentTakeExamBinding.etAnswer.getText().toString();
        }
    }

    private boolean updateAnsDb() {
        boolean flag;
        startTransaction();
        sub.setStatus(currentIndex == questions.size() - 1 ? "requires grading" : "pending");
        RealmList<RealmAnswer> list = sub.getAnswers();
        RealmAnswer answer = createAnswer(list);
        RealmExamQuestion que = mRealm.copyFromRealm(questions.get(currentIndex));
        answer.questionId = que.getId();
        answer.value = ans;
        answer.setValueChoices(listAns, isLastAnsvalid);
        answer.submissionId = sub.getId();
        Submit_id = answer.submissionId;

        if (que.getCorrectChoice().size() == 0) {
            answer.grade = 0;
            answer.mistakes = 0;
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
        if (sub.getType().equals("survey") && list.size() > currentIndex) list.remove(currentIndex);
        else if (list.size() > currentIndex && !isLastAnsvalid) {
            list.remove(currentIndex);
        }
    }

    private boolean checkCorrectAns(RealmAnswer answer, RealmExamQuestion que) {
        boolean flag = false;
        answer.isPassed = que.getCorrectChoice().contains(ans.toLowerCase());
        answer.grade = 1;
        int mistake = answer.mistakes;
        String[] selectedAns = listAns.values().toArray(new String[0]);
        String[] correctChoices = que.getCorrectChoice().toArray(new String[0]);
        if (!isEqual(selectedAns, correctChoices)) {
            mistake++;
        } else {
            flag = true;
        }
        answer.mistakes = mistake;
        return flag;
    }

    public boolean isEqual(String[] ar1, String[] ar2) {
        Arrays.sort(ar1);
        Arrays.sort(ar2);
        Utilities.log(Arrays.toString(ar1) + " " + Arrays.toString(ar2));
        return Arrays.equals(ar1, ar2);
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
