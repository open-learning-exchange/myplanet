package org.ole.planet.myplanet.ui.feedback;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmFeedback;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.w3c.dom.Text;

import java.util.List;

import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class FeedbackListFragment extends Fragment {

    TextView etMessage;
    RecyclerView rvFeedbacks;
    Realm mRealm;
    RealmUserModel userModel;

    public FeedbackListFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_feedback_list, container, false);
        etMessage = v.findViewById(R.id.et_message);
        rvFeedbacks = v.findViewById(R.id.rv_feedback);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        userModel = new UserProfileDbHandler(getActivity()).getUserModel();
        v.findViewById(R.id.fab).setOnClickListener(vi -> new FeedbackFragment().show(getChildFragmentManager(), ""));
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        rvFeedbacks.setLayoutManager(new LinearLayoutManager(getActivity()));
        List<RealmFeedback> list = mRealm.where(RealmFeedback.class).equalTo("owner", userModel.getName()).findAll();
        AdapterFeedback adapterFeedback = new AdapterFeedback(getActivity(), list);
        rvFeedbacks.setAdapter(adapterFeedback);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (!mRealm.isClosed())
            mRealm.close();
    }
}
