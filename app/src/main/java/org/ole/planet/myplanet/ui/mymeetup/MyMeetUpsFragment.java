package org.ole.planet.myplanet.ui.mymeetup;


import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.ole.planet.myplanet.R;

/**
 * A simple {@link Fragment} subclass.
 */
public class MyMeetUpsFragment extends Fragment {


    public MyMeetUpsFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_my_meet_ups, container, false);
    }

}
