package org.ole.planet.myplanet.ui.myhealth;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.text.TextUtils;

import android.view.MenuItem;
import android.widget.Spinner;

import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyHealth;
import org.ole.planet.myplanet.model.RealmMyHealthPojo;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.AndroidDecrypter;
import org.ole.planet.myplanet.utilities.Utilities;


import io.realm.Realm;

public class AddMyHealthActivity extends AppCompatActivity {
    Realm realm;
    TextInputLayout fname, mname, lname, email, phone, birthplace, birthdate, emergencyNumber, contact, specialNeed, otherNeed;
    Spinner contactType;
    RealmMyHealthPojo healthPojo;
    RealmUserModel userModelB;
    String userId;
    String key, iv;
    RealmMyHealth myHealth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_my_health);
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
            Utilities.toast(AddMyHealthActivity.this, "My health saved successfully");
        });
        initViews();
    }

    private void createMyHealth() {
        if (!realm.isInTransaction())
            realm.beginTransaction();
        RealmMyHealth.RealmMyHealthProfile health = new RealmMyHealth.RealmMyHealthProfile();
        userModelB.setFirstName(fname.getEditText().getText().toString().trim());
        userModelB.setMiddleName(mname.getEditText().getText().toString().trim());
        userModelB.setLastName(lname.getEditText().getText().toString().trim());
        userModelB.setEmail(email.getEditText().getText().toString().trim());
        userModelB.setDob(birthdate.getEditText().getText().toString().trim());
        userModelB.setBirthPlace(birthplace.getEditText().getText().toString().trim());
        userModelB.setPhoneNumber(phone.getEditText().getText().toString().trim());
        health.setEmergencyContactName(emergencyNumber.getEditText().getText().toString().trim());
        health.setEmergencyContact(contact.getEditText().getText().toString().trim());
        health.setEmergencyContactType(contactType.getSelectedItem().toString());
        health.setSpecialNeeds(specialNeed.getEditText().getText().toString().trim());
        health.setNotes(otherNeed.getEditText().getText().toString().trim());
        if (myHealth == null) {
            myHealth = new RealmMyHealth();
        }
        if (TextUtils.isEmpty(myHealth.getUserKey())) {
            myHealth.setUserKey(AndroidDecrypter.generateKey());
        }
        myHealth.setProfile(health);
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
        fname = findViewById(R.id.et_fname);
        mname = findViewById(R.id.et_mname);
        lname = findViewById(R.id.et_lname);
        email = findViewById(R.id.et_email);
        phone = findViewById(R.id.et_phone);
        birthplace = findViewById(R.id.et_birthplace);
        birthdate = findViewById(R.id.et_birthdate);
        emergencyNumber = findViewById(R.id.et_emergency);
        contact = findViewById(R.id.et_contact);
        specialNeed = findViewById(R.id.et_special_need);
        otherNeed = findViewById(R.id.et_other_need);
        contactType = findViewById(R.id.spn_contact_type);
        populate();
    }

    public void populate() {
        if (healthPojo != null && !TextUtils.isEmpty(healthPojo.getData())) {
            myHealth = new Gson().fromJson(AndroidDecrypter.decrypt(healthPojo.getData(), userModelB.getKey(), userModelB.getIv()), RealmMyHealth.class);
            RealmMyHealth.RealmMyHealthProfile health = myHealth.getProfile();
            emergencyNumber.getEditText().setText(health.getEmergencyContactName());
            specialNeed.getEditText().setText(health.getSpecialNeeds());
            otherNeed.getEditText().setText(health.getNotes());
        }
        if (userModelB != null) {
            fname.getEditText().setText(userModelB.getFirstName());
            mname.getEditText().setText(userModelB.getMiddleName());
            lname.getEditText().setText(userModelB.getLastName());
            email.getEditText().setText(userModelB.getEmail());
            phone.getEditText().setText(userModelB.getPhoneNumber());
            birthdate.getEditText().setText(userModelB.getDob());
            birthplace.getEditText().setText(userModelB.getBirthPlace());
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home)
            finish();
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (realm != null)
            realm.close();
    }
}
