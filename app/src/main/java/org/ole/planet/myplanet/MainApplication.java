package org.ole.planet.myplanet;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.Settings;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.Lifetime;
import com.firebase.jobdispatcher.RetryStrategy;
import com.firebase.jobdispatcher.Trigger;
import com.jakewharton.threetenabp.AndroidThreeTen;

import org.ole.planet.myplanet.callback.TeamPageListener;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmApkLog;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.AutoSyncWorker;
import org.ole.planet.myplanet.service.StayOnlineWorker;
import org.ole.planet.myplanet.service.TaskNotificationWorker;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.LocaleHelper;
import org.ole.planet.myplanet.utilities.NotificationUtil;
import org.ole.planet.myplanet.utilities.Utilities;
import org.ole.planet.myplanet.utilities.VersionUtils;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.realm.Realm;

public class MainApplication extends Application {
    private static final String AUTO_SYNC_WORK_TAG = "autoSyncWork";
    private static final String STAY_ONLINE_WORK_TAG = "stayOnlineWork";
    private static final String TASK_NOTIFICATION_WORK_TAG = "taskNotificationWork";

    public static Context context;
    public static SharedPreferences preferences;
    public static int syncFailedCount = 0;
    public static boolean isCollectionSwitchOn = false;
    public static boolean showDownload = false;
    public static boolean isSyncRunning = false;
    public static boolean showHealthDialog = true;
    public static TeamPageListener listener;

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
        preferences = getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE);

        // Initialize libraries and settings
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        builder.detectFileUriExposure();
        AndroidThreeTen.init(this);

        // Set up auto-sync using WorkManager
        if (preferences.getBoolean("autoSync", false) && preferences.contains("autoSyncInterval")) {
            int syncInterval = preferences.getInt("autoSyncInterval", 60 * 60);
            scheduleAutoSyncWork(syncInterval);
        } else {
            cancelAutoSyncWork();
        }

        // Set up other periodic works using WorkManager
        scheduleStayOnlineWork(5 * 60);
        scheduleTaskNotificationWork(60);

    }

    private void scheduleAutoSyncWork(int syncInterval) {
        PeriodicWorkRequest autoSyncWork = new PeriodicWorkRequest.Builder(
                AutoSyncWorker.class,
                syncInterval,
                TimeUnit.SECONDS
        ).build();

        WorkManager workManager = WorkManager.getInstance(this);
        workManager.enqueueUniquePeriodicWork(
                AUTO_SYNC_WORK_TAG,
                ExistingPeriodicWorkPolicy.REPLACE,
                autoSyncWork
        );
    }

    private void cancelAutoSyncWork() {
        WorkManager workManager = WorkManager.getInstance(this);
        workManager.cancelUniqueWork(AUTO_SYNC_WORK_TAG);
    }

    private void scheduleStayOnlineWork(int interval) {
        PeriodicWorkRequest stayOnlineWork = new PeriodicWorkRequest.Builder(
                StayOnlineWorker.class,
                interval,
                TimeUnit.SECONDS
        ).build();

        WorkManager workManager = WorkManager.getInstance(this);
        workManager.enqueueUniquePeriodicWork(
                STAY_ONLINE_WORK_TAG,
                ExistingPeriodicWorkPolicy.REPLACE,
                stayOnlineWork
        );
    }

    private void scheduleTaskNotificationWork(int interval) {
        PeriodicWorkRequest taskNotificationWork = new PeriodicWorkRequest.Builder(
                TaskNotificationWorker.class,
                interval,
                TimeUnit.SECONDS
        ).build();

        WorkManager workManager = WorkManager.getInstance(this);
        workManager.enqueueUniquePeriodicWork(
                TASK_NOTIFICATION_WORK_TAG,
                ExistingPeriodicWorkPolicy.REPLACE,
                taskNotificationWork
        );
    }

    public static String getAndroidId() {
        try {
            return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "0";
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.onAttach(base, "en"));
        Utilities.setContext(base);
    }

    public void createJob(int sec, Class jobClass) {
        FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(this));
        Job myJob = dispatcher.newJobBuilder().setService(jobClass).setTag("ole").setRecurring(true).setLifetime(Lifetime.FOREVER).setTrigger(Trigger.executionWindow(0, sec)).setRetryStrategy(RetryStrategy.DEFAULT_LINEAR).build();
        dispatcher.mustSchedule(myJob);
    }

    public void onActivityCreated(Activity activity, Bundle bundle) {

    }

    public void onActivityStarted(Activity activity) {

    }

    public void onActivityResumed(Activity activity) {

    }

    public void onActivityPaused(Activity activity) {

    }

    public void onActivityStopped(Activity activity) {
    }

    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {

    }

    public void onActivityDestroyed(Activity activity) {
        NotificationUtil.cancellAll(this);
    }

    public void handleUncaughtException(Throwable e) {
        e.printStackTrace();
        Utilities.log("Handle exception " + e.getMessage());
        DatabaseService service = new DatabaseService(this);
        Realm mRealm = service.getRealmInstance();
        if (!mRealm.isInTransaction()) mRealm.beginTransaction();
        RealmApkLog log = mRealm.createObject(RealmApkLog.class, UUID.randomUUID().toString());
        RealmUserModel model = new UserProfileDbHandler(this).getUserModel();
        if (model != null) {
            log.setParentCode(model.getParentCode());
            log.setCreatedOn(model.getPlanetCode());
        }
        log.setTime(new Date().getTime() + "");
        log.setPage("");
        log.setVersion(VersionUtils.getVersionName(this));
        log.setType(RealmApkLog.ERROR_TYPE_CRASH);
        log.setError(e);
        mRealm.commitTransaction();
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(homeIntent);
    }
}