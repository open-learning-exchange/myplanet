package org.ole.planet.myplanet.ui.myhealth;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;

import io.realm.Realm;

public class MyHealthDetailActivity extends AppCompatActivity {
    TextView tvTemp, tvPulse, tvRespiration, tvBlood;
    String id;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_health_detail);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        id = getIntent().getStringExtra("id");
        tvTemp = findViewById(R.id.tv_temp);
        tvPulse = findViewById(R.id.tv_pulse_rate);
        tvRespiration = findViewById(R.id.tv_respiration);
        tvBlood = findViewById(R.id.tv_blood_pressure);
        Realm realm = new DatabaseService(this).getRealmInstance();
        RealmVitalSign realmVitalSign = realm.where(RealmVitalSign.class).equalTo("id", id).findFirst();
        setBloodPressureStatus(realmVitalSign);
        tvRespiration.setText(String.format("Respiration Rate : %d", realmVitalSign.getRespirationRate()));
        tvTemp.setText(String.format("Body Temperature : %d( Taken %s)", realmVitalSign.getRespirationRate(), realmVitalSign.getMethod()));
        tvPulse.setText(String.format("Pulse Rate : %d", realmVitalSign.getPulseRate()));
    }

    private void setBloodPressureStatus(RealmVitalSign realmVitalSign) {
        String message = "";
        if (isNormal(realmVitalSign)) {
            message = "Normal blood Pressure ";
        } else if (isElevated(realmVitalSign)) {
            message = "Elevated blood pressure";
        } else if (isStage1(realmVitalSign)) {
            message = "Stage 1 high blood pressure";
        } else if (isState2(realmVitalSign)) {
            message = "Stage 2 high blood pressure";
        }

        tvBlood.setText(String.format("%s : %d/%d", message, realmVitalSign.getBloodPressureSystolic(), realmVitalSign.getBloodPressureDiastolic()));

    }

    private boolean isState2(RealmVitalSign realmVitalSign) {
        return realmVitalSign.getBloodPressureSystolic() >= 140 && realmVitalSign.getBloodPressureDiastolic() >= 90;
    }

    private boolean isNormal(RealmVitalSign realmVitalSign) {
    return realmVitalSign.getBloodPressureSystolic() <= 120 && realmVitalSign.getBloodPressureDiastolic() <= 80;
    }

    private boolean isElevated(RealmVitalSign realmVitalSign) {
        return realmVitalSign.getBloodPressureSystolic() > 120 && realmVitalSign.getBloodPressureSystolic() < 129 && realmVitalSign.getBloodPressureDiastolic() <= 80;
    }

    private boolean isStage1(RealmVitalSign realmVitalSign) {
        return realmVitalSign.getBloodPressureSystolic() >= 130 && realmVitalSign.getBloodPressureSystolic() < 129 && realmVitalSign.getBloodPressureDiastolic() > 80 && realmVitalSign.getBloodPressureDiastolic() <= 89;
    }
}
