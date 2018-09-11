package org.ole.planet.takeout.courses.exam;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.ole.planet.takeout.R;

/**
 * A simple {@link Fragment} subclass.
 */
public class UserInformationFragment extends Fragment {


    public UserInformationFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_user_information, container, false);
        return v;
    }

}
