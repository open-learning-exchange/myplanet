package org.ole.planet.takeout.courses.exam;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import org.ole.planet.takeout.Data.realm_UserModel;
import org.ole.planet.takeout.Data.realm_answerChoices;
import org.ole.planet.takeout.Data.realm_examQuestion;
import org.ole.planet.takeout.Data.realm_answer;
import org.ole.planet.takeout.Data.realm_stepExam;
import org.ole.planet.takeout.Data.realm_submissions;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.datamanager.DatabaseService;
import org.ole.planet.takeout.userprofile.UserProfileDbHandler;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmResults;


/**
 * A simple {@link Fragment} subclass.
 */
public class TakeExamFragment extends Fragment implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    TextView tvQuestionCount, header, body;
    EditText etAnswer;
    Button btnSubmit;
    RadioGroup listChoices;
    realm_stepExam exam;
    DatabaseService db;
    Realm mRealm;
    String stepId;
    int currentIndex = 0;
    RealmResults<realm_examQuestion> questions;
    String ans = "";
    realm_UserModel user;
    realm_submissions sub;

    public TakeExamFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            stepId = getArguments().getString("stepId");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_take_exam, container, false);
        initializeView(v);
        db = new DatabaseService(getActivity());
        mRealm = db.getRealmInstance();
        UserProfileDbHandler dbHandler = new UserProfileDbHandler(getActivity());
        user = mRealm.copyFromRealm(dbHandler.getUserModel());
        return v;
    }

    private void initializeView(View v) {
        tvQuestionCount = v.findViewById(R.id.tv_question_count);
        header = v.findViewById(R.id.tv_header);
        body = v.findViewById(R.id.tv_body);
        etAnswer = v.findViewById(R.id.et_answer);
        btnSubmit = v.findViewById(R.id.btn_submit);
        listChoices = v.findViewById(R.id.group_choices);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        exam = mRealm.where(realm_stepExam.class).equalTo("stepId", stepId).findFirst();
        questions = mRealm.where(realm_examQuestion.class).equalTo("examId", exam.getId()).findAll();
        sub = mRealm.where(realm_submissions.class)
                .equalTo("userId", user.getId())
                .equalTo("parentId", exam.getId()).findFirst();

        createSubmission();
        startExam(questions.get(currentIndex));
    }

    private void createSubmission() {
        startTransaction();
        if (sub == null)
            sub = mRealm.createObject(realm_submissions.class, UUID.randomUUID().toString());
        sub.setParentId(exam.getId());
        sub.setUserId(user.getId());
        sub.setType("exam");
        mRealm.commitTransaction();
    }

    private void startExam(realm_examQuestion question) {
        if (question.getType().equalsIgnoreCase("select")) {
            etAnswer.setVisibility(View.GONE);
            listChoices.setVisibility(View.VISIBLE);
            listChoices.removeAllViews();
            multipleChoiceQuestion(question);
        } else if (question.getType().equalsIgnoreCase("input")) {
            etAnswer.setVisibility(View.VISIBLE);
            listChoices.setVisibility(View.GONE);
        }
        header.setText(question.getHeader());
        body.setText(question.getBody());
        btnSubmit.setOnClickListener(this);
    }

    private void multipleChoiceQuestion(realm_examQuestion question) {
        if (question.getChoices().size() > 0) {
            RealmList choices = question.getChoices();
            for (int i = 0; i < choices.size(); i++) {
                addRadioButton(choices.get(i).toString());
            }
        } else {
            RealmResults<realm_answerChoices> choices = mRealm.where(realm_answerChoices.class)
                    .equalTo("questionId", question.getId()).findAll();
            for (int i = 0; i < choices.size(); i++) {
                addRadioButton(choices.get(i).getText());
            }
        }
    }

    public void addRadioButton(String choice) {
        RadioButton rdBtn = (RadioButton) LayoutInflater.from(getActivity()).inflate(R.layout.item_radio_btn, null);
        rdBtn.setText(choice);
        rdBtn.setOnCheckedChangeListener(this);
        listChoices.addView(rdBtn);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_submit) {
            String type = questions.get(currentIndex).getType();
            if (type.equalsIgnoreCase("input")) {
                ans = etAnswer.getText().toString();
            }
            if (showErrorMessage("Please select / write your answer to continue")) {
                return;
            }

            boolean cont = updateAnsDb();
            checkAnsAndContinue(cont);
        }
    }

    private void checkAnsAndContinue(boolean cont) {
        if (cont) {
            currentIndex++;
            if (currentIndex < questions.size()) {
                startExam(questions.get(currentIndex));
            } else {
                getActivity().onBackPressed();
            }
        } else {
            Utilities.toast(getActivity(), "Invalid answer");
        }
    }

    private boolean showErrorMessage(String s) {
        if (ans.isEmpty()) {
            Utilities.toast(getActivity(), "Please select answer");
            return true;
        }
        return false;

    }

    private boolean updateAnsDb() {
        boolean flag = false;
        startTransaction();
        sub.setStatus(currentIndex == questions.size() - 1 ? "graded" : "pending");
        RealmList<realm_answer> list = sub.getAnswers();
        realm_answer answer = mRealm.copyFromRealm(createAnswer(list));
        realm_examQuestion que = mRealm.copyFromRealm(questions.get(currentIndex));
        answer.setQuestionId(que.getId());
        answer.setValue(ans);
        if (TextUtils.isEmpty(que.getCorrectChoice())) {
            answer.setGrade(0);
            answer.setMistakes(0);
            flag = true;
        } else {
            answer.setPassed(que.getCorrectChoice().equalsIgnoreCase(ans));
            answer.setGrade(1);
            int mistake = answer.getMistakes();
            if (que.getCorrectChoice().equalsIgnoreCase(ans)) {
                mistake++;
            } else {
                flag = true;
            }
            answer.setMistakes(mistake);
        }
        list.add(currentIndex, answer);
        sub.setAnswers(list);
        mRealm.commitTransaction();
        return flag;
    }

    private void startTransaction() {
        if (!mRealm.isInTransaction()) {
            mRealm.beginTransaction();
        }
    }

    private realm_answer createAnswer(RealmList<realm_answer> list) {
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        mRealm.close();
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if (b) {
            ans = compoundButton.getText().toString();
        }
    }
}
