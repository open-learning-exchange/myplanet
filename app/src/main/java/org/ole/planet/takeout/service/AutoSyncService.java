package org.ole.planet.takeout.service;

import android.content.SharedPreferences;

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;

import org.ole.planet.takeout.callback.SyncListener;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.Date;


public class AutoSyncService extends JobService implements SyncListener {
    SharedPreferences preferences;

    @Override
    public boolean onStartJob(JobParameters job) {
        preferences = getSharedPreferences(SyncManager.PREFS_NAME, MODE_PRIVATE);
        long lastSync = preferences.getLong("LastSync", 0);
        long currentTime = new Date().getTime();
        int syncInterval = preferences.getInt("autoSyncInterval", 15 * 60);
        if ((currentTime - lastSync) > (syncInterval * 1000)) {
            Utilities.toast(this, "Syncing started...");
            SyncManager.getInstance().start(this);
        }
        Utilities.log("Diff  " + (currentTime - lastSync) + " " + (syncInterval * 1000));
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
}
