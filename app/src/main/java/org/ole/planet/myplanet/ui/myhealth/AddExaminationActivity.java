package org.ole.planet.myplanet.ui.myhealth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.flexbox.FlexboxLayout;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.ActivityAddExaminationBinding;
import org.ole.planet.myplanet.databinding.ActivityFeedbackDetailBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyHealth;
import org.ole.planet.myplanet.model.RealmMyHealthPojo;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.AndroidDecrypter;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.DimenUtils;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fisk.chipcloud.ChipCloud;
import fisk.chipcloud.ChipCloudConfig;
import io.realm.Realm;

public class AddExaminationActivity extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener {
    private ActivityAddExaminationBinding activityAddExaminationBinding;
    Realm mRealm;
    String userId;
    RealmUserModel user;
    RealmUserModel currentUser;
    RealmMyHealthPojo pojo;
    RealmMyHealth health = null;
    Set<String> customDiag;
    HashMap<String, Boolean> mapConditions;
    Boolean allowSubmission = true;
    private ChipCloudConfig config;

    private void initViews() {
        config = Utilities.getCloudConfig().selectMode(ChipCloud.SelectMode.close);
        activityAddExaminationBinding.btnAddDiag.setOnClickListener(view -> {
            customDiag.add(activityAddExaminationBinding.etOtherDiag.getText().toString());
            activityAddExaminationBinding.etOtherDiag.setText("");
            showOtherDiagnosis();
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityAddExaminationBinding = ActivityAddExaminationBinding.inflate(getLayoutInflater());
        setContentView(activityAddExaminationBinding.getRoot());
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        customDiag = new HashSet<>();
        initViews();
        currentUser = new UserProfileDbHandler(this).getUserModel();
        mapConditions = new HashMap<String, Boolean>();
        mRealm = new DatabaseService(this).getRealmInstance();
        userId = getIntent().getStringExtra("userId");
        pojo = mRealm.where(RealmMyHealthPojo.class).equalTo("_id", userId).findFirst();
        if (pojo == null) {
            pojo = mRealm.where(RealmMyHealthPojo.class).equalTo("userId", userId).findFirst();
        }
        user = mRealm.where(RealmUserModel.class).equalTo("id", userId).findFirst();
        if (pojo != null && !TextUtils.isEmpty(pojo.getData())) {
            health = new Gson().fromJson(AndroidDecrypter.decrypt(pojo.getData(), user.getKey(), user.getIv()), RealmMyHealth.class);
        }

        if (health == null) {
            initHealth();
        }
        initExamination();
        validateFields();
        findViewById(R.id.btn_save).setOnClickListener(view -> {
            if (!isValidInput() || !allowSubmission) {
                Utilities.toast(this, getString(R.string.invalid_input));
                return;
            }
            saveData();
        });
    }

    RealmMyHealthPojo examination;

    private void initExamination() {
        if (getIntent().hasExtra("id")) {
            examination = mRealm.where(RealmMyHealthPojo.class).equalTo("_id", getIntent().getStringExtra("id")).findFirst();
            activityAddExaminationBinding.etTemperature.setText(examination.getTemperature() + "");
            activityAddExaminationBinding.etPulseRate.setText(examination.getPulse() + "");
            activityAddExaminationBinding.etBloodpressure.setText(examination.getBp() + "");
            activityAddExaminationBinding.etHeight.setText(examination.getHeight() + "");
            activityAddExaminationBinding.etWeight.setText(examination.getWeight() + "");
            activityAddExaminationBinding.etVision.setText(examination.getVision());
            activityAddExaminationBinding.etHearing.setText(examination.getHearing());
            JsonObject encrypted = examination.getEncryptedDataAsJson(this.user);
            activityAddExaminationBinding.etObservation.setText(JsonUtils.getString(getString(R.string.note_), encrypted));
            activityAddExaminationBinding.etDiag.setText(JsonUtils.getString(getString(R.string.diagno), encrypted));
            activityAddExaminationBinding.etTreatments.setText(JsonUtils.getString(getString(R.string.treat), encrypted));
            activityAddExaminationBinding.etMedications.setText(JsonUtils.getString(getString(R.string.medicay), encrypted));
            activityAddExaminationBinding.etImmunization.setText(JsonUtils.getString(getString(R.string.immunizations), encrypted));
            activityAddExaminationBinding.etAllergies.setText(JsonUtils.getString(getString(R.string.allergy), encrypted));
            activityAddExaminationBinding.etXray.setText(JsonUtils.getString(getString(R.string.xrays), encrypted));
            activityAddExaminationBinding.etLabtest.setText(JsonUtils.getString(getString(R.string.tests), encrypted));
            activityAddExaminationBinding.etReferrals.setText(JsonUtils.getString(getString(R.string.referral), encrypted));
        }
        showCheckbox(examination);
        showOtherDiagnosis();
    }

    private void validateFields() {
        allowSubmission = true;
        activityAddExaminationBinding.etBloodpressure.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!activityAddExaminationBinding.etBloodpressure.getText().toString().contains("/")) {
                    activityAddExaminationBinding.etBloodpressure.setError(getString(R.string.blood_pressure_should_be_numeric_systolic_diastolic));
                    allowSubmission = false;
                } else {
                    String[] sysDia = activityAddExaminationBinding.etBloodpressure.getText().toString().trim().split("/");
                    if (sysDia.length > 2 || sysDia.length < 1) {
                        activityAddExaminationBinding.etBloodpressure.setError(getString(R.string.blood_pressure_should_be_systolic_diastolic));
                        allowSubmission = false;
                    } else {
                        try {
                            int sys = Integer.parseInt(sysDia[0]);
                            int dis = Integer.parseInt(sysDia[1]);
                            if ((sys < 60 || dis < 40) || (sys > 300 || dis > 200)) {
                                activityAddExaminationBinding.etBloodpressure.setError(getString(R.string.bp_must_be_between_60_40_and_300_200));
                                allowSubmission = false;
                            } else {
                                allowSubmission = true;
                            }
                        } catch (Exception e) {
                            activityAddExaminationBinding.etBloodpressure.setError(getString(R.string.systolic_and_diastolic_must_be_numbers));
                            allowSubmission = false;
                        }
                    }
                }
            }
        });
    }

    private void showOtherDiagnosis() {
        activityAddExaminationBinding.containerOtherDiagnosis.removeAllViews();
        final ChipCloud chipCloud = new ChipCloud(this, activityAddExaminationBinding.containerOtherDiagnosis, config);

        for (String s : customDiag) {
            chipCloud.addChip(s);

        }
        chipCloud.setDeleteListener((i, s1) -> {
            customDiag.remove(s1);
        });
        preloadCustomDiagnosis(chipCloud);
    }

    private void preloadCustomDiagnosis(ChipCloud chipCloud) {
        String[] arr = getResources().getStringArray(R.array.diagnosis_list);
        List<String> mainList = Arrays.asList(arr);
        if (customDiag.isEmpty() && examination != null) {
            JsonObject conditions = new Gson().fromJson(examination.getConditions(), JsonObject.class);
            for (String s : conditions.keySet()) {
                if (!mainList.contains(s) && JsonUtils.getBoolean(s, conditions)) {
                    chipCloud.addChip(s);
                    chipCloud.setDeleteListener((i, s1) -> customDiag.remove(Constants.LABELS.get(s1)));
                    customDiag.add(s);
                }
            }
        }
    }

    private void showCheckbox(RealmMyHealthPojo examination) {
        String[] arr = getResources().getStringArray(R.array.diagnosis_list);
        activityAddExaminationBinding.containerCheckbox.removeAllViews();
        for (String s : arr) {
            CheckBox c = new CheckBox(this);
            if (examination != null) {
                JsonObject conditions = new Gson().fromJson(examination.getConditions(), JsonObject.class);
                c.setChecked(JsonUtils.getBoolean(s, conditions));
            }
            c.setPadding(DimenUtils.dpToPx(8), DimenUtils.dpToPx(8), DimenUtils.dpToPx(8), DimenUtils.dpToPx(8));
            c.setText(s);
            c.setTag(s);
            c.setOnCheckedChangeListener(this);
            activityAddExaminationBinding.containerCheckbox.addView(c);
        }
    }

    private void getOtherConditions() {
        for (String s : customDiag) {
            mapConditions.put(s, true);
        }
    }

    private void initHealth() {
        if (!mRealm.isInTransaction()) mRealm.beginTransaction();
        health = new RealmMyHealth();
        RealmMyHealth.RealmMyHealthProfile profile = new RealmMyHealth.RealmMyHealthProfile();
        health.lastExamination = new Date().getTime();
        health.userKey = AndroidDecrypter.generateKey();
        health.profile = profile;
        mRealm.commitTransaction();
    }

    private void saveData() {

        if (!mRealm.isInTransaction()) mRealm.beginTransaction();
        createPojo();
        if (examination == null) {
            String userId = AndroidDecrypter.generateIv();
            examination = mRealm.createObject(RealmMyHealthPojo.class, userId);
            examination.setUserId(userId);
        }

        examination.setProfileId(health.userKey);
        examination.setCreatorId(health.userKey);
        examination.setGender(user.getGender());

        examination.setAge(TimeUtils.getAge(user.getDob()));
        examination.setSelfExamination(currentUser.get_id().equals(pojo.get_id()));
        examination.setDate(new Date().getTime());
        examination.setPlanetCode(user.getPlanetCode());
        RealmExamination sign = new RealmExamination();
        sign.setAllergies(activityAddExaminationBinding.etAllergies.getText().toString().trim());
        sign.setCreatedBy(currentUser.get_id());
        examination.setBp(activityAddExaminationBinding.etBloodpressure.getText().toString().trim());
        examination.setTemperature(getFloat(activityAddExaminationBinding.etTemperature.getText().toString().trim()));
        examination.setPulse(getInt(activityAddExaminationBinding.etPulseRate.getText().toString().trim()));
        examination.setWeight(getFloat(activityAddExaminationBinding.etWeight.getText().toString().trim()));
        examination.setHeight(getFloat(activityAddExaminationBinding.etHeight.getText().toString().trim()));
        getOtherConditions();
        examination.setConditions(new Gson().toJson(mapConditions));
        examination.setHearing(activityAddExaminationBinding.etHearing.getText().toString().trim());
        sign.setImmunizations(activityAddExaminationBinding.etImmunization.getText().toString().trim());
        sign.setTests(activityAddExaminationBinding.etLabtest.getText().toString().trim());
        sign.setXrays(activityAddExaminationBinding.etXray.getText().toString().trim());
        examination.setVision(activityAddExaminationBinding.etVision.getText().toString().trim());
        sign.setTreatments(activityAddExaminationBinding.etTreatments.getText().toString().trim());
        sign.setReferrals(activityAddExaminationBinding.etReferrals.getText().toString().trim());
        sign.setNotes(activityAddExaminationBinding.etObservation.getText().toString().trim());
        sign.setDiagnosis(activityAddExaminationBinding.etDiag.getText().toString().trim());
        sign.setMedications(activityAddExaminationBinding.etMedications.getText().toString().trim());
        examination.setDate(new Date().getTime());
        examination.setIsUpdated(true);
        examination.setHasInfo(getHasInfo());
        pojo.setIsUpdated(true);
        try {
            Utilities.log(new Gson().toJson(sign));
            examination.setData(AndroidDecrypter.encrypt(new Gson().toJson(sign), user.getKey(), user.getIv()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        mRealm.commitTransaction();
        Utilities.toast(this, getString(R.string.added_successfully));
        super.finish();
    }

    private boolean getHasInfo() {
        return !TextUtils.isEmpty(activityAddExaminationBinding.etAllergies.getText().toString()) ||
                !TextUtils.isEmpty(activityAddExaminationBinding.etDiag.getText().toString()) ||
                !TextUtils.isEmpty(activityAddExaminationBinding.etImmunization.getText().toString()) ||
                !TextUtils.isEmpty(activityAddExaminationBinding.etMedications.getText().toString()) ||
                !TextUtils.isEmpty(activityAddExaminationBinding.etObservation.getText().toString()) ||
                !TextUtils.isEmpty(activityAddExaminationBinding.etReferrals.getText().toString()) ||
                !TextUtils.isEmpty(activityAddExaminationBinding.etLabtest.getText().toString()) ||
                !TextUtils.isEmpty(activityAddExaminationBinding.etTreatments.getText().toString()) ||
                !TextUtils.isEmpty(activityAddExaminationBinding.etXray.getText().toString());
    }

    private boolean isValidInput() {
        boolean isValidTemp = 30 <= getFloat(activityAddExaminationBinding.etTemperature.getText().toString().trim()) && getFloat(activityAddExaminationBinding.etTemperature.getText().toString().trim()) <= 40 || getFloat(activityAddExaminationBinding.etTemperature.getText().toString().trim()) == 0;
        boolean isValidPulse = 40 <= getInt(activityAddExaminationBinding.etPulseRate.getText().toString().trim()) && getInt(activityAddExaminationBinding.etPulseRate.getText().toString().trim()) <= 120 || getFloat(activityAddExaminationBinding.etPulseRate.getText().toString().trim()) == 0;
        boolean isValidHeight = 1 <= getFloat(activityAddExaminationBinding.etHeight.getText().toString().trim()) && getFloat(activityAddExaminationBinding.etHeight.getText().toString().trim()) <= 250 || getFloat(activityAddExaminationBinding.etHeight.getText().toString().trim()) == 0;
        boolean isValidWeight = 1 <= getFloat(activityAddExaminationBinding.etWeight.getText().toString().trim()) && getFloat(activityAddExaminationBinding.etWeight.getText().toString().trim()) <= 150 || getFloat(activityAddExaminationBinding.etWeight.getText().toString().trim()) == 0;
        if (!isValidTemp) {
            activityAddExaminationBinding.etTemperature.setError(getString(R.string.invalid_input_must_be_between_30_and_40));
        }
        if (!isValidPulse) {
            activityAddExaminationBinding.etPulseRate.setError(getString(R.string.invalid_input_must_be_between_40_and_120));
        }
        if (!isValidHeight) {
            activityAddExaminationBinding.etHeight.setError(getString(R.string.invalid_input_must_be_between_1_and_250));
        }
        if (!isValidWeight) {
            activityAddExaminationBinding.etWeight.setError(getString(R.string.invalid_input_must_be_between_1_and_150));
        }
        return isValidTemp && isValidHeight && isValidPulse && isValidWeight;
    }

//    private float getFloat(String trim) {
//    }

    private int getInt(String trim) {
        try {
            return Integer.parseInt(trim);
        } catch (Exception e) {
            return 0;
        }
    }

    private float getFloat(String trim) {
        try {
            return Float.parseFloat(String.format("%.1f", Float.parseFloat(trim)));
        } catch (Exception e) {
            return getInt(trim);
        }
    }

    private void createPojo() {
        try {
            if (pojo == null) {
                pojo = mRealm.createObject(RealmMyHealthPojo.class, userId);
                pojo.setUserId(user.get_id());
            }
//            if (TextUtils.isEmpty(pojo.getData())) {
            health.lastExamination = new Date().getTime();
            pojo.setData(AndroidDecrypter.encrypt(new Gson().toJson(health), user.getKey(), user.getIv()));
//            }
        } catch (Exception e) {
            e.printStackTrace();
            Utilities.toast(this, getString(R.string.unable_to_add_health_record));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void finish() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage(R.string.are_you_sure_you_want_to_exit_your_data_will_be_lost);
        alertDialogBuilder.setPositiveButton(getString(R.string.yes_i_want_to_exit), (dialogInterface, i) -> {
            super.finish();
        }).setNegativeButton(getString(R.string.cancel), null);
        alertDialogBuilder.show();
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        String text = compoundButton.getText().toString().trim();
        mapConditions.put(text, b);
    }
}
