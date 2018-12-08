package org.ole.planet.myplanet.userprofile;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;

import org.ole.planet.myplanet.Data.realm_UserModel;
import org.ole.planet.myplanet.Data.realm_stepExam;
import org.ole.planet.myplanet.Data.realm_submissions;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import io.realm.Case;
import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class MySubmissionFragment extends Fragment implements CompoundButton.OnCheckedChangeListener {
    Realm mRealm;
    RecyclerView rvSurvey;
    String type = "";
    RadioButton rbExam, rbSurvey;
    EditText etSearch;
    HashMap<String, realm_stepExam> exams;
    List<realm_submissions> submissions;
    realm_UserModel user;

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
        submissions = mRealm.where(realm_submissions.class).findAll();
        createHashMap(submissions);
        setData();
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                String cleanString = charSequence.toString();
                if (!cleanString.isEmpty())
                    search(cleanString);
                else
                    setData();
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

    private void createHashMap(List<realm_submissions> submissions) {
        for (realm_submissions sub : submissions) {
            String id = sub.getParentId();
            if (sub.getParentId().contains("@")) {
                id = sub.getParentId().split("@")[0];
            }
            realm_stepExam survey = mRealm.where(realm_stepExam.class).equalTo("id", id).findFirst();
            if (survey != null)
                exams.put(sub.getParentId(), survey);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if (rbSurvey.isChecked()) {
            type = "survey_submission";
        } else {
            type = "exam";
        }
        setData();
    }

    private void search(String s) {
        List<realm_stepExam> ex = mRealm.where(realm_stepExam.class).contains("name", s, Case.INSENSITIVE).findAll();
        if (type.equals("survey")) {
            submissions = mRealm.where(realm_submissions.class).equalTo("userId", user.getId()).equalTo("status", "pending").equalTo("type", "survey").in("parentId", realm_stepExam.getIds(ex)).findAll();
        } else if (type.equals("survey_submission")) {
            submissions = mRealm.where(realm_submissions.class).equalTo("userId", user.getId()).equalTo("type", "survey").in("parentId", realm_stepExam.getIds(ex)).findAll();
        } else {
            submissions = mRealm.where(realm_submissions.class).equalTo("userId", user.getId()).notEqualTo("type", "survey").in("parentId", realm_stepExam.getIds(ex)).findAll();
        }
        AdapterMySubmission adapter = new AdapterMySubmission(getActivity(), submissions, exams);
        adapter.setType(type);
        rvSurvey.setAdapter(adapter);
    }

    private void setData() {
        if (type.equals("survey")) {
            submissions = mRealm.where(realm_submissions.class).equalTo("userId", user.getId()).equalTo("status", "pending").equalTo("type", "survey").findAll();
        } else if (type.equals("survey_submission")) {
            submissions = mRealm.where(realm_submissions.class).equalTo("userId", user.getId()).equalTo("type", "survey").findAll();
        } else {
            submissions = mRealm.where(realm_submissions.class).equalTo("userId", user.getId()).notEqualTo("type", "survey").findAll();
        }
        AdapterMySubmission adapter = new AdapterMySubmission(getActivity(), submissions, exams);
        adapter.setType(type);
        rvSurvey.setAdapter(adapter);
    }
}
