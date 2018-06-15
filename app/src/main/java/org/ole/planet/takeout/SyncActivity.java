package org.ole.planet.takeout;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.provider.SyncStateContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.kittinunf.fuel.android.core.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.lightcouch.CouchDbClient;
import org.lightcouch.CouchDbClientAndroid;
import org.lightcouch.CouchDbProperties;
import org.lightcouch.Document;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmResults;
import io.realm.Sort;

abstract class SyncActivity extends AppCompatActivity {
    private TextView syncDate;
    private TextView intervalLabel;
    private Spinner spinner;
    private Switch syncSwitch;
    int convertedDate;
    public static final String PREFS_NAME = "OLE_PLANET";
    SharedPreferences settings;
    Realm mRealm;
    Context context;
    CouchDbProperties properties;


    public void sync(MaterialDialog dialog) {
        // Check Autosync switch (Toggler)
        syncSwitch = (Switch) dialog.findViewById(R.id.syncSwitch);
        intervalLabel = (TextView) dialog.findViewById(R.id.intervalLabel);
        syncSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Log.e("MD: ", "Autosync is On");
                    intervalLabel.setVisibility(View.VISIBLE);
                    spinner.setVisibility(View.VISIBLE);
                } else {
                    Log.e("MD: ", "Autosync is Off");
                    spinner.setVisibility(View.GONE);
                    intervalLabel.setVisibility(View.GONE);
                }
            }
        });
        dateCheck(dialog);
    }

    private void dateCheck(MaterialDialog dialog) {
        convertedDate = convertDate();
        // Check if the user never synced
        if (convertedDate == 0) {
            syncDate = (TextView) dialog.findViewById(R.id.lastDateSynced);
            syncDate.setText("Last Sync Date: Never");
        } else {
            syncDate = (TextView) dialog.findViewById(R.id.lastDateSynced);
            syncDate.setText("Last Sync Date: " + convertedDate);
        }

        // Init spinner dropdown items
        spinner = (Spinner) dialog.findViewById(R.id.intervalDropper);
        syncDropdownAdd();
    }

    // Converts OS date to human date
    private int convertDate() {
        // Context goes here
        return 0; // <=== modify this when implementing this method
    }

    // Create items in the spinner
    public void syncDropdownAdd() {
        List<String> list = new ArrayList<>();
        list.add("15 Minutes");
        list.add("30 Minutes");
        list.add("1 Hour");
        list.add("3 Hours");
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<String>(this, R.layout.spinner_item, list);
        spinnerArrayAdapter.setDropDownViewResource(R.layout.spinner_item);
        spinner.setAdapter(spinnerArrayAdapter);
    }


    public void syncDatabase(final String databaseName) {
        Thread td = new Thread(new Runnable() {
            public void run() {
                realmConfig(databaseName);
                CouchDbClientAndroid dbClient = new CouchDbClientAndroid(properties);
                List<Document> allDocs = dbClient.view("_all_docs").includeDocs(true).query(Document.class);
                for (int i = 0; i < allDocs.size(); i++) {
                    Document doc = allDocs.get(i);
                    processUserDoc(dbClient, doc);
                }
            }
        });
        td.start();
    }

    private void processUserDoc(CouchDbClientAndroid dbClient, Document doc) {
        try {
            if (!doc.getId().equalsIgnoreCase("_design/_auth")) {
                JsonObject jsonDoc = dbClient.find(JsonObject.class, doc.getId());
                mRealm.beginTransaction();
                populateUsersTable(jsonDoc);
                Log.e("Realm", " STRING " + jsonDoc.get("_id"));
                mRealm.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void populateUsersTable(JsonObject jsonDoc) {
        try {
            realm_UserModel user = mRealm.createObject(realm_UserModel.class, jsonDoc.get("_id").getAsString());
            user.set_rev(jsonDoc.get("_rev").getAsString());
            user.setName(jsonDoc.get("name").getAsString());
            //JsonElement userRoles = jsonDoc.get("roles");
            //user.setRoles(userRolesAsJsonArray.getAsString());
            user.setRoles("");
            if ((jsonDoc.get("isUserAdmin").getAsString().equalsIgnoreCase("true"))) {
                user.setUserAdmin(true);
            } else {
                user.setUserAdmin(false);
            }
            user.setJoinDate(jsonDoc.get("joinDate").getAsInt());
            user.setFirstName(jsonDoc.get("firstName").getAsString());
            user.setLastName(jsonDoc.get("lastName").getAsString());
            user.setMiddleName(jsonDoc.get("middleName").getAsString());
            user.setEmail(jsonDoc.get("email").getAsString());
            user.setPhoneNumber(jsonDoc.get("phoneNumber").getAsString());
            user.setPassword_scheme(jsonDoc.get("password_scheme").getAsString());
            user.setIterations(jsonDoc.get("iterations").getAsString());
            user.setDerived_key(jsonDoc.get("derived_key").getAsString());
            user.setSalt(jsonDoc.get("salt").getAsString());
            mRealm.commitTransaction();
        } catch (Exception err) {
            err.printStackTrace();
        }
    }


    public void alertDialogOkay(String Message) {
        AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
        builder1.setMessage(Message);
        builder1.setCancelable(true);
        builder1.setNegativeButton("Okay",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert11 = builder1.create();
        alert11.show();
    }

    public boolean authenticateUser(String username, String password, Context context) {
        this.context = context;
        AndroidDecrypter decrypt = new AndroidDecrypter();
        realmConfig("_users");
        if (mRealm.isEmpty()) {
            alertDialogOkay("Server not configured properly. Connect this device with Planet server");
            mRealm.close();
            return false;
        } else {
            return checkName(username, password, decrypt);
        }
    }

    @Nullable
    private Boolean checkName(String username, String password, AndroidDecrypter decrypt) {
        try {
            RealmResults<realm_UserModel> db_users = mRealm.where(realm_UserModel.class)
                    .equalTo("name", username)
                    .findAll();
            mRealm.beginTransaction();
            for (realm_UserModel user : db_users) {
                if (decrypt.AndroidDecrypter(username, password, user.getDerived_key(), user.getSalt())) {
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putString("name", user.getName());
                    editor.putString("password", password);
                    editor.putString("firstName", user.getFirstName());
                    editor.putString("lastName", user.getLastName());
                    editor.putString("middleName", user.getMiddleName());
                    editor.putBoolean("isUserAdmin", user.getUserAdmin());
                    editor.commit();
                    syncDatabase("_users");
                    mRealm.close();
                    return true;
                }
            }
        } catch (Exception err) {
            err.printStackTrace();
            mRealm.close();
            return false;
        }
        mRealm.close();
        return  false;
    }

    public void realmConfig(String dbName) {
        Realm.init(context);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .name(Realm.DEFAULT_REALM_NAME)
                .deleteRealmIfMigrationNeeded()
                .schemaVersion(4)
                .build();
        Realm.setDefaultConfiguration(config);
        mRealm = Realm.getInstance(config);
        properties = new CouchDbProperties()
                .setDbName(dbName)
                .setCreateDbIfNotExist(false)
                .setProtocol(settings.getString("url_Scheme", "http"))
                .setHost(settings.getString("url_Host", "192.168.2.1"))
                .setPort(settings.getInt("url_Port", 3000))
                .setUsername(settings.getString("url_user", ""))
                .setPassword(settings.getString("url_pwd", ""))
                .setMaxConnections(100)
                .setConnectionTimeout(0);

    }


    public void setUrlParts(String url, String password, Context context) {
        this.context = context;
        URI uri = URI.create(url);
        String url_Scheme = uri.getScheme();
        String url_Host = uri.getHost();
        int url_Port = uri.getPort();
        String url_user = null, url_pwd = null;
        if (url.contains("@")) {
            String[] userinfo = uri.getUserInfo().split(":");
            url_user = userinfo[0];
            url_pwd = userinfo[1];
        } else {
            url_user = "";
            url_pwd = password;
        }
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("serverURL", url);
        editor.putString("url_Scheme", url_Scheme);
        editor.putString("url_Host", url_Host);
        editor.putInt("url_Port", url_Port);
        editor.putString("url_user", url_user);
        editor.putString("url_pwd", url_pwd);
        editor.commit();
        syncDatabase("_users");
    }


}
