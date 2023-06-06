package org.ole.planet.myplanet;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.Settings;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.jakewharton.threetenabp.AndroidThreeTen;

import org.ole.planet.myplanet.callback.TeamPageListener;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmApkLog;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.AutoSyncWorker;
import org.ole.planet.myplanet.utilities.LocaleHelper;
import org.ole.planet.myplanet.utilities.NotificationUtil;
import org.ole.planet.myplanet.utilities.StayOnLineWorker;
import org.ole.planet.myplanet.utilities.TaskNotificationWorker;
import org.ole.planet.myplanet.utilities.Utilities;
import org.ole.planet.myplanet.utilities.VersionUtils;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.realm.Realm;


public class MainApplication extends Application implements Application.ActivityLifecycleCallbacks {
    public static WorkManager workManager;
    public static Context context;
    public static SharedPreferences preferences;
    public static int syncFailedCount = 0;
    public static boolean isCollectionSwitchOn = false;
    public static boolean showDownload = false;
    public static boolean isSyncRunning = false;
    public static boolean showHealthDialog = true;
    public static TeamPageListener listener;

    @SuppressLint("HardwareIds")
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
    }

    @Override
    public void onCreate() {
        super.onCreate();
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        builder.detectFileUriExposure();
        context = this;
        AndroidThreeTen.init(this);

        Realm.init(this);
        preferences = getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE);

        if (preferences.getBoolean("autoSync", false) && preferences.contains("autoSyncInterval")) {
            if (workManager != null) {
                workManager.cancelUniqueWork("autoSync");
            }
            createJob(preferences.getInt("autoSyncInterval", 60 * 60), AutoSyncWorker.class);
        } else {
            if (workManager != null) {
                workManager.cancelUniqueWork("autoSync");
            }
        }

        createJob(5 * 60, StayOnLineWorker.class);
        createJob(60, TaskNotificationWorker.class);
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> handleUncaughtException(e));
        registerActivityLifecycleCallbacks(this);
    }

    public void createWork(int sec, Class workerClass, String tag) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        WorkRequest workRequest = new PeriodicWorkRequest.Builder(workerClass, sec, TimeUnit.SECONDS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build();

        if (workManager != null) {
            workManager.enqueueUniquePeriodicWork(tag, ExistingPeriodicWorkPolicy.KEEP, (PeriodicWorkRequest) workRequest);
        }
    }

    public void createJob(int intervalSeconds, Class jobClass) {
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                jobClass,
                intervalSeconds,
                TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ole",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest);
    }


    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {}

    @Override
    public void onActivityStarted(Activity activity) {}

    @Override
    public void onActivityResumed(Activity activity) {}

    @Override
    public void onActivityPaused(Activity activity) {}

    @Override
    public void onActivityStopped(Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {}

    @Override
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
//        System.exit(2);
    }
}
