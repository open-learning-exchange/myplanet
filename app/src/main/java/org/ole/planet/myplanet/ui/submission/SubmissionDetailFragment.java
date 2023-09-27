package org.ole.planet.myplanet.ui.submission;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import org.ole.planet.myplanet.databinding.FragmentSubmissionDetailBinding;

public class SubmissionDetailFragment extends Fragment {
    private FragmentSubmissionDetailBinding fragmentSubmissionDetailBinding;
    public SubmissionDetailFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentSubmissionDetailBinding = FragmentSubmissionDetailBinding.inflate(inflater, container, false);
        return fragmentSubmissionDetailBinding.getRoot();
    }
}
