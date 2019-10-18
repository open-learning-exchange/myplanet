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

import java.util.Date;
import java.util.UUID;

import io.realm.Realm;

public class MainApplication extends Application implements Application.ActivityLifecycleCallbacks {
    public static FirebaseJobDispatcher dispatcher;
    public static Context context;
    public static SharedPreferences preferences;
    public static int syncFailedCount = 0;
    public static boolean isCollectionSwitchOn = false;

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

        //todo
        //Delete bellow when fully implemented
        /// Test Encryption
        String key = "1b81c6e8a90adcfb29beeb255b6a2b803118c024f859c4d1761266f8ac78fbe3";  // 64
        String iv = "b846da3e330e968e4f9fcb9f6eebf1f1"; // 32
        String data = "{\"cat\":\"zuzu\"}"; //
        try {
            Log.e("Decyp ", AndroidDecrypter.decrypt("437f0def3817958a4d48672f8e54e51f0077a59f7cc1e60a8bab432763d039551bc852189d2e2c175ea9eaed117ecd25daf12e61497656d527a96efa05b14029fd7bd68e8a38693fc7ee9266ce9ff30cf706438c644276afc13e3c221afad7901b239e418c8335739c6fc47a35991d4d48cc28bfd287d2ebe63b9420e6094b18d7b4437960ae5eb216785f96c23e9a3780db40ffd62f234a46a31747308ea3a41304830a5714fabc2f40a6de5eb2bb735a93d1b0097651503ae1594524234d64d6dd777302a0d89f78e012366285a65a0c5786e6b555909a8741cb077522c0788f32c627ffbc2294abff9af99d16c524afea4420cf456f251d4e57f3b4197408e75bacdea29626a25b42c964e9d14a6f6805eeca263092b57e0534f448c0135bea84b8e49c9fdbb8f55d0956a5fe232065b22b47218cf1b2f8ea75ab8b5c53a53fef1ba7837c1981c6a3a399c6802c321f40fa71ce61f7e48945ea22a411f701f2f15f525b39227f92935034e73fada8b989d12816a1143854c10253a30c4e2a0a0ee1cf1ba2184e0fe8e496340eb049c988f6bea2a298a43dc8111a611186867f40cfa188faad669ffb4c6d2bb7b763990c38f3bce3e49cfe979f389e20d5c39e30019ca76670de7ccb4bbb7a369f29dff73374e9b8eb9980ed6fc657c010dddad50db4b2343f4007bf306821cc450307c9c613457506c32359b56663333dc2cb46de022b0e47058f0ddffc6b2cb73fece48674640856dff35c7af8d3e45d91035c0c2a248c42e7f7e20ef76ab4bb4bac2e3f9a4b4ee56b9dcd94a51c6c075a9df7a7bf228ebcfa2c985fe28ea34ff402c550521eb939fb890065e0509063364dfe787f21c89987445bbfd5982165a4879509c45b90cecccf554fe1f7c91c3d26a6dd67947d94e64ddfd3e3fc5f845a5256c6fa2bf8e0164ad3ace14ca5106c955c1b77da9b04c715c0231c273d424f1f02473b24a46f77122ede9f55fac1afbfe6c1b3ff7b30ebca821696eef61796aa6afb6c1e1dad3bf648a61e78ebd974fa5c470c910cb57670aa5d618aad777a948a0daf72a9e1eec4218c90e96c79fe59d594d09438a3a49b9b71fa77d9bbe7b39a5ee2a1c4eb69a8d2a10f88c1063aaf451c5ff27ebec48df81645269e7aa4bb1e550d5f0970e9f765f1f3662aba6c45b65db980db96e50c017572ab339026", key, iv));
        } catch (Exception e) {
            e.printStackTrace();
        }
        //
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
