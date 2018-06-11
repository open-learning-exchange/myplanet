package org.ole.planet.takeout;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.provider.SyncStateContract;
import android.support.annotation.NonNull;
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

    // Server feedback dialog
    public void  feedbackDialog(){
        MaterialDialog dialog = new MaterialDialog.Builder(this).title(R.string.title_sync_settings)
                .customView(R.layout.dialog_sync_feedback, true)
                .positiveText(R.string.btn_sync).negativeText(R.string.btn_sync_cancel).neutralText(R.string.btn_sync_save)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(MaterialDialog dialog, DialogAction which) {
                        Toast.makeText(SyncActivity.this, "Syncing now...", Toast.LENGTH_SHORT).show();
                    }
                })
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(MaterialDialog dialog, DialogAction which) {
                        Log.e("MD: ", "Clicked Negative (Cancel)");
                    }
                })
                .onNeutral(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        Toast.makeText(SyncActivity.this, "Saving sync settings...", Toast.LENGTH_SHORT).show();
                    }
                })
                .build();
        sync(dialog);
        dialog.show();
    }

    private void sync(MaterialDialog dialog) {
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
        if (convertedDate == 0){
            syncDate = (TextView) dialog.findViewById(R.id.lastDateSynced);
            syncDate.setText("Last Sync Date: Never");
        }
        else {
            syncDate = (TextView) dialog.findViewById(R.id.lastDateSynced);
            syncDate.setText("Last Sync Date: " + convertedDate);
        }

        // Init spinner dropdown items
        spinner = (Spinner) dialog.findViewById(R.id.intervalDropper);
        syncDropdownAdd();
    }

    // Converts OS date to human date
    private int convertDate(){
        // Context goes here
        return 0; // <=== modify this when implementing this method
    }

    // Create items in the spinner
    public void syncDropdownAdd(){
        List<String> list = new ArrayList<>();
        list.add("15 Minutes");
        list.add("30 Minutes");
        list.add("1 Hour");
        list.add("3 Hours");
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<String>(this,R.layout.spinner_item,list);
        spinnerArrayAdapter.setDropDownViewResource(R.layout.spinner_item);
        spinner.setAdapter(spinnerArrayAdapter);
    }
    public void setUrlParts(String url, String password, Context context){
        this.context  = context;
        URI uri = URI.create(url);
        String url_Scheme = uri.getScheme();
        String url_Host = uri.getHost();
        int url_Port = uri.getPort();
        String url_user = null, url_pwd = null;
        if (url.contains("@")) {
            String[] userinfo = uri.getUserInfo().split(":");
            url_user = userinfo[0];
            url_pwd = userinfo[1];
        }else{
            url_user="";
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
    public void syncDatabase(final String databaseName){
        /// mRealm = Realm.getDefaultInstance();
        Thread td = new Thread(new Runnable() {
            public void run() {

                Realm.init(context);
                RealmConfiguration config = new RealmConfiguration.Builder()
                        .name(Realm.DEFAULT_REALM_NAME)
                        .deleteRealmIfMigrationNeeded()
                        .schemaVersion(4)
                        .build();
                Realm.setDefaultConfiguration(config);
                mRealm = Realm.getInstance(config);

                CouchDbProperties properties = new CouchDbProperties()
                        .setDbName(databaseName)
                        .setCreateDbIfNotExist(false)
                        .setProtocol(settings.getString("url_Scheme","http"))
                        .setHost(settings.getString("url_Host","192.168.2.1"))
                        .setPort(settings.getInt("url_Port",3000))
                        .setUsername(settings.getString("url_user",""))
                        .setPassword(settings.getString("url_pwd",""))
                        .setMaxConnections(100)
                        .setConnectionTimeout(0);

                CouchDbClientAndroid dbClient = new CouchDbClientAndroid(properties);
                List<Document> allDocs = dbClient.view("_all_docs").includeDocs(true).query(Document.class);
                for (int i = 0; i < allDocs.size(); i++){
                    Document doc = allDocs.get(i);
                    try {
                        if(!doc.getId().equalsIgnoreCase("_design/_auth")) {
                            JsonObject jsonDoc = dbClient.find(JsonObject.class, doc.getId());
                            Object value = jsonDoc.get("_id");
                            if (value == JSONObject.NULL) {
                                // Handle NULL
                                Log.e("Realm", " Null "+jsonDoc.get("_id"));
                            } else if (value instanceof JSONObject) {
                                // Handle JSONObject
                                Log.e("Realm", " JSON OBJECT "+jsonDoc.get("_id"));
                            } else {
                                // Handle String
                                populateUsersTable(jsonDoc);
                                //putUserDataInRealm(jsonDoc);
                                Log.e("Realm", " STRING " + jsonDoc.get("_id"));
                            }
                        }
                    } catch (Exception e) {
                        Log.e("Realm", "it isn't a json object: = " + e.toString());
                        e.printStackTrace();
                    }
                }
            }
        });
        td.start();
    }
    public void populateUsersTable(JsonObject jsonDoc){
        try {
            mRealm.beginTransaction();
            realm_UserModel user = mRealm.createObject(realm_UserModel.class, jsonDoc.get("_id").getAsString());
            user.set_rev(jsonDoc.get("_rev").getAsString());
            user.setName(jsonDoc.get("name").getAsString());
            JsonElement userRoles = jsonDoc.get("roles");
            JsonArray userRolesAsJsonArray = userRoles.getAsJsonArray();
            //user.setRoles(userRolesAsJsonArray.getAsString());
            user.setRoles("");
            if (jsonDoc.get("isUserAdmin").getAsString().equalsIgnoreCase("true")) {
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
            //mRealm.commitTransaction();
            Log.e("RealmDB", " item id " + jsonDoc.get("_id"));
            /*RealmResults<realm_UserModel> result = mRealm.where(realm_UserModel.class)
                    .beginGroup()
                    .contains("_id", "leomaxi")
                    .endGroup()
                    .findAll();

            Log.e("RealmDB", " DB result " + result);
            */
        }catch(Exception err){
            err.printStackTrace();
        }
    }
    private void putUserDataInRealm(ArrayList<realm_UserModel> resultObj) {
        mRealm.beginTransaction();
        for (realm_UserModel member : resultObj) {
            realm_UserModel user = new realm_UserModel();
            user.set_rev(member.get_rev());
            user.setName(member.getName());
            //JsonElement userRoles = jsonDoc.get("roles");
            //JsonArray userRolesAsJsonArray = userRoles.getAsJsonArray();
            //user.setRoles(userRolesAsJsonArray.getAsString());
            user.setRoles("");
            if (member.getUserAdmin().toString().equalsIgnoreCase("true")) {
                user.setUserAdmin(true);
            } else {
                user.setUserAdmin(false);
            }
            user.setJoinDate(member.getJoinDate());
            user.setFirstName(member.getFirstName());
            user.setLastName(member.getLastName());
            user.setMiddleName(member.getMiddleName());
            user.setEmail(member.getEmail());
            user.setPhoneNumber(member.getPhoneNumber());
            user.setPassword_scheme(member.getPassword_scheme());
            user.setIterations(member.getIterations());
            user.setDerived_key(member.getDerived_key());
            user.setSalt(member.getSalt());
            mRealm.insertOrUpdate(user);
        }
        mRealm.commitTransaction();
    }

}
