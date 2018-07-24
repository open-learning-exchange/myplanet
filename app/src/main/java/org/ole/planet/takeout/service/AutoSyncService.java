package org.ole.planet.takeout.service;

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;

import org.ole.planet.takeout.SyncActivity;
import org.ole.planet.takeout.utilities.Utilities;

public class AutoSyncService extends JobService {
    @Override
    public boolean onStartJob(JobParameters job) {
        Utilities.toast(this, "Syncing started...");
        SyncManager.getInstance().start();
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters job) {
        return false;
    }
}
