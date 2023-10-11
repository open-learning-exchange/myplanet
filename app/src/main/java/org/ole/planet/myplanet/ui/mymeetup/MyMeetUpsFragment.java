package org.ole.planet.myplanet.ui.mymeetup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.ole.planet.myplanet.databinding.FragmentMyMeetUpsBinding;

public class MyMeetUpsFragment extends Fragment {
    private FragmentMyMeetUpsBinding fragmentMyMeetUpsBinding;
    public MyMeetUpsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentMyMeetUpsBinding = FragmentMyMeetUpsBinding.inflate(inflater, container, false);
        return fragmentMyMeetUpsBinding.getRoot();
    }
}
