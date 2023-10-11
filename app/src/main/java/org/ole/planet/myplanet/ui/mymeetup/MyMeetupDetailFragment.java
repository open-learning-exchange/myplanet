package org.ole.planet.myplanet.ui.mymeetup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.FragmentMyMeetupDetailBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMeetup;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Constants;

import java.util.ArrayList;
import java.util.HashMap;

import io.realm.Realm;
import io.realm.RealmResults;

public class MyMeetupDetailFragment extends Fragment implements View.OnClickListener {
    private FragmentMyMeetupDetailBinding fragmentMyMeetupDetailBinding;
    RealmMeetup meetups;
    Realm mRealm;
    String meetUpId;

    UserProfileDbHandler profileDbHandler;
    RealmUserModel user;
    ListView listUsers;
    ListView listDesc;
    TextView tvJoined;

    public MyMeetupDetailFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            meetUpId = getArguments().getString("id");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentMyMeetupDetailBinding = FragmentMyMeetupDetailBinding.inflate(inflater, container, false);
        listDesc = fragmentMyMeetupDetailBinding.getRoot().findViewById(R.id.list_desc);
        listUsers = fragmentMyMeetupDetailBinding.getRoot().findViewById(R.id.list_users);
        tvJoined = fragmentMyMeetupDetailBinding.getRoot().findViewById(R.id.tv_joined);
        fragmentMyMeetupDetailBinding.btnInvite.setVisibility(Constants.showBetaFeature(Constants.KEY_MEETUPS, getActivity()) ? View.VISIBLE : View.GONE);
        fragmentMyMeetupDetailBinding.btnLeave.setVisibility(Constants.showBetaFeature(Constants.KEY_MEETUPS, getActivity()) ? View.VISIBLE : View.GONE);
        fragmentMyMeetupDetailBinding.btnLeave.setOnClickListener(this);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        profileDbHandler = new UserProfileDbHandler(getActivity());
        user = mRealm.copyFromRealm(profileDbHandler.getUserModel());
        return fragmentMyMeetupDetailBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        meetups = mRealm.where(RealmMeetup.class).equalTo("meetupId", meetUpId).findFirst();
        setUpData();
        setUserList();
    }

    private void setUserList() {
        String[] ids = RealmMeetup.getJoinedUserIds(mRealm);
        RealmResults<RealmUserModel> users = mRealm.where(RealmUserModel.class).in("id", ids).findAll();
        listUsers.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, users));
        tvJoined.setText(String.format(getString(R.string.joined_members_colon) + " %s", users.size() == 0 ? "(0)\n " + getString(R.string.no_members_has_joined_this_meet_up) : users.size()));
    }

    private void setUpData() {
        fragmentMyMeetupDetailBinding.meetupTitle.setText(meetups.getTitle());
        final HashMap<String, String> map = RealmMeetup.getHashMap(meetups);
        final ArrayList<String> keys = new ArrayList<>(map.keySet());
        listDesc.setAdapter(new ArrayAdapter<String>(getActivity(), R.layout.row_description, keys) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getActivity()).inflate(R.layout.row_description, parent, false);
                }
                ((TextView) convertView.findViewById(R.id.title)).setText(getItem(position) + " : ");
                ((TextView) convertView.findViewById(R.id.description)).setText("" + map.get(getItem(position)));
                return convertView;
            }
        });
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_leave) {
            leaveJoinMeetUp();
        }
    }

    private void leaveJoinMeetUp() {
        mRealm.executeTransaction(realm -> {
            if (meetups.getUserId().isEmpty()) {
                meetups.setUserId(user.getId());
                fragmentMyMeetupDetailBinding.btnLeave.setText(R.string.leave);
            } else {
                meetups.setUserId("");
                fragmentMyMeetupDetailBinding.btnLeave.setText(R.string.join);
            }
        });
    }
}

