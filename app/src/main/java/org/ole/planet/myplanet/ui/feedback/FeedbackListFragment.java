package org.ole.planet.myplanet.ui.feedback;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.ole.planet.myplanet.databinding.FragmentFeedbackListBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmFeedback;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;

import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;

public class FeedbackListFragment extends Fragment implements FeedbackFragment.OnFeedbackSubmittedListener {
    private FragmentFeedbackListBinding fragmentFeedbackListBinding;
    Realm mRealm;
    RealmUserModel userModel;

    public FeedbackListFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentFeedbackListBinding = FragmentFeedbackListBinding.inflate(inflater, container, false);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        userModel = new UserProfileDbHandler(getActivity()).getUserModel();
        fragmentFeedbackListBinding.fab.setOnClickListener(vi -> {
            FeedbackFragment feedbackFragment = new FeedbackFragment();
            feedbackFragment.setOnFeedbackSubmittedListener(this);
            feedbackFragment.show(getChildFragmentManager(), "");
        });
        return fragmentFeedbackListBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        fragmentFeedbackListBinding.rvFeedback.setLayoutManager(new LinearLayoutManager(getActivity()));
        List<RealmFeedback> list = mRealm.where(RealmFeedback.class).equalTo("owner", userModel.name).findAll();
        if (userModel.isManager()) list = mRealm.where(RealmFeedback.class).findAll();
        AdapterFeedback adapterFeedback = new AdapterFeedback(getActivity(), list);
        fragmentFeedbackListBinding.rvFeedback.setAdapter(adapterFeedback);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (!mRealm.isClosed()) mRealm.close();
    }

    @Override
    public void onFeedbackSubmitted() {
        mRealm.executeTransactionAsync(realm -> {}, () -> {
            RealmResults<RealmFeedback> updatedList = mRealm.where(RealmFeedback.class).equalTo("owner", userModel.name).findAll();
            if (userModel.isManager()) updatedList = mRealm.where(RealmFeedback.class).findAll();
            AdapterFeedback adapterFeedback = new AdapterFeedback(getActivity(), updatedList);
            fragmentFeedbackListBinding.rvFeedback.setAdapter(adapterFeedback);
            adapterFeedback.notifyDataSetChanged();
        });
    }
}