package org.ole.planet.takeout.survey;


import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import org.ole.planet.takeout.Data.realm_stepExam;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.base.BaseRecyclerFragment;

/**
 * A simple {@link Fragment} subclass.
 */
public class SurveyFragment extends BaseRecyclerFragment<realm_stepExam> {

    public SurveyFragment() {
        // Required empty public constructor
    }

    @Override
    public int getLayout() {
        return R.layout.fragment_survey;
    }

    @Override
    public RecyclerView.Adapter getAdapter() {
        return null;
    }




}
