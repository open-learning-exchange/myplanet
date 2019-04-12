package org.ole.planet.myplanet.service;

import android.content.Intent;
import android.content.SharedPreferences;

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.callback.SyncListener;
import org.ole.planet.myplanet.datamanager.Service;
import org.ole.planet.myplanet.model.MyPlanet;
import org.ole.planet.myplanet.ui.sync.LoginActivity;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Date;


public class AutoSyncService extends JobService implements SyncListener, Service.CheckVersionCallback {
    SharedPreferences preferences;

    @Override
    public boolean onStartJob(JobParameters job) {
        preferences = getSharedPreferences(SyncManager.PREFS_NAME, MODE_PRIVATE);
        long lastSync = preferences.getLong("LastSync", 0);
        long currentTime = new Date().getTime();
        int syncInterval = preferences.getInt("autoSyncInterval", 15 * 60);
        if ((currentTime - lastSync) > (syncInterval * 1000)) {
            Utilities.toast(this, "Syncing started...");
            new Service(this).checkVersion(this, preferences);
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters job) {
        return false;
    }

    @Override
    public void onSyncStarted() {
        Utilities.log("Sync started " + new Date());
    }

    @Override
    public void onSyncComplete() {
        Utilities.log("Sync completed");
    }

    @Override
    public void onSyncFailed(String msg) {
        if (MainApplication.syncFailedCount > 3) {
            startActivity(new Intent(this, LoginActivity.class).putExtra("showWifiDialog", true)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    @Override
    public void onUpdateAvailable(MyPlanet info, boolean cancelable) {
        startActivity(new Intent(this, LoginActivity.class)
                .putExtra("versionInfo", info)
                .putExtra("cancelable", cancelable)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @Override
    public void onCheckingVersion() {

    }

    @Override
    public void onError(String msg, boolean blockSync) {
        if (!blockSync)
            SyncManager.getInstance().start(this);
    }
}
