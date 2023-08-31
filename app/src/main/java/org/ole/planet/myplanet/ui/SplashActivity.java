package org.ole.planet.myplanet.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import org.ole.planet.myplanet.databinding.ActivitySplashBinding;
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity;
import org.ole.planet.myplanet.ui.sync.LoginActivity;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.FileUtils;

public class SplashActivity extends AppCompatActivity {
    private ActivitySplashBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Find and show space available on the device
        binding.tvAvailableSpace.setText(FileUtils.getAvailableOverTotalMemoryFormattedString());

        FileUtils.copyAssets(this);
        SharedPreferences settings = getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE);
        if (settings.getBoolean(Constants.KEY_LOGIN, false) && !Constants.autoSynFeature(Constants.KEY_AUTOSYNC_, getApplicationContext())) {
            Intent dashboard = new Intent(getApplicationContext(), DashboardActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(dashboard);
            finish();
            return;
        }
        if (settings.contains("isChild")) {
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            finish();
        }
        binding.getStarted.setOnClickListener(view -> {
            settings.edit().putBoolean("isChild", binding.childLogin.isChecked()).commit();
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
        });
    }
}
