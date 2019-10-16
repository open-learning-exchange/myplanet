package org.ole.planet.myplanet.ui.myhealth;


import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyHealth;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class MyHealthFragment extends Fragment {

    RecyclerView rvRecord;
    Button fab, btnNewPatient, btnUpdateRecord;
    TextView lblName;
    String userId;
    Realm mRealm;
    RealmUserModel userModel;
    AlertDialog showDialog;
    TextView txtFullname, txtEmail, txtLanguage,txtDob;
    public UserProfileDbHandler profileDbHandler;

    public MyHealthFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_vital_sign, container, false);
        rvRecord = v.findViewById(R.id.rv_records);
        lblName = v.findViewById(R.id.lblHealthName);
        txtFullname = v.findViewById(R.id.txt_full_name);
        txtEmail = v.findViewById(R.id.txt_email);
        txtLanguage = v.findViewById(R.id.txt_language);
        txtDob = v.findViewById(R.id.txt_dob);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        btnNewPatient = v.findViewById(R.id.btnnew_patient);
        btnNewPatient.setOnClickListener(view -> selectPatient());
        btnNewPatient.setVisibility(Constants.showBetaFeature(Constants.KEY_HEALTHWORKER, getActivity()) ? View.VISIBLE : View.GONE);
        fab = v.findViewById(R.id.add_new_record);
        fab.setOnClickListener(view -> startActivity(new Intent(getActivity(), AddExaminationActivity.class).putExtra("userId", userId)));
        fab.setVisibility(Constants.showBetaFeature(Constants.KEY_HEALTHWORKER, getActivity()) ? View.VISIBLE : View.GONE);
        btnUpdateRecord = v.findViewById(R.id.update_health);
        btnUpdateRecord.setOnClickListener(view -> startActivity(new Intent(getActivity(), AddMyHealthActivity.class).putExtra("userId", userId)));
        btnUpdateRecord.setVisibility(Constants.showBetaFeature(Constants.KEY_HEALTHWORKER, getActivity()) ? View.VISIBLE : View.GONE);
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        View v = getLayoutInflater().inflate(R.layout.alert_users_spinner, null);
        Spinner spnUser = v.findViewById(R.id.spn_user);
        List<RealmUserModel> userList = mRealm.where(RealmUserModel.class).findAll();
        rvRecord.addItemDecoration(new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL));
        profileDbHandler = new UserProfileDbHandler(v.getContext());
        userId = profileDbHandler.getUserModel().getId();
        getHealthRecords(userId);
//        selectPatient();
        //showRecords();
    }

    private void getHealthRecords(String memberId) {
        userId = memberId;
        userModel = mRealm.where(RealmUserModel.class).equalTo("id", userId).findFirst();
        lblName.setText(userModel.getFullName());
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
                    getHealthRecords(userId);
                }).show();
    }

    private void showRecords() {
        RealmMyHealth myHealths = mRealm.where(RealmMyHealth.class).equalTo("userId", userId).findFirst();
        txtFullname.setText(myHealths.getFirstName() + " " + myHealths.getMiddleName() + " " + myHealths.getLastName());
        txtEmail.setText(myHealths.getEmail());
        txtLanguage.setText(myHealths.getLanguage());
        txtDob.setText(myHealths.getBirthDate());
        List<RealmExamination> list = mRealm.where(RealmExamination.class).equalTo("userId", userId).findAll();
        rvRecord.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false));
        rvRecord.setAdapter(new AdapterHealthExamination(getActivity(), list));

    }

}
