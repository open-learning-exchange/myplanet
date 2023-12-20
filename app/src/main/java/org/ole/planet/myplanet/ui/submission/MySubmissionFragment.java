package org.ole.planet.myplanet.ui.submission;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.ole.planet.myplanet.databinding.FragmentMySubmissionBinding;
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

public class MySubmissionFragment extends Fragment implements CompoundButton.OnCheckedChangeListener {
    private FragmentMySubmissionBinding fragmentMySubmissionBinding;
    Realm mRealm;
    String type = "";
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
        if (getArguments() != null) type = getArguments().getString("type");
    }

    public MySubmissionFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentMySubmissionBinding = FragmentMySubmissionBinding.inflate(inflater, container, false);
        exams = new HashMap<>();
        user = new UserProfileDbHandler(getActivity()).getUserModel();
        return fragmentMySubmissionBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        fragmentMySubmissionBinding.rvMysurvey.setLayoutManager(new LinearLayoutManager(getActivity()));
        fragmentMySubmissionBinding.rvMysurvey.addItemDecoration(new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL));
        submissions = mRealm.where(RealmSubmission.class).findAll();
        exams = RealmSubmission.getExamMap(mRealm, submissions);
        setData("");
        fragmentMySubmissionBinding.etSearch.addTextChangedListener(new TextWatcher() {
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
            fragmentMySubmissionBinding.rbExam.setChecked(true);
            fragmentMySubmissionBinding.rbExam.setOnCheckedChangeListener(this);
            fragmentMySubmissionBinding.rbSurvey.setOnCheckedChangeListener(this);
        } else {
            fragmentMySubmissionBinding.rbSurvey.setVisibility(View.GONE);
            fragmentMySubmissionBinding.rbExam.setVisibility(View.GONE);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if (fragmentMySubmissionBinding.rbSurvey.isChecked()) {
            type = "survey_submission";
        } else {
            type = "exam";
        }
        setData("");
    }

    private void setData(String s) {
        RealmQuery q = null;
        if (type.equals("survey")) {
            q = mRealm.where(RealmSubmission.class).equalTo("userId", user.id).equalTo("type", "survey");
        } else if (type.equals("survey_submission")) {
            q = mRealm.where(RealmSubmission.class).equalTo("userId", user.id).notEqualTo("status", "pending").equalTo("type", "survey");
        } else {
            q = mRealm.where(RealmSubmission.class).equalTo("userId", user.id).notEqualTo("type", "survey");
        }

        if (!TextUtils.isEmpty(s)) {
            List<RealmStepExam> ex = mRealm.where(RealmStepExam.class).contains("name", s, Case.INSENSITIVE).findAll();
            q.in("parentId", RealmStepExam.getIds(ex));
        }
        submissions = q.findAll();
        AdapterMySubmission adapter = new AdapterMySubmission(getActivity(), submissions, exams);
        adapter.setmRealm(mRealm);
        adapter.setType(type);
        fragmentMySubmissionBinding.rvMysurvey.setAdapter(adapter);
    }
}
