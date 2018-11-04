package org.ole.planet.myplanet.courses.exam;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.widget.NestedScrollView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.github.kittinunf.fuel.android.core.Json;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.Data.realm_answer;
import org.ole.planet.myplanet.Data.realm_examQuestion;
import org.ole.planet.myplanet.Data.realm_submissions;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.userprofile.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.JsonParserUtils;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

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
        db = new DatabaseService(getActivity());
        mRealm = db.getRealmInstance();
        UserProfileDbHandler dbHandler = new UserProfileDbHandler(getActivity());
        user = mRealm.copyFromRealm(dbHandler.getUserModel());
        return view;
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initExam();
        questions = mRealm.where(realm_examQuestion.class).equalTo("examId", exam.getId()).findAll();
        tvQuestionCount.setText("Question : 1/" + questions.size());
        sub = mRealm.where(realm_submissions.class)
                .equalTo("userId", user.getId())
                .equalTo("parentId", exam.getId())
                .sort("date", Sort.DESCENDING)
                .equalTo("status", "pending")
                .findFirst();
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
        if (sub == null || questions.size() == sub.getAnswers().size())
            sub = mRealm.createObject(realm_submissions.class, UUID.randomUUID().toString());
        sub.setParentId(exam.getId());
        sub.setUserId(user.getId());
        sub.setType(type);
        sub.setDate(new Date().getTime());
        if (sub.getAnswers() != null) {
            currentIndex = sub.getAnswers().size();
        }
        mRealm.commitTransaction();
    }

    @Override
    public void startExam(realm_examQuestion question) {
        tvQuestionCount.setText("Question : " + (currentIndex + 1) + "/" + questions.size());
        if (currentIndex == questions.size() - 1) {
            btnSubmit.setText("Finish");
        }
        listChoices.removeAllViews();
        llCheckbox.removeAllViews();
        etAnswer.setVisibility(View.GONE);
        listChoices.setVisibility(View.GONE);
        llCheckbox.setVisibility(View.GONE);

        if (question.getType().equalsIgnoreCase("select")) {
            listChoices.setVisibility(View.VISIBLE);
            selectQuestion(question);
        } else if (question.getType().equalsIgnoreCase("input")) {
            etAnswer.setVisibility(View.VISIBLE);
        } else if (question.getType().equalsIgnoreCase("selectMultiple")) {
            llCheckbox.setVisibility(View.VISIBLE);
            showCheckBoxes(question);
        }
        etAnswer.setText("");
        ans = "";
        header.setText(question.getHeader());
        body.setText(question.getBody());
        btnSubmit.setOnClickListener(this);
    }


    private void showCheckBoxes(realm_examQuestion question) {
        JsonArray choices = JsonParserUtils.getStringAsJsonArray(question.getChoices());
        for (int i = 0; i < choices.size(); i++) {
            addCheckBoxes(choices.get(i).getAsJsonObject());
        }
    }

    private void selectQuestion(realm_examQuestion question) {
        JsonArray choices = JsonParserUtils.getStringAsJsonArray(question.getChoices());
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).isJsonObject()) {
                addRadioButton(choices.get(i).getAsJsonObject());
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

    public void addRadioButton(JsonObject choice) {
        RadioButton rdBtn = (RadioButton) LayoutInflater.from(getActivity()).inflate(R.layout.item_radio_btn, null);
        rdBtn.setText(JsonUtils.getString("text", choice));
        rdBtn.setTag(JsonUtils.getString("id", choice));
        rdBtn.setOnCheckedChangeListener(this);
        listChoices.addView(rdBtn);
    }

    public void addCheckBoxes(JsonObject choice) {
        CheckBox rdBtn = (CheckBox) LayoutInflater.from(getActivity()).inflate(R.layout.item_checkbox, null);
        rdBtn.setText(JsonUtils.getString("text", choice));
        rdBtn.setTag(JsonUtils.getString("id", choice));
        rdBtn.setOnCheckedChangeListener(this);
        llCheckbox.addView(rdBtn);
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


    private boolean updateAnsDb() {
        boolean flag;
        startTransaction();
        Utilities.log("Current  index " + currentIndex + " " + (questions.size() - 1));
        sub.setStatus(currentIndex == questions.size() - 1 ? "graded" : "pending");
        RealmList<realm_answer> list = sub.getAnswers();
        realm_answer answer = mRealm.copyFromRealm(createAnswer(list));
        realm_examQuestion que = mRealm.copyFromRealm(questions.get(currentIndex));
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

    private boolean checkCorrectAns(realm_answer answer, realm_examQuestion que) {
        boolean flag = false;
        answer.setPassed(que.getCorrectChoice().contains(ans.toLowerCase()));
        answer.setGrade(1);
        int mistake = answer.getMistakes();
        String[] selectedAns = listAns.values().toArray(new String[listAns.keySet().size()]);
        String[] correctChoices = que.getCorrectChoice().toArray(new String[que.getCorrectChoice().size()]);
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
        Utilities.log("Array " + Arrays.toString(ar1) + " " + Arrays.toString(ar2));
        for (int i = 0; i < ar2.length; i++) {
            Utilities.log("Is equal " + new Gson().toJson(ar1[i]) + " " + ar2[i]);
            if (!ar1[i].equalsIgnoreCase(ar2[i]))
                return false;
        }

        return true;
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
            addAnswer(compoundButton);
        } else if (compoundButton.getTag() != null) {
            listAns.remove(compoundButton.getText() + "");
        }
    }

}
