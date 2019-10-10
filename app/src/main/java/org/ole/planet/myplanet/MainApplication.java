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
import android.text.format.DateFormat;
import android.util.Log;

import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.Lifetime;
import com.firebase.jobdispatcher.RetryStrategy;
import com.firebase.jobdispatcher.Trigger;

import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmApkLog;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.AutoSyncService;
import org.ole.planet.myplanet.service.StayOnLineService;
import org.ole.planet.myplanet.service.TaskNotificationService;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.AndroidDecrypter;
import org.ole.planet.myplanet.utilities.LocaleHelper;
import org.ole.planet.myplanet.utilities.NotificationUtil;
import org.ole.planet.myplanet.utilities.Utilities;
import org.ole.planet.myplanet.utilities.VersionUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import io.realm.Realm;

public class MainApplication extends Application implements Application.ActivityLifecycleCallbacks {
    public static FirebaseJobDispatcher dispatcher;
    public static Context context;
    SharedPreferences preferences;
    public static int syncFailedCount = 0;
    public static boolean isCollectionSwitchOn = false;
    Calendar cal_today , cal_last_Sync;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.onAttach(base, "en"));
    }

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
    public void onCreate() {
        super.onCreate();
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        builder.detectFileUriExposure();
        dispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(this));
        context = this;
        Realm.init(this);
        preferences = getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE);
        if (preferences.getBoolean("autoSync", false) && preferences.contains("autoSyncInterval")) {
            dispatcher.cancelAll();
            createJob(preferences.getInt("autoSyncInterval", 15 * 60), AutoSyncService.class);
        } else {
            dispatcher.cancelAll();
        }
        createJob(5 * 60, StayOnLineService.class);
        createJob(60, TaskNotificationService.class);
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> handleUncaughtException(e));
        registerActivityLifecycleCallbacks(this);

        checkForceSync();
        //todo
        //Delete bellow when fully implemented
        /// Test Encryption
        String key = "0102030405060708090001020304050607080900010203040506070809000102";  // 64
        String iv = "00010203040506070809000102030405"; // 32
        String data = "{\"cat\":\"zuzu\"}"; //
        try {
            Log.e("Enc ",AndroidDecrypter.encrypt(data,key,iv));
            Log.e("Decyp ",AndroidDecrypter.decrypt("1620545cbde0bd053ac9d47fd3fdfa3b",key,iv));

        } catch (Exception e) {
            e.printStackTrace();
        }
        //
    }

    private void checkForceSync() {
        cal_today = Calendar.getInstance(Locale.ENGLISH);
        cal_last_Sync = Calendar.getInstance(Locale.ENGLISH);
        cal_last_Sync.setTimeInMillis(preferences.getLong("LastSync", 0));
        cal_today.setTimeInMillis(new Date().getTime());
        Log.e("Call ",""+cal_today.getTime());
        Log.e("Old Sync ",""+cal_last_Sync.getTime());
        if(cal_today.compareTo(cal_last_Sync)>7){
            Log.e("Sync Date ","True - ");
        }else{
            Log.e("Sync Date ","Not up to 7 - ");
        }
    }

    public void createJob(int sec, Class jobClass) {
        Utilities.log("Create job");
        Job myJob = dispatcher.newJobBuilder()
                .setService(jobClass)
                .setTag("ole")
                .setRecurring(true)
                .setLifetime(Lifetime.FOREVER)
                .setTrigger(Trigger.executionWindow(0, sec))
                .setRetryStrategy(RetryStrategy.DEFAULT_LINEAR)
                .build();
        dispatcher.mustSchedule(myJob);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {

    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {

    }

    @Override
    public void onActivityPaused(Activity activity) {

    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        Utilities.log("Destroyed ");
        NotificationUtil.cancellAll(this);
    }

    private String getDateFromLong(long time) {
        Calendar cal = Calendar.getInstance(Locale.ENGLISH);
        cal.setTimeInMillis(time * 1000);
        String date = DateFormat.format("dd-MM-yyyy", cal).toString();
        return date;
    }


    public void handleUncaughtException(Throwable e) {
        e.printStackTrace();
        Utilities.log("Handle exception " + e.getMessage());
        DatabaseService service = new DatabaseService(this);
        Realm mRealm = service.getRealmInstance();
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        RealmApkLog log = mRealm.createObject(RealmApkLog.class, UUID.randomUUID().toString());
        RealmUserModel model = new UserProfileDbHandler(this).getUserModel();
        if (model != null) {
            log.setParentCode(model.getParentCode());
            log.setCreatedOn(model.getPlanetCode());
        }
        log.setTime(new Date().getTime() +"");
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
