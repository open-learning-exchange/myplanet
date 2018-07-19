package org.ole.planet.takeout.userprofile;


import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.ole.planet.takeout.Data.realm_UserModel;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.datamanager.RealmService;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;

import io.realm.Realm;

public class UserProfileFragment extends Fragment {

    UserProfileDbHandler handler;
    RealmService realmService;
    Realm mRealm;

    public UserProfileFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_user_profile, container, false);
        handler = new UserProfileDbHandler(getActivity());
        realmService = new RealmService(getActivity());
        mRealm = realmService.getInstance();
        populateUserData(v);
        return v;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void populateUserData(View v) {
        realm_UserModel model = mRealm.copyToRealmOrUpdate(handler.getUserModel());
        ((TextView) v.findViewById(R.id.txt_name)).setText(String.format("%s %s %s", model.getFirstName(), model.getMiddleName(), model.getLastName()));
        ((TextView) v.findViewById(R.id.txt_email)).setText(Utilities.checkNA(model.getEmail()));
        ((TextView) v.findViewById(R.id.txt_dob)).setText(Utilities.checkNA(model.getDob()));

        final LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
        map.put("Community Name", Utilities.checkNA(model.getCommunityName()));
        map.put("Last Login : ", Utilities.getRelativeTime(handler.getLastVisit()));
        map.put("Total Visits : ", handler.getOfflineVisits() + "");
        map.put("Maximum opened Resource : ", Utilities.checkNA(handler.getMaxOpenedResource()));
        map.put("Number of Resource open : ", Utilities.checkNA(handler.getNumberOfResourceOpen()));

        setUpRecyclerView(map, v);
    }

    public void setUpRecyclerView(final HashMap<String, String> map, View v) {
        RecyclerView rvStat = v.findViewById(R.id.rv_stat);
        final LinkedList<String> keys = new LinkedList<>(map.keySet());
        rvStat.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvStat.setNestedScrollingEnabled(false);
        rvStat.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = getLayoutInflater().inflate(R.layout.row_stat, parent, false);
                return new ViewHolderStat(v);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (holder instanceof ViewHolderStat) {
                    ((ViewHolderStat) holder).key.setText(keys.get(position));
                    ((ViewHolderStat) holder).value.setText(map.get(keys.get(position)));
                    if (position %2 ==0){
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


    class ViewHolderStat extends RecyclerView.ViewHolder {
        TextView key, value;

        public ViewHolderStat(View itemView) {
            super(itemView);
            key = itemView.findViewById(R.id.key);
            value = itemView.findViewById(R.id.value);
        }
    }
}
