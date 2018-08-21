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
import org.ole.planet.takeout.utilities.Utilities;

/**
 * A simple {@link Fragment} subclass.
 */
public class SurveyFragment extends BaseRecyclerFragment<realm_stepExam>  {
    FloatingActionButton addNewServey;
    public SurveyFragment() {
    }

    @Override
    public int getLayout() {
        return R.layout.fragment_survey;
    }


    @Override
    public RecyclerView.Adapter getAdapter() {
        return new AdapterSurvey(getActivity(), getList(realm_stepExam.class));
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        addNewServey = getView().findViewById(R.id.fab_add_new_survey);
        addNewServey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: 8/21/18 Create add survey page for administrator
            }
        });
    }
}
