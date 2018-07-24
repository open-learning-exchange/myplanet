package org.ole.planet.takeout;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.firebase.jobdispatcher.Constraint;
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.Lifetime;
import com.firebase.jobdispatcher.RetryStrategy;
import com.firebase.jobdispatcher.Trigger;

import org.ole.planet.takeout.service.AutoSyncService;
import org.ole.planet.takeout.utilities.Utilities;

public class MainApplication extends Application {
    public static FirebaseJobDispatcher dispatcher;
    public static Context context;
    SharedPreferences preferences;

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
}
