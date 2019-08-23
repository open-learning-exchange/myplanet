package org.ole.planet.myplanet.ui.myhealth;


import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class MyHealthFragment extends Fragment {

    RecyclerView rvRecord;
    Button fab;
    String userId;
    Realm mRealm;
    Spinner spnUsers;

    public MyHealthFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_vital_sign, container, false);
        rvRecord = v.findViewById(R.id.rv_records);
        fab = v.findViewById(R.id.add_new_record);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        fab.setOnClickListener(view -> startActivity(new Intent(getActivity(), AddVitalSignActivity.class).putExtra("userId", userId)));
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        View v = getLayoutInflater().inflate(R.layout.alert_users_spinner, null);
        Spinner spnUser = v.findViewById(R.id.spn_user);
        List<RealmUserModel> userList = mRealm.where(RealmUserModel.class).findAll();
        Utilities.log("User " + userList.size());
        ArrayAdapter<RealmUserModel> adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, userList);
        spnUser.setAdapter(adapter);
        List<RealmVitalSign> list = mRealm.where(RealmVitalSign.class).equalTo("userId", userId).findAll();
        new AlertDialog.Builder(getActivity()).setTitle("Select Patient")
                .setView(R.layout.alert_users_spinner).setCancelable(false).setPositiveButton("OK", (dialogInterface, i) -> userId = ((RealmUserModel) spnUser.getSelectedItem()).getId()).show();
        rvRecord.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvRecord.setAdapter(new AdapterVitalSign(getActivity(), list));
    }

}
