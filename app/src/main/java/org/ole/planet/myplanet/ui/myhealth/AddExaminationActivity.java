package org.ole.planet.myplanet.ui.myhealth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.flexbox.FlexboxLayout;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyHealth;
import org.ole.planet.myplanet.model.RealmMyHealthPojo;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.AndroidDecrypter;
import org.ole.planet.myplanet.utilities.DimenUtils;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;

public class AddExaminationActivity extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener {
    Realm mRealm;
    String userId;
    EditText etTemperature, etPulseRate, etBloodPressure, etHeight, etWeight, etVision, etHearing,
            etObservation, etDiag, etTretments, etMedications, etImmunization, etAllergies, etXray, etLabtest, etReferrals;
    RealmUserModel user;
    RealmMyHealthPojo pojo;
    RealmMyHealth health = null;
    FlexboxLayout flexboxLayout;
    HashMap<String, Boolean> mapConditions;
    Boolean allowSubmission = true;

    private void initViews() {
        etTemperature = findViewById(R.id.et_temperature);
        etPulseRate = findViewById(R.id.et_pulse_rate);
        flexboxLayout = findViewById(R.id.container_checkbox);
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
        mapConditions = new HashMap<String, Boolean>();
        mRealm = new DatabaseService(this).getRealmInstance();
        userId = getIntent().getStringExtra("userId");
        pojo = mRealm.where(RealmMyHealthPojo.class).equalTo("_id", userId).findFirst();
        user = mRealm.where(RealmUserModel.class).equalTo("id", userId).findFirst();
        if (pojo != null && !TextUtils.isEmpty(pojo.getData())) {
            health = new Gson().fromJson(AndroidDecrypter.decrypt(pojo.getData(), user.getKey(), user.getIv()), RealmMyHealth.class);
        }
        if (health == null || health.getProfile() == null) {
            initHealth();
        }
        initExamination();
        validateFields();
        findViewById(R.id.btn_save).setOnClickListener(view -> {
            saveData();
        });
    }

    RealmMyHealthPojo examination;

    private void initExamination() {
        if (getIntent().hasExtra("id")) {
            examination = mRealm.where(RealmMyHealthPojo.class).equalTo("_id", getIntent().getStringExtra("id")).findFirst();
            etTemperature.setText(examination.getTemperature());
            etPulseRate.setText(examination.getPulse());
            etBloodPressure.setText(examination.getBp());
            etTemperature.setText(examination.getTemperature());
            etHeight.setText(examination.getHeight());
            etWeight.setText(examination.getWeight());
            etVision.setText(examination.getVision());
            etHearing.setText(examination.getHearing());
            JsonObject encrypted = examination.getEncryptedDataAsJson(this.user);

            etObservation.setText(JsonUtils.getString("notes", encrypted));
            etDiag.setText(JsonUtils.getString("diagnosis", encrypted));
            etTretments.setText(JsonUtils.getString("treatments", encrypted));
            etMedications.setText(JsonUtils.getString("medications", encrypted));
            etImmunization.setText(JsonUtils.getString("immunizations", encrypted));
            etAllergies.setText(JsonUtils.getString("allergies", encrypted));
            etXray.setText(JsonUtils.getString("xrays", encrypted));
            etLabtest.setText(JsonUtils.getString("tests", encrypted));
            etReferrals.setText(JsonUtils.getString("referrals", encrypted));
            showCheckbox(examination);
        }

    }

    private void validateFields() {
        allowSubmission = false;
        etBloodPressure.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!etBloodPressure.getText().toString().contains("/")) {
                    etBloodPressure.setError("Blood Pressure should be numeric systolic/diastolic");
                } else {
                    String[] sysDia = etBloodPressure.getText().toString().trim().split("/");
                    if (sysDia.length > 2 || sysDia.length < 1) {
                        etBloodPressure.setError("Blood Pressure should be systolic/diastolic");
                        allowSubmission = false;
                    } else {
                        for (int x = 0; x < sysDia.length; x++) {
                            if (!sysDia[x].matches("-?\\d+") || sysDia[x].isEmpty()) {
                                etBloodPressure.setError("Systolic and diastolic must be numbers");
                                allowSubmission = false;
                            }
                        }
                        allowSubmission = true;
                    }
                }
            }
        });
    }


    private void showCheckbox(RealmMyHealthPojo examination) {
        String[] arr = getResources().getStringArray(R.array.diagnosis_list);
        flexboxLayout.removeAllViews();
        for (String s : arr) {
            CheckBox c = new CheckBox(this);
            if (examination != null) {
                JsonObject conditions = new Gson().fromJson(examination.getConditions(), JsonObject.class);
                c.setChecked(JsonUtils.getBoolean("s", conditions));

            }
            c.setPadding(DimenUtils.dpToPx(8), DimenUtils.dpToPx(8), DimenUtils.dpToPx(8), DimenUtils.dpToPx(8));
            c.setText(s);
            c.setTag(s);
            c.setOnCheckedChangeListener(this);
            flexboxLayout.addView(c);
        }
    }

    private void initHealth() {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        health = new RealmMyHealth();
        RealmMyHealth.RealmMyHealthProfile profile = new RealmMyHealth.RealmMyHealthProfile();
        health.setProfile(profile);
    }

    private void saveData() {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        createPojo();
        if (examination == null) {
            examination = mRealm.createObject(RealmMyHealthPojo.class, UUID.randomUUID().toString());
        }
        examination.setProfileId(health.getUserKey());
        examination.setSelfExamination(userId.equals(pojo.get_id()));
        examination.setDate(new Date().getTime());
        examination.setPlanetCode(user.getPlanetCode());
        RealmExamination sign = new RealmExamination();
        sign.setAllergies(etAllergies.getText().toString().trim());
        sign.setCreatedBy(user.get_id());
        pojo.setBp(etBloodPressure.getText().toString().trim());
        examination.setTemperature(getInt(etTemperature.getText().toString().trim()));
        examination.setPulse(getInt(etPulseRate.getText().toString().trim()));
        examination.setWeight(getInt(etWeight.getText().toString().trim()));
        examination.setConditions(new Gson().toJson(mapConditions));
        examination.setHearing(etHearing.getText().toString().trim());
        examination.setHeight(getInt(etHeight.getText().toString().trim()));
        sign.setImmunizations(etImmunization.getText().toString().trim());
        sign.setTests(etLabtest.getText().toString().trim());
        sign.setXrays(etXray.getText().toString().trim());
        examination.setVision(etVision.getText().toString().trim());
        sign.setTreatments(etTretments.getText().toString().trim());
        sign.setReferrals(etReferrals.getText().toString().trim());
        sign.setNotes(etObservation.getText().toString().trim());
        sign.setMedications(etMedications.getText().toString().trim());
        examination.setDate(new Date().getTime());
        try {
            examination.setData(AndroidDecrypter.encrypt(new Gson().toJson(sign), user.getKey(), user.getIv()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        mRealm.commitTransaction();
        Utilities.toast(this, "Added successfully");
        finish();

    }

    private int getInt(String trim) {
        try {
            return Integer.parseInt(etTemperature.getText().toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private void createPojo() {
        try {
            if (!mRealm.isInTransaction())
                mRealm.beginTransaction();
            if (pojo == null) {
                pojo = mRealm.createObject(RealmMyHealthPojo.class, userId);
            }
        } catch (Exception e) {
            Utilities.toast(this, "Unable to add health record.");
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home)
            finish();
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        String text = compoundButton.getText().toString().trim();
        mapConditions.put(text, b);
    }
}
