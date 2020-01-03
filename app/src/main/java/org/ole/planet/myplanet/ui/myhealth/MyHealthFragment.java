package org.ole.planet.myplanet.ui.myhealth;


import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.borax12.materialdaterangepicker.date.AccessibleDateAnimator;
import com.google.gson.Gson;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyHealth;
import org.ole.planet.myplanet.model.RealmMyHealthPojo;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.AndroidDecrypter;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * A simple {@link Fragment} subclass.
 */
public class MyHealthFragment extends Fragment {

    public UserProfileDbHandler profileDbHandler;
    RecyclerView rvRecord;
    Button fab, btnNewPatient, btnUpdateRecord;
    TextView lblName;
    String userId;
    Realm mRealm;
    RealmUserModel userModel;
    AlertDialog dialog;
    TextView txtFullname, txtEmail, txtLanguage, txtDob, txtBirthPlace, txtEmergency, txtSpecial, txtOther, txtMessage;
    LinearLayout llUserDetail;

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
        txtBirthPlace = v.findViewById(R.id.txt_birth_place);
        txtEmergency = v.findViewById(R.id.txt_emergency_contact);
        txtSpecial = v.findViewById(R.id.txt_special_needs);
        txtOther = v.findViewById(R.id.txt_other_need);
        llUserDetail = v.findViewById(R.id.layout_user_detail);
        txtMessage = v.findViewById(R.id.tv_message);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        fab = v.findViewById(R.id.add_new_record);
        fab.setOnClickListener(view -> {
//            RealmMyHealth myHealth = n
            startActivity(new Intent(getActivity(), AddExaminationActivity.class).putExtra("userId", userId));
        });
//        fab.setVisibility(Constants.showBetaFeature(Constants.KEY_HEALTHWORKER, getActivity()) ? View.VISIBLE : View.GONE);
        btnUpdateRecord = v.findViewById(R.id.update_health);
        btnUpdateRecord.setOnClickListener(view -> startActivity(new Intent(getActivity(), AddMyHealthActivity.class).putExtra("userId", userId)));
//        btnUpdateRecord.setVisibility(Constants.showBetaFeature(Constants.KEY_HEALTHWORKER, getActivity()) ? View.VISIBLE : View.GONE);
        btnNewPatient = v.findViewById(R.id.btnnew_patient);
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        View v = getLayoutInflater().inflate(R.layout.alert_users_spinner, null);
        rvRecord.addItemDecoration(new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL));
        profileDbHandler = new UserProfileDbHandler(v.getContext());
        userId = profileDbHandler.getUserModel().getId();
        getHealthRecords(userId);
//        selectPatient();
        btnNewPatient.setOnClickListener(view -> selectPatient());
        btnNewPatient.setVisibility(Constants.showBetaFeature(Constants.KEY_HEALTHWORKER, getActivity()) ? View.VISIBLE : View.GONE);
    }

    private void getHealthRecords(String memberId) {
        userId = memberId;
        userModel = mRealm.where(RealmUserModel.class).equalTo("id", userId).findFirst();
        lblName.setText(userModel.getFullName());
        showRecords();
    }

    private void selectPatient() {
        RealmResults<RealmUserModel> userModelList = mRealm.where(RealmUserModel.class).findAll();
        List<String> memberFullNameList = new ArrayList<>();
        HashMap<String, String> map = new HashMap<>();
        for (RealmUserModel um : userModelList) {
            memberFullNameList.add(um.getFullName());
            map.put(um.getFullName(), um.getId());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, memberFullNameList);
        View alertHealth = LayoutInflater.from(getActivity()).inflate(R.layout.alert_health_list, null);
        EditText etSearch = alertHealth.findViewById(R.id.et_search);
        setTextWatcher(etSearch, adapter);
        ListView lv = alertHealth.findViewById(R.id.list);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((adapterView, view, i, l) -> {
            userId = map.get(((TextView)view).getText().toString());
            getHealthRecords(userId);
            dialog.dismiss();
        });
        dialog = new AlertDialog.Builder(getActivity()).setTitle(getString(R.string.select_health_member))
                .setView(alertHealth)
                .setCancelable(false).setNegativeButton("Dismiss",null).create();


        dialog.show();

    }

    private void setTextWatcher(EditText etSearch, ArrayAdapter<String> adapter) {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                adapter.getFilter().filter(charSequence);
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        showRecords();
    }

    private void showRecords() {
        RealmMyHealthPojo mh = mRealm.where(RealmMyHealthPojo.class).equalTo("_id", userId).findFirst();
        if (mh != null) {
            llUserDetail.setVisibility(View.VISIBLE);
            txtMessage.setVisibility(View.GONE);
            RealmMyHealth mm = getHealthProfile(mh);
            if(mm == null){
                Utilities.toast(getActivity(), "Health Record not available.");
                return;
            }
            RealmMyHealth.RealmMyHealthProfile myHealths = mm.getProfile();
            txtFullname.setText(myHealths.getFirstName() + " " + myHealths.getMiddleName() + " " + myHealths.getLastName());
            txtEmail.setText(TextUtils.isEmpty(myHealths.getEmail()) ? "N/A" : myHealths.getEmail());
            txtLanguage.setText(TextUtils.isEmpty(myHealths.getLanguage()) ? "N/A" : myHealths.getLanguage());
            txtDob.setText(TextUtils.isEmpty(myHealths.getBirthDate()) ? "N/A" : myHealths.getBirthDate());
            txtOther.setText(TextUtils.isEmpty(myHealths.getNotes()) ? "N/A" : myHealths.getNotes());
            txtSpecial.setText(TextUtils.isEmpty(myHealths.getSpecialNeeds()) ? "N/A" : myHealths.getSpecialNeeds());
            txtBirthPlace.setText(TextUtils.isEmpty(myHealths.getBirthplace()) ? "N/A" : myHealths.getBirthplace());
            txtEmergency.setText("Name : " + myHealths.getEmergencyContactName() + "\nType : " + myHealths.getEmergencyContactName() + "\nContact : " + myHealths.getEmergencyContact());
            List<RealmExamination> list = mm.getEvents();
            rvRecord.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false));
            rvRecord.setNestedScrollingEnabled(false);
            AdapterHealthExamination adapter = new AdapterHealthExamination(getActivity(), list,mh, userModel );
            adapter.setmRealm(mRealm);
            rvRecord.setAdapter(adapter);
            List<RealmExamination> finalList = list;
            rvRecord.post(() -> rvRecord.scrollToPosition(finalList.size() - 1));
        } else {
            llUserDetail.setVisibility(View.GONE);
            txtMessage.setText(R.string.no_records);
            txtMessage.setVisibility(View.VISIBLE);
        }


    }

    private RealmMyHealth getHealthProfile(RealmMyHealthPojo mh) {
        Utilities.log("User profile " + userModel.getName());
        String json = AndroidDecrypter.decrypt(mh.getData(), userModel.getKey(), userModel.getIv());
        if(json == null){
            if(!userModel.getRealm().isInTransaction()){
                userModel.getRealm().beginTransaction();
            }
            userModel.setIv("");
            userModel.setKey("");
            userModel.getRealm().commitTransaction();
            return  null;
        }else{
            return new Gson().fromJson(json, RealmMyHealth.class);
        }
    }

}
