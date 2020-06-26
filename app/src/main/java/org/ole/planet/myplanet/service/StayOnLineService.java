package org.ole.planet.myplanet.service;


import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;

import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.NetworkUtils;

public class StayOnLineService extends JobService {

    @Override
    public boolean onStartJob(JobParameters job) {
        if (Constants.showBetaFeature(Constants.KEY_SYNC, this)) {
            if (NetworkUtils.isWifiConnected())
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("SHOW_WIFI_ALERT"));
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters job) {
        return false;
    }
}
