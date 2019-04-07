package org.ole.planet.myplanet.ui.exam;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.widget.NestedScrollView;
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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmAnswer;
import org.ole.planet.myplanet.model.RealmExamQuestion;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.JsonParserUtils;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

import io.realm.RealmList;
import io.realm.Sort;

/**
 * A simple {@link Fragment} subclass.
 */
public class TakeExamFragment extends BaseExamFragment implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    TextView tvQuestionCount, header, body;
    EditText etAnswer;
    Button btnSubmit;
    RadioGroup listChoices;
    LinearLayout llCheckbox;

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

        UserProfileDbHandler dbHandler = new UserProfileDbHandler(getActivity());
        user = mRealm.copyFromRealm(dbHandler.getUserModel());
        return view;
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initExam();
        questions = mRealm.where(RealmExamQuestion.class).equalTo("examId", exam.getId()).findAll();
        tvQuestionCount.setText("Question : 1/" + questions.size());
        if (!isMySurvey)
            sub = mRealm.where(RealmSubmission.class)
                    .equalTo("userId", user.getId())
                    .equalTo("parentId", exam.getId())
                    .sort("startTime", Sort.DESCENDING)
                    .equalTo("status", "pending")
                    .findFirst();
//        Utilities.log("Ans size "  + sub.getAnswers().size());
        if (questions.size() > 0) {
            createSubmission();
            Utilities.log("Current index " + currentIndex);
            startExam(questions.get(currentIndex));
        } else {
            container.setVisibility(View.GONE);
            Snackbar.make(tvQuestionCount, "No questions available", Snackbar.LENGTH_LONG).show();
        }
    }


    private void createSubmission() {
        startTransaction();
        sub = RealmSubmission.createSubmission(sub, questions, mRealm);
        sub.setParentId(exam.getId());
        sub.setUserId(user.getId());
        sub.setStatus("pending");
        sub.setType(type);
        sub.setStartTime(new Date().getTime());
        if (sub.getAnswers() != null) {
            currentIndex = sub.getAnswers().size();
        }
        mRealm.commitTransaction();
    }

    @Override
    public void startExam(RealmExamQuestion question) {
        tvQuestionCount.setText("Question : " + (currentIndex + 1) + "/" + questions.size());
        setButtonText();

        listChoices.removeAllViews();
        llCheckbox.removeAllViews();
        etAnswer.setVisibility(View.GONE);
        listChoices.setVisibility(View.GONE);
        llCheckbox.setVisibility(View.GONE);

        if (question.getType().equalsIgnoreCase("select")) {
            listChoices.setVisibility(View.VISIBLE);
            selectQuestion(question);
        } else if (question.getType().equalsIgnoreCase("input") || question.getType().equalsIgnoreCase("textarea") ) {
            etAnswer.setVisibility(View.VISIBLE);
        } else if (question.getType().equalsIgnoreCase("selectMultiple")) {
            llCheckbox.setVisibility(View.VISIBLE);
            showCheckBoxes(question);
        }
        etAnswer.setText("");
        ans = "";
        listAns.clear();
        header.setText(question.getHeader());
        body.setText(question.getBody());
        btnSubmit.setOnClickListener(this);
    }

    public void setButtonText() {
        if (currentIndex == questions.size() - 1) {
            btnSubmit.setText("Finish");
        }
        else {
            btnSubmit.setText("Submit");
        }
    }

    private void showCheckBoxes(RealmExamQuestion question) {
        JsonArray choices = JsonParserUtils.getStringAsJsonArray(question.getChoices());
        for (int i = 0; i < choices.size(); i++) {
            addCompoundButton(choices.get(i).getAsJsonObject(), false);
        }
    }

    private void selectQuestion(RealmExamQuestion question) {
        JsonArray choices = JsonParserUtils.getStringAsJsonArray(question.getChoices());
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).isJsonObject()) {
                addCompoundButton(choices.get(i).getAsJsonObject(), true);
            } else {
                addRadioButton(JsonUtils.getString(choices, i));
            }
        }
    }


    public void addRadioButton(String choice) {
        RadioButton rdBtn = (RadioButton) LayoutInflater.from(getActivity()).inflate(R.layout.item_radio_btn, null);
        rdBtn.setText(choice);
        rdBtn.setOnCheckedChangeListener(this);
        listChoices.addView(rdBtn);
    }

    public void addCompoundButton(JsonObject choice, boolean isRadio) {
        CompoundButton rdBtn = (CompoundButton) LayoutInflater.from(getActivity()).inflate(isRadio ? R.layout.item_radio_btn : R.layout.item_checkbox, null);
        rdBtn.setText(JsonUtils.getString("text", choice));
        rdBtn.setTag(JsonUtils.getString("id", choice));
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
            checkAnsAndContinue(cont);
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
        RealmAnswer answer = mRealm.copyFromRealm(createAnswer(list));
        RealmExamQuestion que = mRealm.copyFromRealm(questions.get(currentIndex));
        answer.setQuestionId(que.getId());
        answer.setValue(ans);
        answer.setValueChoices(listAns);
        answer.setSubmissionId(sub.getId());
        if (que.getCorrectChoice().size() == 0) {
            answer.setGrade(0);
            answer.setMistakes(0);
            flag = true;
        } else {
            flag = checkCorrectAns(answer, que);
        }
        list.add(currentIndex, answer);
        sub.setAnswers(list);
        mRealm.commitTransaction();
        return flag;
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
        Utilities.log("b = " + b);
        if (b) {
            addAnswer(compoundButton);
        } else if (compoundButton.getTag() != null) {
            listAns.remove(compoundButton.getText() + "");
        }
    }

}
