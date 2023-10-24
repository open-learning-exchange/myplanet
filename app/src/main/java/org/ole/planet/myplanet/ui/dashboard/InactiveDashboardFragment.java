package org.ole.planet.myplanet.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.ole.planet.myplanet.databinding.FragmentInActiveDashboardBinding;
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment;

public class InactiveDashboardFragment extends Fragment {
    private FragmentInActiveDashboardBinding fragmentInActiveDashboardBinding;
    public InactiveDashboardFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentInActiveDashboardBinding = FragmentInActiveDashboardBinding.inflate(inflater, container, false);
        fragmentInActiveDashboardBinding.btnFeedback.setOnClickListener(vi -> {
            new FeedbackFragment().show(getChildFragmentManager(), "");
        });
        return fragmentInActiveDashboardBinding.getRoot();
    }
}
