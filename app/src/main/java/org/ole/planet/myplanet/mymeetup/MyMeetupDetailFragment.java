package org.ole.planet.myplanet.mymeetup;


import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.ole.planet.myplanet.Data.realm_UserModel;
import org.ole.planet.myplanet.Data.realm_meetups;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.userprofile.UserProfileDbHandler;

import java.util.ArrayList;
import java.util.HashMap;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * A simple {@link Fragment} subclass.
 */
public class MyMeetupDetailFragment extends Fragment implements View.OnClickListener {

    LinearLayout llContent;
    realm_meetups meetups;
    Realm mRealm;
    String meetUpId;
    TextView title;
    Button btnLeave;
    UserProfileDbHandler profileDbHandler;
    realm_UserModel user;
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_my_meetup_detail, container, false);
        // llContent = v.findViewById(R.id.ll_content);
        listDesc = v.findViewById(R.id.list_desc);
        listUsers = v.findViewById(R.id.list_users);
        tvJoined = v.findViewById(R.id.tv_joined);
        btnLeave = v.findViewById(R.id.btn_leave);
        btnLeave.setOnClickListener(this);
        title = v.findViewById(R.id.meetup_title);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        profileDbHandler = new UserProfileDbHandler(getActivity());
        user = mRealm.copyFromRealm(profileDbHandler.getUserModel());
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        meetups = mRealm.where(realm_meetups.class).equalTo("meetupId", meetUpId).findFirst();
        setUpData();
        setUserList();
    }

    private void setUserList() {
        String[] ids = realm_meetups.getJoinedUserIds(mRealm);
        RealmResults<realm_UserModel> users = mRealm.where(realm_UserModel.class).in("id", ids).findAll();
        listUsers.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, users));
        tvJoined.setText(String.format("Joined Members : %s", users.size() == 0 ? "(0)\nNo members has joined this meet up" : users.size()));
    }

    private void setUpData() {
        title.setText(meetups.getTitle());
        final HashMap<String, String> map = realm_meetups.getHashMap(meetups);
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
        mRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                if (meetups.getUserId().isEmpty()) {
                    meetups.setUserId(user.getId());
                    btnLeave.setText("Leave");
                } else {
                    meetups.setUserId("");
                    btnLeave.setText("Join");
                }
            }
        });
    }
}
