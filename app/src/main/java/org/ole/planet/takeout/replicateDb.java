package org.ole.planet.takeout;

import android.content.SharedPreferences;
import android.util.Log;


import com.couchbase.lite.Context;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.Manager;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryEnumerator;
import com.couchbase.lite.QueryRow;
import com.couchbase.lite.android.AndroidContext;
import com.couchbase.lite.replicator.Replication;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class replicateDb {
    android.content.Context context;
    List<String> serverDbNameList = new ArrayList<>();
    public static final String PREFS_NAME = "OLE_PLANET";
    SharedPreferences settings;

    public void replicationFromCoucDb(android.content.Context context) {
        databaseList();
        this.context = context;
        try {
            for (int i = 0; i < serverDbNameList.size(); i++) {
                replicate(serverDbNameList.get(i),"pull");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void databaseList() {
        serverDbNameList.add("_global_changes");
        serverDbNameList.add("_replicator");
        serverDbNameList.add("_users");
        serverDbNameList.add("communityregistrationrequests");
        serverDbNameList.add("configurations");
        serverDbNameList.add("courses");
        serverDbNameList.add("feedback");
        serverDbNameList.add("login_activities");
        serverDbNameList.add("meetups");
        serverDbNameList.add("nations");
        serverDbNameList.add("notifications");
        serverDbNameList.add("resource_activities");
    }

    private void replicate(String databaseName,String replicateDirection) {
        // Create a manager

        Manager manager = null;
        Database database = null;
        URL url = null;
        try {
            manager = new Manager(new AndroidContext(context), Manager.DEFAULT_OPTIONS);
            database = manager.getDatabase(databaseName);
            url = new URL(settings.getString("serverURL","") + databaseName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (replicateDirection.equalsIgnoreCase("pull")) {
            Replication pull = database.createPullReplication(url);
            pull.setContinuous(true);
            pull.start();
        } else {
            Replication push = database.createPushReplication(url);
            push.setContinuous(true);
            push.start();
        }
    }

}
