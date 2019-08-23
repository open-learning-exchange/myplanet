package org.ole.planet.myplanet.ui.myhealth;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.UUID;

import io.realm.Realm;

public class AddVitalSignActivity extends AppCompatActivity {
    Realm mRealm;
    String userId;
    RadioGroup rbMethod;
    RadioButton rbRectally, rbOrally, rbAxillary, rbByEar, rbBySkin;
    EditText etTemperature, etPulseRate, etRespirationRate, etBloodPressureSystolic, etBloodPressureDiastolic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_vital_sign);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        initViews();
        userId = getIntent().getStringExtra("userId");
        mRealm = new DatabaseService(this).getRealmInstance();
        findViewById(R.id.btn_save).setOnClickListener(view -> {
            String method = "";
            RadioButton btn = findViewById(rbMethod.getCheckedRadioButtonId());
            if (btn != null) {
                method = ((RadioButton) findViewById(rbMethod.getCheckedRadioButtonId())).getText().toString();
            } else {
                Utilities.toast(this, "Please select Temperature taken method");
            }
            saveData(method);

        });

    }

    private void saveData(String method) {
        try {
            float temp = Float.parseFloat(etTemperature.getText().toString());
            int pulseRate = Integer.parseInt(etPulseRate.getText().toString());
            int respRate = Integer.parseInt(etRespirationRate.getText().toString());
            int systolic = Integer.parseInt(etBloodPressureSystolic.getText().toString());
            int diastolic = Integer.parseInt(etBloodPressureDiastolic.getText().toString());
            mRealm.executeTransactionAsync(realm -> {
                RealmVitalSign sign = realm.createObject(RealmVitalSign.class, UUID.randomUUID().toString());
                sign.setMethod(method);
                sign.setBodyTemp(temp);
                sign.setBloodPressureDiastolic(diastolic);
                sign.setBloodPressureSystolic(systolic);
                sign.setRespirationRate(respRate);
                sign.setPulseRate(pulseRate);
                sign.setUserId(userId);
            }, () -> {
                Utilities.toast(AddVitalSignActivity.this, "Record Saved");
                finish();
            });
        } catch (Exception e) {
            Utilities.toast(this, "All fields are required");
        }
    }

    private void initViews() {
        rbMethod = findViewById(R.id.rb_method);
        rbRectally = findViewById(R.id.rb_rectally);
        rbOrally = findViewById(R.id.rb_orally);
        rbAxillary = findViewById(R.id.rb_axillary);
        rbByEar = findViewById(R.id.rb_by_ear);
        rbBySkin = findViewById(R.id.rb_by_skin);
        etTemperature = findViewById(R.id.et_temperature);
        etPulseRate = findViewById(R.id.et_pulse_rate);
        etRespirationRate = findViewById(R.id.et_respiration_rate);
        etBloodPressureSystolic = findViewById(R.id.et_systolic);
        etBloodPressureDiastolic = findViewById(R.id.et_diastolic);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home)
            finish();
        return super.onOptionsItemSelected(item);
    }
}
