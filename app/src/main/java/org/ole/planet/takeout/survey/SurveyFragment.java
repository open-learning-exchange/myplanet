package org.ole.planet.takeout.survey;


import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.ole.planet.takeout.Data.realm_stepExam;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.base.BaseRecyclerFragment;

/**
 * A simple {@link Fragment} subclass.
 */
public class SurveyFragment extends Fragment implements View.OnClickListener {
    RecyclerView rvSurvey;
    FloatingActionButton fabSurvey;

    public SurveyFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_survey, container, false);
        rvSurvey = v.findViewById(R.id.recycler);
        fabSurvey = v.findViewById(R.id.fab_add_survey);
        fabSurvey.setOnClickListener(this);
        return v;
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.fab_add_survey){

        }
    }
}
