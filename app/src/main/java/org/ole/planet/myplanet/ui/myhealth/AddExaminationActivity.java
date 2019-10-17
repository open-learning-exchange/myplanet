package org.ole.planet.myplanet.ui.myhealth;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Date;
import java.util.UUID;

import io.realm.Realm;

public class AddExaminationActivity extends AppCompatActivity {
    Realm mRealm;
    String userId;
    EditText etTemperature, etPulseRate, etBloodPressure, etHeight, etWeight, etVision, etHearing,
            etObservation, etDiag, etTretments, etMedications, etImmunization, etAllergies, etXray, etLabtest, etReferrals;


    private void initViews() {
        etTemperature = findViewById(R.id.et_temperature);
        etPulseRate = findViewById(R.id.et_pulse_rate);
        etBloodPressure = findViewById(R.id.et_bloodpressure);
        etHeight = findViewById(R.id.et_height);
        etWeight = findViewById(R.id.et_weight);
        etVision = findViewById(R.id.et_vision);
        etHearing = findViewById(R.id.et_hearing);
        etObservation = findViewById(R.id.et_observation);
        etDiag = findViewById(R.id.et_diag);
        etTretments = findViewById(R.id.et_treatments);
        etMedications = findViewById(R.id.et_medications);
        etImmunization = findViewById(R.id.et_immunization);
        etAllergies = findViewById(R.id.et_allergies);
        etXray = findViewById(R.id.et_xray);
        etLabtest = findViewById(R.id.et_labtest);
        etReferrals = findViewById(R.id.et_referrals);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_examination);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        initViews();
        userId = getIntent().getStringExtra("userId");
        mRealm = new DatabaseService(this).getRealmInstance();
        findViewById(R.id.btn_save).setOnClickListener(view -> {
            saveData();
        });

    }

    private void saveData() {
        try {
            mRealm.executeTransactionAsync(realm -> {
                RealmExamination sign = realm.createObject(RealmExamination.class, UUID.randomUUID().toString());
                sign.setAllergies(etAllergies.getText().toString());
                sign.setBp(etBloodPressure.getText().toString());
                sign.setTemperature(etTemperature.getText().toString());
                sign.setPulse(etPulseRate.getText().toString());
                sign.setWeight(etWeight.getText().toString());
                sign.setDionosis(etDiag.getText().toString());
                sign.setHearing(etHearing.getText().toString());
                sign.setHeight(etHeight.getText().toString());
                sign.setImmunizationDate(etImmunization.getText().toString());
                sign.setLabtests(etLabtest.getText().toString());
                sign.setXrays(etXray.getText().toString());
                sign.setVision(etVision.getText().toString());
                sign.setTreatments(etTretments.getText().toString());
                sign.setReferrals(etReferrals.getText().toString());
                sign.setNotes(etObservation.getText().toString());
                sign.setMedications(etMedications.getText().toString());
                sign.setCreated(new Date().getTime());
                sign.setUserId(userId);
            }, () -> {
                Utilities.toast(AddExaminationActivity.this, "Record Saved");
                finish();
            });
        } catch (Exception e) {
            Utilities.toast(this, "All fields are required");
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home)
            finish();
        return super.onOptionsItemSelected(item);
    }
}
