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
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

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
    Button fab,btnNewPatient;
    TextView lblName;
    String userId;
    Realm mRealm;
    Spinner spnUsers;
    RealmUserModel userModel;
    AlertDialog showDialog;
    public MyHealthFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_vital_sign, container, false);
        rvRecord = v.findViewById(R.id.rv_records);
        fab = v.findViewById(R.id.add_new_record);
        btnNewPatient = v.findViewById(R.id.btnnew_patient);
        lblName = v.findViewById(R.id.lblHealthName);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        fab.setOnClickListener(view -> startActivity(new Intent(getActivity(), AddVitalSignActivity.class).putExtra("userId", userId)));
        v.findViewById(R.id.update_health).setOnClickListener(view -> startActivity(new Intent(getActivity(), AddMyHealthActivity.class).putExtra("userId", userId)));
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        View v = getLayoutInflater().inflate(R.layout.alert_users_spinner, null);
        Spinner spnUser = v.findViewById(R.id.spn_user);
        List<RealmUserModel> userList = mRealm.where(RealmUserModel.class).findAll();
        btnNewPatient.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view){
                selectPatient();
            }
        });
        selectPatient();
        showRecords();
    }

    private void selectPatient() {
        View v = getLayoutInflater().inflate(R.layout.alert_users_spinner, null);
        Spinner spnUser = v.findViewById(R.id.spn_user);
        List<RealmUserModel> userList = mRealm.where(RealmUserModel.class).findAll();
        ArrayAdapter<RealmUserModel> adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, userList);
        spnUser.setAdapter(adapter);
        showDialog = new AlertDialog.Builder(getActivity()).setTitle("Select Patient")
                .setView(v).setCancelable(false).setPositiveButton("OK", (dialogInterface, i) -> {
                    userId = ((RealmUserModel) spnUser.getSelectedItem()).getId();
                    userModel = mRealm.where(RealmUserModel.class).equalTo("id", userId).findFirst();
                    lblName.setText(userModel.getFullName());
                    showRecords();
                }).show();
    }

    private void showRecords() {
        List<RealmVitalSign> list = mRealm.where(RealmVitalSign.class).equalTo("userId", userId).findAll();
        rvRecord.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvRecord.setAdapter(new AdapterVitalSign(getActivity(), list));
    }

}
