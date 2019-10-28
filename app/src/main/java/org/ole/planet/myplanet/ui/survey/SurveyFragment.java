package org.ole.planet.myplanet.ui.survey;


import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseRecyclerFragment;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.utilities.Utilities;

import io.realm.Sort;

/**
 * A simple {@link Fragment} subclass.
 */
public class SurveyFragment extends BaseRecyclerFragment<RealmStepExam> {
    FloatingActionButton addNewServey;
    Spinner spn;
    public SurveyFragment() {
    }

    @Override
    public int getLayout() {
        return R.layout.fragment_survey;
    }


    @Override
    public RecyclerView.Adapter getAdapter() {
        return new AdapterSurvey(getActivity(), getList(RealmStepExam.class, "name", Sort.ASCENDING), mRealm, model.getId());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        spn = getView().findViewById(R.id.spn_sort);
        addNewServey = getView().findViewById(R.id.fab_add_new_survey);
        addNewServey.setOnClickListener(view -> {
            // TODO: 8/21/18 Create add survey page for administrator
        });
        if (getAdapter() != null)
            showNoData(tvMessage, getAdapter().getItemCount());

        spn.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                Utilities.log("i " + i);
                if (i == 0){
                    recyclerView.setAdapter(new AdapterSurvey(getActivity(), getList(RealmStepExam.class, "name", Sort.ASCENDING), mRealm, model.getId()));
                }else{
                    recyclerView.setAdapter(new AdapterSurvey(getActivity(), getList(RealmStepExam.class, "name", Sort.DESCENDING), mRealm, model.getId()));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

    }
}
