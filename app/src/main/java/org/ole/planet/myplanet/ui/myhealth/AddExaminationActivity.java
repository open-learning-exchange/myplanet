package org.ole.planet.myplanet.ui.myhealth;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;

import com.google.android.flexbox.FlexboxLayout;
import com.google.gson.Gson;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyHealth;
import org.ole.planet.myplanet.model.RealmMyHealthPojo;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.AndroidDecrypter;
import org.ole.planet.myplanet.utilities.DimenUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.Date;
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
    String diag = "";

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
        mRealm = new DatabaseService(this).getRealmInstance();
        userId = getIntent().getStringExtra("userId");
        pojo = mRealm.where(RealmMyHealthPojo.class).equalTo("userId", userId).findFirst();
        if (pojo == null){
            mRealm.where(RealmMyHealthPojo.class).equalTo("_id", userId).findFirst();
        }
        user = mRealm.where(RealmUserModel.class).equalTo("id", userId).findFirst();
//        if (TextUtils.isEmpty(user.getIv())) {
//            Utilities.toast(this, "You cannot create health record from myPlanet. Please contact your manager.");
//            finish();
//            return;
//        }
        if (pojo != null && !TextUtils.isEmpty(pojo.getData())) {
            Utilities.log("health data" +  pojo.getData());
            health = new Gson().fromJson(AndroidDecrypter.decrypt(pojo.getData(), user.getKey(), user.getIv()), RealmMyHealth.class);
        }
        if (health == null || health.getProfile() == null) {
            initHealth();
        }
        initExamination();

        findViewById(R.id.btn_save).setOnClickListener(view -> {
            saveData();
        });
    }

    private void initExamination() {
        List<RealmExamination> list = health.getEvents();
        RealmExamination examination = null;
        if (getIntent().hasExtra("position") && list != null) {
            int position = getIntent().getIntExtra("position", 0);
            examination = list.get(position);
            etTemperature.setText(examination.getTemperature());
            etPulseRate.setText(examination.getPulse());
            etBloodPressure.setText(examination.getBp());
            etTemperature.setText(examination.getTemperature());
            etHeight.setText(examination.getHeight());
            etWeight.setText(examination.getWeight());
            etVision.setText(examination.getVision());
            etHearing.setText(examination.getHearing());
            etObservation.setText(examination.getNotes());
            etDiag.setText(examination.getDiagnosisNote());
            etTretments.setText(examination.getTreatments());
            etMedications.setText(examination.getMedications());
            etImmunization.setText(examination.getImmunizations());
            etAllergies.setText(examination.getAllergies());
            etXray.setText(examination.getXrays());
            etLabtest.setText(examination.getTests());
            etReferrals.setText(examination.getReferrals());
        }
        showCheckbox(examination);

    }

    private void showCheckbox(RealmExamination examination) {
        String[] arr = getResources().getStringArray(R.array.diagnosis_list);
        flexboxLayout.removeAllViews();
        for (String s : arr) {
            CheckBox c = new CheckBox(this);
            if (examination != null) {
                c.setChecked(examination.getDiagnosis().contains(s));
                diag += examination.getDiagnosis().contains(s) ? s + "," : "";
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
        profile.setFirstName(user.getFirstName());
        profile.setMiddleName(user.getMiddleName());
        profile.setLastName(user.getLastName());
        profile.setLanguage(user.getLanguage());
        profile.setBirthDate(user.getDob());
        profile.setBirthplace(user.getBirthPlace());
        profile.setEmail(user.getEmail());
        profile.setPhone(user.getPhoneNumber());
        health.setProfile(profile);
        Utilities.log("Init health");
    }

    private void saveData() {

        RealmExamination sign = new RealmExamination();
        sign.setAllergies(etAllergies.getText().toString().trim());
        sign.setAddedBy(user.getId());
        sign.setBp(etBloodPressure.getText().toString().trim());
        sign.setTemperature(etTemperature.getText().toString().trim());
        sign.setPulse(etPulseRate.getText().toString().trim());
        sign.setWeight(etWeight.getText().toString().trim());
        sign.setDiagnosisNote(etDiag.getText().toString().trim());
        sign.setDiagnosis(diag);
        sign.setHearing(etHearing.getText().toString().trim());
        sign.setHeight(etHeight.getText().toString().trim());
        sign.setImmunizations(etImmunization.getText().toString().trim());
        sign.setTests(etLabtest.getText().toString().trim());
        sign.setXrays(etXray.getText().toString().trim());
        sign.setVision(etVision.getText().toString().trim());
        sign.setTreatments(etTretments.getText().toString().trim());
        sign.setReferrals(etReferrals.getText().toString().trim());
        sign.setNotes(etObservation.getText().toString().trim());
        sign.setMedications(etMedications.getText().toString().trim());
        sign.setDate(new Date().getTime());
        List<RealmExamination> list = health.getEvents();
        if (list == null) {
            list = new ArrayList<>();
        }

        if (getIntent().hasExtra("position")) {
            int pos = getIntent().getIntExtra("position", 0);
            list.remove(pos);
            list.add(pos, sign);
        } else {
            list.add(sign);
        }
        createPojo(list);
        Utilities.toast(this, "Added successfully");
        finish();

    }

    private void createPojo(List<RealmExamination> list) {
        try {
            health.setEvents(list);
            if (!mRealm.isInTransaction())
                mRealm.beginTransaction();
            if (pojo == null) {
                pojo = mRealm.createObject(RealmMyHealthPojo.class, UUID.randomUUID().toString());
            }
            pojo.setUserId(userId);
            pojo.setData(TextUtils.isEmpty(user.getIv()) ? new Gson().toJson(health) : AndroidDecrypter.encrypt(new Gson().toJson(health), user.getKey(), user.getIv()));
            mRealm.commitTransaction();
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
        if (b) {
            diag = diag + "," + text + " ";
        } else {
            diag = diag.replace("," + text + " ", "");
        }
    }
}
