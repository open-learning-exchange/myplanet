package org.ole.planet.myplanet.ui.userprofile;


import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;

import io.realm.Realm;

public class UserProfileFragment extends Fragment {

    UserProfileDbHandler handler;
    DatabaseService realmService;
    Realm mRealm;
    RecyclerView rvStat;

    public UserProfileFragment() {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mRealm != null)
            mRealm.close();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_user_profile, container, false);
        handler = new UserProfileDbHandler(getActivity());
        realmService = new DatabaseService(getActivity());
        mRealm = realmService.getRealmInstance();
        rvStat = v.findViewById(R.id.rv_stat);
        rvStat.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvStat.setNestedScrollingEnabled(false);
        populateUserData(v);
        return v;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void populateUserData(View v) {
        RealmUserModel model = mRealm.copyToRealmOrUpdate(handler.getUserModel());
        ((TextView) v.findViewById(R.id.txt_name)).setText(String.format("%s %s %s", model.getFirstName(), model.getMiddleName(), model.getLastName()));
        ((TextView) v.findViewById(R.id.txt_email)).setText(Utilities.checkNA(model.getEmail()));
        ((TextView) v.findViewById(R.id.txt_dob)).setText(Utilities.checkNA(model.getDob()));
        Utilities.loadImage(model.getUserImage(), (ImageView) v.findViewById(R.id.image));
        final LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
        map.put("Community Name", Utilities.checkNA(model.getCommunityName()));
        map.put("Last Login : ", Utilities.getRelativeTime(handler.getLastVisit()));
        map.put("Total Visits : ", handler.getOfflineVisits() + "");
        map.put("Maximum opened Resource : ", Utilities.checkNA(handler.getMaxOpenedResource()));
        map.put("Number of Resource open : ", Utilities.checkNA(handler.getNumberOfResourceOpen()));
        setUpRecyclerView(map, v);
    }

    public void setUpRecyclerView(final HashMap<String, String> map, View v) {
        final LinkedList<String> keys = new LinkedList<>(map.keySet());
        rvStat.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = getLayoutInflater().inflate(R.layout.row_stat, parent, false);
                return new AdapterOtherInfo.ViewHolderOtherInfo(v);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (holder instanceof AdapterOtherInfo.ViewHolderOtherInfo) {
                    ((AdapterOtherInfo.ViewHolderOtherInfo) holder).tvTitle.setText(keys.get(position));
                    ((AdapterOtherInfo.ViewHolderOtherInfo) holder).tvDescription.setText(map.get(keys.get(position)));
                    if (position % 2 == 0) {
                        holder.itemView.setBackgroundColor(getResources().getColor(R.color.bg_white));
                        holder.itemView.setBackgroundColor(getResources().getColor(R.color.md_grey_300));
                    }
                }
            }

            @Override
            public int getItemCount() {
                return keys.size();
            }
        });
    }

}
