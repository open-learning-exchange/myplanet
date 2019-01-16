package org.ole.planet.myplanet.ui.survey;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseRecyclerFragment;
import org.ole.planet.myplanet.model.RealmStepExam;

/**
 * A simple {@link Fragment} subclass.
 */
public class SurveyFragment extends BaseRecyclerFragment<RealmStepExam> {
    FloatingActionButton addNewServey;

    public SurveyFragment() {
    }

    @Override
    public int getLayout() {
        return R.layout.fragment_survey;
    }


    @Override
    public RecyclerView.Adapter getAdapter() {
        return new AdapterSurvey(getActivity(), getList(RealmStepExam.class), mRealm, model.getId());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        addNewServey = getView().findViewById(R.id.fab_add_new_survey);
        addNewServey.setOnClickListener(view -> {
            // TODO: 8/21/18 Create add survey page for administrator
        });
    }
}
