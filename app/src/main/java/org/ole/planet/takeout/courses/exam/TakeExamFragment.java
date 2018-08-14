package org.ole.planet.takeout.courses.exam;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import org.ole.planet.takeout.Data.realm_answerChoices;
import org.ole.planet.takeout.Data.realm_examQuestion;
import org.ole.planet.takeout.Data.realm_stepExam;
import org.ole.planet.takeout.Data.realm_submissions;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.datamanager.DatabaseService;
import org.ole.planet.takeout.utilities.Utilities;

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
        startExam(questions.get(currentIndex));
    }

    private void startExam(realm_examQuestion question) {
        if (question.getType().equalsIgnoreCase("select")) {
            etAnswer.setVisibility(View.GONE);
            listChoices.setVisibility(View.VISIBLE);
            listChoices.removeAllViews();
            if (question.getChoices().size() > 0) {
                RealmList choices = question.getChoices();
                for (int i = 0; i < choices.size(); i++) {
                    RadioButton rdBtn = (RadioButton) LayoutInflater.from(getActivity()).inflate(R.layout.item_radio_btn, null);
                    rdBtn.setText(choices.get(i).toString());
                    rdBtn.setOnCheckedChangeListener(this);
                    listChoices.addView(rdBtn);
                }
            } else {
                RealmResults<realm_answerChoices> choices = mRealm.where(realm_answerChoices.class).equalTo("questionId", question.getId()).findAll();
                for (int i = 0; i < choices.size(); i++) {
                    RadioButton rdBtn = (RadioButton) LayoutInflater.from(getActivity()).inflate(R.layout.item_radio_btn, null);
                    rdBtn.setText(choices.get(i).getText());
                    rdBtn.setOnCheckedChangeListener(this);
                    listChoices.addView(rdBtn);
                }
            }
        } else if (question.getType().equalsIgnoreCase("input")) {
            etAnswer.setVisibility(View.VISIBLE);
            listChoices.setVisibility(View.GONE);
        }
        header.setText(question.getHeader());
        body.setText(question.getBody());
        btnSubmit.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_submit) {
            String type = questions.get(currentIndex).getType();
            if (type.equalsIgnoreCase("input")) {
                ans = etAnswer.getText().toString();
                if (ans.isEmpty()) {
                    etAnswer.setError("Please write your answer to continue");
                    return;
                }
            } else {
                if (ans.isEmpty()) {
                    Utilities.toast(getActivity(), "Please select answer");
                    return;
                }
            }

            updateAnsDb(ans);

            currentIndex++;
            if (currentIndex < questions.size()) {
                startExam(questions.get(currentIndex));
            } else {
                getActivity().onBackPressed();
            }
        }
    }

    private void updateAnsDb(String ans) {
        if (!mRealm.isInTransaction()) {
            mRealm.beginTransaction();
        }
//        realm_submissions submissions = mRealm.createObject(realm_submissions.class);

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

    String ans = "";
}
