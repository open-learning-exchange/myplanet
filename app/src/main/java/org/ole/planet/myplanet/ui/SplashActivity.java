package org.ole.planet.myplanet.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity;
import org.ole.planet.myplanet.ui.sync.LoginActivity;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.FileUtils;

public class SplashActivity extends AppCompatActivity {
    RadioButton rbChild, rbNormal;
    Button getStarted;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        rbChild = findViewById(R.id.child_login);
        rbNormal = findViewById(R.id.normal_login);
        getStarted = findViewById(R.id.get_started);
        FileUtils.copyAssets(this);
        SharedPreferences settings = getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE);
        if (settings.getBoolean(Constants.KEY_LOGIN, false) && !Constants.autoSynFeature(Constants.KEY_AUTOSYNC_,getApplicationContext()) ) {
            Intent dashboard = new Intent(getApplicationContext(), DashboardActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(dashboard);
            finish();
            return;
        }
        if (settings.contains("isChild")){
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            finish();
        }
        getStarted.setOnClickListener(view -> {
            settings.edit().putBoolean("isChild", rbChild.isChecked()).commit();
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
        });
    }
}
