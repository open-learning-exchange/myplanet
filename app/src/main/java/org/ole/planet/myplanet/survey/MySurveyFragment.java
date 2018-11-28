package org.ole.planet.myplanet.survey;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.ole.planet.myplanet.Data.realm_stepExam;
import org.ole.planet.myplanet.Data.realm_submissions;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class MySurveyFragment extends Fragment {
    Realm mRealm;
    RecyclerView rvSurvey;

    public MySurveyFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_my_survey, container, false);
        rvSurvey = v.findViewById(R.id.rv_mysurvey);
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        List<realm_submissions> submissions = mRealm.where(realm_submissions.class).equalTo("type", "survey").equalTo("status", "pending").findAll();
        HashMap<String, realm_stepExam> exams = new HashMap<>();
        for (realm_submissions sub : submissions) {
            realm_stepExam survey = mRealm.where(realm_stepExam.class).equalTo("id", sub.getParentId()).findFirst();
            if (survey != null)
                exams.put(sub.getParentId(), survey);
        }
        rvSurvey.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvSurvey.addItemDecoration(new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL));
        rvSurvey.setAdapter(new AdapterMySurvey(getActivity(), submissions, exams));

    }
}
