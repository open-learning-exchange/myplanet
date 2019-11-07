package org.ole.planet.myplanet.ui.submission;


import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;

import java.util.HashMap;
import java.util.List;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmQuery;

/**
 * A simple {@link Fragment} subclass.
 */
public class MySubmissionFragment extends Fragment implements CompoundButton.OnCheckedChangeListener {
    Realm mRealm;
    RecyclerView rvSurvey;
    String type = "";
    RadioButton rbExam, rbSurvey;
    EditText etSearch;
    HashMap<String, RealmStepExam> exams;
    List<RealmSubmission> submissions;
    RealmUserModel user;

    public static Fragment newInstance(String type) {
        MySubmissionFragment fragment = new MySubmissionFragment();
        Bundle b = new Bundle();
        b.putString("type", type);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null)
            type = getArguments().getString("type");
    }

    public MySubmissionFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_my_submission, container, false);
        exams = new HashMap<>();
        rbExam = v.findViewById(R.id.rb_exam);
        rbSurvey = v.findViewById(R.id.rb_survey);
        rvSurvey = v.findViewById(R.id.rv_mysurvey);
        etSearch = v.findViewById(R.id.et_search);
        user = new UserProfileDbHandler(getActivity()).getUserModel();
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        rvSurvey.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvSurvey.addItemDecoration(new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL));
        submissions = mRealm.where(RealmSubmission.class).findAll();
        exams = RealmSubmission.getExamMap(mRealm, submissions);
        setData("");
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                String cleanString = charSequence.toString();
                setData(cleanString);
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
        showHideRadioButton();
    }

    private void showHideRadioButton() {
        if (!type.equals("survey")) {
            rbExam.setChecked(true);
            rbExam.setOnCheckedChangeListener(this);
            rbSurvey.setOnCheckedChangeListener(this);
        } else {
            rbSurvey.setVisibility(View.GONE);
            rbExam.setVisibility(View.GONE);
        }
    }


    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if (rbSurvey.isChecked()) {
            type = "survey_submission";
        } else {
            type = "exam";
        }
        setData("");
    }


    private void setData(String s) {
        RealmQuery q = null;
        if (type.equals("survey")) {
            q = mRealm.where(RealmSubmission.class).equalTo("userId", user.getId()).equalTo("type", "survey");
        } else if (type.equals("survey_submission")) {
            q = mRealm.where(RealmSubmission.class).equalTo("userId", user.getId()).notEqualTo("status", "pending").equalTo("type", "survey");
        } else {
            q = mRealm.where(RealmSubmission.class).equalTo("userId", user.getId()).notEqualTo("type", "survey");
        }

        if (!TextUtils.isEmpty(s)) {
            List<RealmStepExam> ex = mRealm.where(RealmStepExam.class).contains("name", s, Case.INSENSITIVE).findAll();
            q.in("parentId", RealmStepExam.getIds(ex));
        }
        submissions = q.findAll();
        AdapterMySubmission adapter = new AdapterMySubmission(getActivity(), submissions, exams);
        adapter.setmRealm(mRealm);
        adapter.setType(type);
        rvSurvey.setAdapter(adapter);
    }
}
