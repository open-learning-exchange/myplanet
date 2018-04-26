package org.ole.planet.takeout;

import android.content.Context;
import android.util.Log;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseConfiguration;

import java.util.ArrayList;
import java.util.List;

public class dbSetup {
    Context context;
    List<String> databaseList = new ArrayList<>();

    public void dbSetup(Context context){
        this.context = context;
        initDatabases();


    }
    public void initDatabases(){
        populateDatabaseList();
        DatabaseConfiguration config = new DatabaseConfiguration(context);
        try {
            System.out.println("==> For Loop Example.");
            for (int i = 0; i < databaseList.size(); i++) {
                Database db = new Database(databaseList.get(i), config);
                Log.e("Database ", "Created Name_ : "+db.getName());
            }

        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
    }
    public void populateDatabaseList(){
        databaseList.add("communityregistrationrequests");
        databaseList.add("courses");
        databaseList.add("feedback");
        databaseList.add("login_activities");
        databaseList.add("meetups");
        databaseList.add("nations");
        databaseList.add("notifications");
        databaseList.add("ratings");
        databaseList.add("resource_activities");
        databaseList.add("resources");
        databaseList.add("shelf");
    }
}