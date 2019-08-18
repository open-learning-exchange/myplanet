package org.ole.planet.myplanet.ui.team;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.ole.planet.myplanet.R;

/**
 * A simple {@link Fragment} subclass.
 */
public class JoinedMemberFragment extends Fragment {


    public JoinedMemberFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_joined_member, container, false);
    }

}
