package org.ole.planet.takeout.survey;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.ole.planet.takeaway.R;
import org.ole.planet.takeout.R;

/**
 * A simple {@link Fragment} subclass.
 */
public class SurveyHistoryFragment extends Fragment {


    public SurveyHistoryFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_survey_history, container, false);
    }

}
