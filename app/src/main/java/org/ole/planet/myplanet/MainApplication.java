package org.ole.planet.myplanet;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.Lifetime;
import com.firebase.jobdispatcher.RetryStrategy;
import com.firebase.jobdispatcher.Trigger;

import org.ole.planet.myplanet.service.AutoSyncService;
import org.ole.planet.myplanet.utilities.NotificationUtil;
import org.ole.planet.myplanet.utilities.Utilities;

public class MainApplication extends Application implements Application.ActivityLifecycleCallbacks {
    public static FirebaseJobDispatcher dispatcher;
    public static Context context;
    SharedPreferences preferences;
    public static int syncFailedCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        dispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(this));
        context = this;
        preferences = getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE);
        if (preferences.getBoolean("autoSync", false) && preferences.contains("autoSyncInterval")) {
            dispatcher.cancelAll();
            createJob(preferences.getInt("autoSyncInterval", 15 * 60));
        } else {
            dispatcher.cancelAll();
        }
        registerActivityLifecycleCallbacks(this);
    }

    public void createJob(int sec) {
        Utilities.log("Create job");
        Job myJob = dispatcher.newJobBuilder()
                .setService(AutoSyncService.class)
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
}
