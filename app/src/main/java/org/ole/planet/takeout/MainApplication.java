package org.ole.planet.takeout;

import android.app.Application;
import android.content.Context;
import android.os.StrictMode;

public class MainApplication extends Application {
    public static Context context;
    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
    }
}
