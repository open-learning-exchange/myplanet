package org.ole.planet.myplanet.ui.myhealth;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.text.TextUtils;

import android.view.MenuItem;
import android.widget.Spinner;

import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.ActivityAddExaminationBinding;
import org.ole.planet.myplanet.databinding.ActivityAddMyHealthBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyHealth;
import org.ole.planet.myplanet.model.RealmMyHealthPojo;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.AndroidDecrypter;
import org.ole.planet.myplanet.utilities.Utilities;


import io.realm.Realm;

public class AddMyHealthActivity extends AppCompatActivity {
    private ActivityAddMyHealthBinding activityAddMyHealthBinding;
    Realm realm;
    RealmMyHealthPojo healthPojo;
    RealmUserModel userModelB;
    String userId;
    String key, iv;
    RealmMyHealth myHealth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityAddMyHealthBinding = ActivityAddMyHealthBinding.inflate(getLayoutInflater());
        setContentView(activityAddMyHealthBinding.getRoot());
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        realm = new DatabaseService(this).getRealmInstance();
        userId = getIntent().getStringExtra("userId");
        healthPojo = realm.where(RealmMyHealthPojo.class).equalTo("_id", userId).findFirst();
        if (healthPojo == null) {
            healthPojo = realm.where(RealmMyHealthPojo.class).equalTo("userId", userId).findFirst();
        }
        userModelB = realm.where(RealmUserModel.class).equalTo("id", userId).findFirst();
        key = userModelB.getKey();
        iv = userModelB.getIv();
        findViewById(R.id.btn_submit).setOnClickListener(view -> {
            createMyHealth();
            Utilities.toast(AddMyHealthActivity.this, getString(R.string.my_health_saved_successfully));
        });
        initViews();
    }

    private void createMyHealth() {
        if (!realm.isInTransaction()) realm.beginTransaction();
        RealmMyHealth.RealmMyHealthProfile health = new RealmMyHealth.RealmMyHealthProfile();
        userModelB.setFirstName(activityAddMyHealthBinding.etFname.getEditText().getText().toString().trim());
        userModelB.setMiddleName(activityAddMyHealthBinding.etMname.getEditText().getText().toString().trim());
        userModelB.setLastName(activityAddMyHealthBinding.etLname.getEditText().getText().toString().trim());
        userModelB.setEmail(activityAddMyHealthBinding.etEmail.getEditText().getText().toString().trim());
        userModelB.setDob(activityAddMyHealthBinding.etBirthdate.getEditText().getText().toString().trim());
        userModelB.setBirthPlace(activityAddMyHealthBinding.etBirthplace.getEditText().getText().toString().trim());
        userModelB.setPhoneNumber(activityAddMyHealthBinding.etPhone.getEditText().getText().toString().trim());
        health.emergencyContactName = activityAddMyHealthBinding.etEmergency.getEditText().getText().toString().trim();
        health.emergencyContact = activityAddMyHealthBinding.etContact.getEditText().getText().toString().trim();
        health.emergencyContactType = activityAddMyHealthBinding.spnContactType.getSelectedItem().toString();
        health.specialNeeds = activityAddMyHealthBinding.etSpecialNeed.getEditText().getText().toString().trim();
        health.notes = activityAddMyHealthBinding.etOtherNeed.getEditText().getText().toString().trim();
        if (myHealth == null) {
            myHealth = new RealmMyHealth();
        }
        if (TextUtils.isEmpty(myHealth.userKey)) {
            myHealth.userKey = AndroidDecrypter.generateKey();
        }
        myHealth.profile = health;
        if (healthPojo == null) {
            healthPojo = realm.createObject(RealmMyHealthPojo.class, userId);
        }
        healthPojo.setIsUpdated(true);
        healthPojo.setUserId(userModelB.get_id());
        try {
            healthPojo.setData(AndroidDecrypter.encrypt(new Gson().toJson(myHealth), key, iv));
        } catch (Exception e) {
        }
        realm.commitTransaction();
        finish();
    }

    private void initViews() {
        populate();
    }

    public void populate() {
        if (healthPojo != null && !TextUtils.isEmpty(healthPojo.getData())) {
            myHealth = new Gson().fromJson(AndroidDecrypter.decrypt(healthPojo.getData(), userModelB.getKey(), userModelB.getIv()), RealmMyHealth.class);
            RealmMyHealth.RealmMyHealthProfile health = myHealth.profile;
            activityAddMyHealthBinding.etEmergency.getEditText().setText(health.emergencyContactName);
            activityAddMyHealthBinding.etSpecialNeed.getEditText().setText(health.specialNeeds);
            activityAddMyHealthBinding.etOtherNeed.getEditText().setText(health.notes);
        }
        if (userModelB != null) {
            activityAddMyHealthBinding.etFname.getEditText().setText(userModelB.getFirstName());
            activityAddMyHealthBinding.etMname.getEditText().setText(userModelB.getMiddleName());
            activityAddMyHealthBinding.etLname.getEditText().setText(userModelB.getLastName());
            activityAddMyHealthBinding.etEmail.getEditText().setText(userModelB.getEmail());
            activityAddMyHealthBinding.etPhone.getEditText().setText(userModelB.getPhoneNumber());
            activityAddMyHealthBinding.etBirthdate.getEditText().setText(userModelB.getDob());
            activityAddMyHealthBinding.etBirthplace.getEditText().setText(userModelB.getBirthPlace());
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) finish();
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (realm != null) realm.close();
    }
}
