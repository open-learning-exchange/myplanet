package org.ole.planet.myplanet.ui.exam;

import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.RadioButton;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseDialogFragment;
import org.ole.planet.myplanet.databinding.FragmentUserInformationBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;

import io.realm.Realm;

public class UserInformationFragment extends BaseDialogFragment implements View.OnClickListener {
    private FragmentUserInformationBinding fragmentUserInformationBinding;
    String dob = "";
    Realm mRealm;
    RealmSubmission submissions;
    RealmUserModel userModel;

    public UserInformationFragment() {}

    public static UserInformationFragment getInstance(String id) {
        UserInformationFragment f = new UserInformationFragment();
        setArgs(f, id);
        return f;
    }

    private static void setArgs(UserInformationFragment f, String id) {
        Bundle b = new Bundle();
        b.putString("sub_id", id);
        f.setArguments(b);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentUserInformationBinding = FragmentUserInformationBinding.inflate(inflater, container, false);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        userModel = new UserProfileDbHandler(requireContext()).getUserModel();
        if (!TextUtils.isEmpty(id))
            submissions = mRealm.where(RealmSubmission.class).equalTo("id", id).findFirst();
        initViews();
        return fragmentUserInformationBinding.getRoot();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    private void initViews() {
        fragmentUserInformationBinding.etEmail.setText(userModel.getEmail() + "");
        fragmentUserInformationBinding.etFname.setText(userModel.getFirstName() + "");
        fragmentUserInformationBinding.etLname.setText(userModel.getLastName() + "");
        fragmentUserInformationBinding.etPhone.setText(userModel.getPhoneNumber() + "");
        fragmentUserInformationBinding.txtDob.setText(userModel.getDob() + "");
        dob = userModel.getDob();
        fragmentUserInformationBinding.btnCancel.setOnClickListener(this);
        fragmentUserInformationBinding.btnSubmit.setOnClickListener(this);
        fragmentUserInformationBinding.txtDob.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_cancel:
                if (isAdded()) {
                    Objects.requireNonNull(getDialog()).dismiss();
                }
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
        String fname = fragmentUserInformationBinding.etFname.getText().toString().trim();
        String lname = fragmentUserInformationBinding.etLname.getText().toString().trim();
        String mName = fragmentUserInformationBinding.etMname.getText().toString().trim();
        String phone = fragmentUserInformationBinding.etPhone.getText().toString().trim();
        String email = fragmentUserInformationBinding.etEmail.getText().toString().trim();
        String gender = "";
        RadioButton rbSelected = getView().findViewById(fragmentUserInformationBinding.rbGender.getCheckedRadioButtonId());
        if (rbSelected != null) {
            gender = rbSelected.getText().toString();
        }
        String level = fragmentUserInformationBinding.spnLevel.getSelectedItem().toString();
        String lang = fragmentUserInformationBinding.spnLang.getSelectedItem().toString();
        if (TextUtils.isEmpty(id)) {
            String userId = userModel.getId();
            String finalGender = gender;
            mRealm.executeTransactionAsync(realm -> {
                RealmUserModel model = realm.where(RealmUserModel.class).equalTo("id", userId).findFirst();
                if (model != null) {
                    if (!TextUtils.isEmpty(fname)) model.setFirstName(fname);
                    if (!TextUtils.isEmpty(lname)) model.setLastName(lname);
                    if (!TextUtils.isEmpty(email)) model.setEmail(email);
                    if (!TextUtils.isEmpty(lang)) model.setLanguage(lang);
                    if (!TextUtils.isEmpty(phone)) model.setPhoneNumber(phone);
                    if (!TextUtils.isEmpty(dob)) model.setBirthPlace(dob);
                    if (!TextUtils.isEmpty(level)) model.setLevel(level);
                    if (!TextUtils.isEmpty(finalGender)) model.setGender(finalGender);
                    model.setUpdated(true);
                }
            }, () -> {
                Utilities.toast(MainApplication.context, getString(R.string.user_profile_updated));
                if (isAdded()) {
                    Objects.requireNonNull(getDialog()).dismiss();
                }
            }, error -> {
                Utilities.toast(MainApplication.context, getString(R.string.unable_to_update_user));
                if (isAdded()) {
                    Objects.requireNonNull(getDialog()).dismiss();
                }
            });
        } else {
            final JsonObject user = new JsonObject();
            user.addProperty("name", fname + " " + lname);
            user.addProperty("firstName", fname);
            user.addProperty("middleName", mName);
            user.addProperty("lastName", lname);
            user.addProperty("email", email);
            user.addProperty("language", lang);
            user.addProperty("phoneNumber", phone);
            user.addProperty("birthDate", dob);
            user.addProperty("gender", gender);
            user.addProperty("level", level);
            saveSubmission(user);
        }
    }

    private void saveSubmission(JsonObject user) {
        if (!mRealm.isInTransaction()) mRealm.beginTransaction();
        submissions.setUser(user.toString());
        submissions.setStatus("complete");
        mRealm.commitTransaction();
        if (isAdded()) {
            Objects.requireNonNull(getDialog()).dismiss();
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        Utilities.toast(getActivity(), getString(R.string.thank_you_for_taking_this_survey));
        getActivity().onBackPressed();
    }

    private void showDatePickerDialog() {
        Calendar now = Calendar.getInstance();
        DatePickerDialog dpd = new DatePickerDialog(getActivity(), new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker datePicker, int i, int i1, int i2) {
                dob = String.format(Locale.US, "%04d-%02d-%02d", i, i1 + 1, i2);
                fragmentUserInformationBinding.txtDob.setText(dob);
            }
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        dpd.show();
    }

    @Override
    protected String getKey() {
        return "sub_id";
    }
}
