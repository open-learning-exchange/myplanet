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

import java.util.List;

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
        userModelB = realm.where(RealmUserModel.class).equalTo("id", userId).findFirst();

        key = userModelB.getKey();
        iv = userModelB.getIv();
        if (TextUtils.isEmpty(key) || TextUtils.isEmpty(iv)) {
            Utilities.toast(this, "You cannot create health record from myPlanet. Please contact your manager.");
            finish();
            return;
        }
        initViews();
        findViewById(R.id.btn_submit).setOnClickListener(view -> {
            createMyHealth();
            Utilities.toast(AddMyHealthActivity.this, "My health saved successfully");
        });
    }

    private void createMyHealth() {
        if (!realm.isInTransaction())
            realm.beginTransaction();
        RealmMyHealth.RealmMyHealthProfile health = new RealmMyHealth.RealmMyHealthProfile();
        health.setFirstName(fname.getEditText().getText().toString().trim());
        health.setMiddleName(mname.getEditText().getText().toString().trim());
        health.setLastName(lname.getEditText().getText().toString().trim());
        health.setEmail(email.getEditText().getText().toString().trim());
        health.setBirthDate(birthdate.getEditText().getText().toString().trim());
        health.setBirthplace(birthplace.getEditText().getText().toString().trim());
        health.setEmergencyContactName(emergencyNumber.getEditText().getText().toString().trim());
        health.setEmergencyContact(contact.getEditText().getText().toString().trim());
        health.setEmergencyContactType(contactType.getSelectedItem().toString());
        health.setSpecialNeeds(specialNeed.getEditText().getText().toString().trim());
        health.setNotes(otherNeed.getEditText().getText().toString().trim());
        if (myHealth == null)
            myHealth = new RealmMyHealth();
        myHealth.setProfile(health);
        if (healthPojo == null) {
            healthPojo = realm.createObject(RealmMyHealthPojo.class, userId);
        }
        try {
            healthPojo.setData(AndroidDecrypter.encrypt(new Gson().toJson(myHealth), key, iv));
        } catch (Exception e) {}
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
        if (healthPojo != null) {
            myHealth = new Gson().fromJson(AndroidDecrypter.decrypt(healthPojo.getData(), userModelB.getKey(), userModelB.getIv()), RealmMyHealth.class);
            RealmMyHealth.RealmMyHealthProfile health = myHealth.getProfile();
            fname.getEditText().setText(health.getFirstName());
            mname.getEditText().setText(health.getMiddleName());
            lname.getEditText().setText(health.getLastName());
            email.getEditText().setText(health.getEmail());
            phone.getEditText().setText(health.getPhone());
            emergencyNumber.getEditText().setText(health.getEmergencyContactName());
            birthdate.getEditText().setText(health.getBirthDate());
            birthplace.getEditText().setText(health.getBirthplace());
            specialNeed.getEditText().setText(health.getSpecialNeeds());
            otherNeed.getEditText().setText(health.getNotes());
        } else if (userModelB != null) {
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
