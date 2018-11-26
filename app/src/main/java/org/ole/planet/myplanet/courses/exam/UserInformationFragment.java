package org.ole.planet.myplanet.courses.exam;


import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.Data.realm_submissions;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Calendar;
import java.util.Locale;

import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class UserInformationFragment extends DialogFragment implements View.OnClickListener {

    EditText etFname, etMname, etLname, etPhone, etEmail;
    TextView tvBirthDate;
    String dob = "";
    RadioGroup rbGender;
    Spinner spnLang, spnLvl;
    Button btnSubmit, btnCancel;
    String id;
    Realm mRealm;
    realm_submissions submissions;

    public UserInformationFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Holo_Light_Dialog_NoActionBar_MinWidth);
        if (getArguments() != null) {
            id = getArguments().getString("sub_id");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_user_information, container, false);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        submissions = mRealm.where(realm_submissions.class).equalTo("id", id).findFirst();
        initViews(v);
        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    private void initViews(View v) {
        etEmail = v.findViewById(R.id.et_email);
        etFname = v.findViewById(R.id.et_fname);
        etMname = v.findViewById(R.id.et_mname);
        etLname = v.findViewById(R.id.et_lname);
        etPhone = v.findViewById(R.id.et_phone);
        tvBirthDate = v.findViewById(R.id.txt_dob);
        rbGender = v.findViewById(R.id.rb_gender);
        spnLang = v.findViewById(R.id.spn_lang);
        spnLvl = v.findViewById(R.id.spn_level);
        btnCancel = v.findViewById(R.id.btn_cancel);
        btnSubmit = v.findViewById(R.id.btn_submit);
        btnCancel.setOnClickListener(this);
        btnSubmit.setOnClickListener(this);
        tvBirthDate.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.btn_cancel:
                dismiss();
                break;
            case R.id.btn_submit:
                submitForm();
                break;
            case R.id.txt_dob:
                showDatePickerDialog();
                break;
        }
    }

    private void submitForm() {
        String fname = etFname.getText().toString();
        String lname = etLname.getText().toString();
        String mName = etMname.getText().toString();
        String phone = etPhone.getText().toString();
        String email = etEmail.getText().toString();
        String gender = "";
        RadioButton rbSelected = getView().findViewById(rbGender.getCheckedRadioButtonId());
        if (rbSelected != null) {
            gender = rbSelected.getText().toString();
        }
        String level = spnLvl.getSelectedItem().toString();
        String lang = spnLang.getSelectedItem().toString();
        final JsonObject user = new JsonObject();
        user.addProperty("firstName", fname);
        user.addProperty("middleName", mName);
        user.addProperty("lastName", lname);
        user.addProperty("email", email);
        user.addProperty("language", lang);
        user.addProperty("phoneNumber", phone);
        user.addProperty("birthDate", dob);
        user.addProperty("gender", gender);
        user.addProperty("level", level);
        saveUser(user);

    }

    private void saveUser(JsonObject user) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        submissions.setUser(user.toString());
        submissions.setStatus("completed");
        mRealm.commitTransaction();
        dismiss();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        Utilities.toast(getActivity(), "Thank you for taking this survey.");
        getActivity().onBackPressed();
    }


    private void showDatePickerDialog() {
        Calendar now = Calendar.getInstance();
        DatePickerDialog dpd = new DatePickerDialog(getActivity(), new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker datePicker, int i, int i1, int i2) {
                dob = String.format(Locale.US, "%04d-%02d-%02d", i, i1 + 1, i2);
                tvBirthDate.setText(dob);
            }
        }, now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH));
        dpd.show();
    }

}
