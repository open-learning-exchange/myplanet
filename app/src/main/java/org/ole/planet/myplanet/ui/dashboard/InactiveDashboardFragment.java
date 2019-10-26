package org.ole.planet.myplanet.ui.dashboard;


import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment;

/**
 * A simple {@link Fragment} subclass.
 */
public class InactiveDashboardFragment extends Fragment {


    public InactiveDashboardFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_in_active_dashboard, container, false);

        v.findViewById(R.id.btn_feedback).setOnClickListener(vi -> {
            new FeedbackFragment().show(getChildFragmentManager(), "");
        });
        return v;
    }

}
