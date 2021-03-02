package org.ole.planet.myplanet.service;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.callback.SuccessListener;
import org.ole.planet.myplanet.callback.SyncListener;
import org.ole.planet.myplanet.datamanager.Service;
import org.ole.planet.myplanet.model.Download;
import org.ole.planet.myplanet.model.MyPlanet;
import org.ole.planet.myplanet.ui.sync.LoginActivity;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.DialogUtils;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Date;

import org.ole.planet.myplanet.service.SyncManager;

import static org.ole.planet.myplanet.ui.dashboard.DashboardActivity.MESSAGE_PROGRESS;
import static org.ole.planet.myplanet.ui.sync.SyncActivity.PREFS_NAME;


public class AutoSyncService extends JobService implements SyncListener, Service.CheckVersionCallback, SuccessListener {
    SharedPreferences preferences;

    @Override
    public boolean onStartJob(JobParameters job) {
        preferences = getSharedPreferences(SyncManager.PREFS_NAME, MODE_PRIVATE);
        long lastSync = preferences.getLong("LastSync", 0);
        long currentTime = new Date().getTime();
        int syncInterval = preferences.getInt("autoSyncInterval", 60 * 60);
        if ((currentTime - lastSync) > (syncInterval * 1000)) {
            Utilities.toast(this, "Syncing started...");
            new Service(this).checkVersion(this, preferences);
        }
        return false;
    }


    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(MESSAGE_PROGRESS)) {
                Download download = intent.getParcelableExtra("download");
                if (!download.isFailed() && download.isCompleteAll()) {
                    FileUtils.installApk(AutoSyncService.this, download.getFileUrl());
                }
            }
        }
    };


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
//        startActivity(new Intent(this, LoginActivity.class)
//                .putExtra("versionInfo", info)
//                .putExtra("cancelable", cancelable)
//                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        if (Constants.showBetaFeature(Constants.KEY_AUTOUPDATE, this)) {
            DialogUtils.startDownloadUpdate(this, Utilities.getApkUpdateUrl(info.getLocalapkpath()), null);
        }
    }

    @Override
    public void onCheckingVersion() {

    }

    @Override
    public void onError(String msg, boolean blockSync) {
        if (!blockSync) {
            SyncManager.getInstance().start(this);
            UploadToShelfService.getInstance().uploadUserData(success -> new Service(MainApplication.context).healthAccess(success1 -> UploadToShelfService.getInstance().uploadHealth()));
            if (!MainApplication.isSyncRunning){
                MainApplication.isSyncRunning = true;
                UploadManager.getInstance().uploadExamResult(this);
                UploadManager.getInstance().uploadFeedback(this);
                UploadManager.getInstance().uploadAchievement();
                UploadManager.getInstance().uploadResourceActivities("");
                UploadManager.getInstance().uploadUserActivities(this);
                UploadManager.getInstance().uploadCourseActivities();
                UploadManager.getInstance().uploadSearchActivity();
                UploadManager.getInstance().uploadRating(this);
                UploadManager.getInstance().uploadResource(this);
                UploadManager.getInstance().uploadNews();
                UploadManager.getInstance().uploadTeams();
                UploadManager.getInstance().uploadTeamTask();
                UploadManager.getInstance().uploadCrashLog(this);
                UploadManager.getInstance().uploadActivities(success -> MainApplication.isSyncRunning = false);
            }
        }
    }

    @Override
    public void onSuccess(String s) {
        SharedPreferences settings = MainApplication.context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        settings.edit().putLong("lastUsageUploaded", new Date().getTime()).commit();

    }
}
