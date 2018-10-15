package org.ole.planet.myplanet.survey;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.ole.planet.myplanet.Data.realm_submissions;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseRecyclerFragment;

/**
 * A simple {@link Fragment} subclass.
 */
public class SurveyHistoryFragment extends BaseRecyclerFragment<realm_submissions> {


    public SurveyHistoryFragment() {
    }

    @Override
    public int getLayout() {
        return R.layout.fragment_survey_history;
    }

    @Override
    public RecyclerView.Adapter getAdapter() {
        return null;
    }

}
