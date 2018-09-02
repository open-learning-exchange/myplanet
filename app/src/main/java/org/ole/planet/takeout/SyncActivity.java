package org.ole.planet.takeout;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;

import org.lightcouch.CouchDbProperties;
import org.ole.planet.takeout.Data.realm_UserModel;
import org.ole.planet.takeout.callback.SyncListener;
import org.ole.planet.takeout.datamanager.DatabaseService;
import org.ole.planet.takeout.service.SyncManager;
import org.ole.planet.takeout.service.UploadManager;
import org.ole.planet.takeout.utilities.NotificationUtil;
import org.ole.planet.takeout.utilities.Utilities;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;

public abstract class SyncActivity extends ProcessUserData implements SyncListener {
    public static final String PREFS_NAME = "OLE_PLANET";
    public TextView syncDate;
    public TextView intervalLabel;
    public Spinner spinner;
    public Switch syncSwitch;
    int convertedDate;
    SharedPreferences settings;
    Realm mRealm;
    Context context;
    CouchDbProperties properties;
    MaterialDialog progress_dialog;
    SharedPreferences.Editor editor;
    int[] syncTimeInteval = {10 * 60,15 * 60, 30 * 60, 60 * 60, 3 * 60 * 60};

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        editor = settings.edit();
    }

    protected void hideKeyboard(View view) {
        InputMethodManager in = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        in.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
    }

    public void sync(MaterialDialog dialog) {
        spinner = (Spinner) dialog.findViewById(R.id.intervalDropper);
        syncSwitch = (Switch) dialog.findViewById(R.id.syncSwitch);
        intervalLabel = (TextView) dialog.findViewById(R.id.intervalLabel);
        syncSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setSpinnerVisibility(isChecked);
            }
        });
        syncSwitch.setChecked(settings.getBoolean("autoSync", false));
        dateCheck(dialog);
    }

    private void setSpinnerVisibility(boolean isChecked) {
        if (isChecked) {
            intervalLabel.setVisibility(View.VISIBLE);
            spinner.setVisibility(View.VISIBLE);
        } else {
            spinner.setVisibility(View.GONE);
            intervalLabel.setVisibility(View.GONE);
        }
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
        list.add("10 Minutes");
        list.add("15 Minutes");
        list.add("30 Minutes");
        list.add("1 Hour");
        list.add("3 Hours");
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, list);
        spinnerArrayAdapter.setDropDownViewResource(R.layout.spinner_item);
        spinner.setAdapter(spinnerArrayAdapter);
        spinner.setSelection(settings.getInt("autoSyncPosition", 0));

    }

    public void saveSyncInfoToPreference() {
        editor.putBoolean("autoSync", syncSwitch.isChecked());
        editor.putInt("autoSyncInterval", syncTimeInteval[spinner.getSelectedItemPosition()]);
        editor.putInt("autoSyncPosition", spinner.getSelectedItemPosition());
        editor.commit();
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

    public boolean authenticateUser(SharedPreferences settings, String username, String password, Context context) {
        this.settings = settings;
        this.context = context;
        AndroidDecrypter decrypt = new AndroidDecrypter();
        mRealm = new DatabaseService(context).getRealmInstance();
        ;
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
            if (!mRealm.isInTransaction())
                mRealm.beginTransaction();
            for (realm_UserModel user : db_users) {
                if (decrypt.AndroidDecrypter(username, password, user.getDerived_key(), user.getSalt())) {
                    saveUserInfoPref(settings, password, user);
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
        return false;
    }

    public String setUrlParts(String url, String password, Context context) {
        this.context = context;
        URI uri = URI.create(url);
        String couchdbURL;
        String url_user = null, url_pwd = null;
        if (url.contains("@")) {
            String[] userinfo = uri.getUserInfo().split(":");
            url_user = userinfo[0];
            url_pwd = userinfo[1];
            couchdbURL = url;
        } else {
            url_user = "satellite";
            url_pwd = password;
            couchdbURL = uri.getScheme() + "://" + url_user + ":" + url_pwd + "@" + uri.getHost() + ":" + uri.getPort();
        }
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("serverURL", url);
        editor.putString("couchdbURL", couchdbURL);
        editor.putString("url_Scheme", uri.getScheme());
        editor.putString("url_Host", uri.getHost());
        editor.putInt("url_Port", uri.getPort());
        editor.putString("url_user", url_user);
        editor.putString("url_pwd", url_pwd);
        editor.commit();
        return couchdbURL;
    }

    public void startSync() {
        SyncManager.getInstance().start(this);
    }

    @Override
    public void onSyncStarted() {
        progress_dialog = new MaterialDialog.Builder(this)
                .title("Syncing")
                .content("Please wait")
                .progress(true, 0)
                .show();
    }

    @Override
    public void onSyncComplete() {
        progress_dialog.dismiss();
        NotificationUtil.cancellAll(this);
    }

}
