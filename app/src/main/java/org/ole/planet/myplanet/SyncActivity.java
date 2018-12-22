package org.ole.planet.myplanet;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.URLUtil;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.github.kittinunf.fuel.Fuel;
import com.github.kittinunf.fuel.core.FuelError;
import com.github.kittinunf.fuel.core.Handler;
import com.github.kittinunf.fuel.core.Request;
import com.github.kittinunf.fuel.core.Response;

import org.ole.planet.myplanet.Data.realm_UserModel;
import org.ole.planet.myplanet.callback.SuccessListener;
import org.ole.planet.myplanet.callback.SyncListener;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.service.SyncManager;
import org.ole.planet.myplanet.service.UploadManager;
import org.ole.planet.myplanet.utilities.DialogUtils;
import org.ole.planet.myplanet.utilities.NotificationUtil;
import org.ole.planet.myplanet.utilities.Utilities;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.realm.Realm;
import io.realm.RealmResults;

public abstract class SyncActivity extends ProcessUserData implements SyncListener {
    public static final String PREFS_NAME = "OLE_PLANET";
    public TextView syncDate;
    public TextView intervalLabel;
    public Spinner spinner;
    public Switch syncSwitch;
    int convertedDate;
    boolean connectionResult;
    Realm mRealm;
    Context context;
    SharedPreferences.Editor editor;
    int[] syncTimeInteval = {10 * 60, 15 * 60, 30 * 60, 60 * 60, 3 * 60 * 60};
    ProgressDialog progressDialog;
    private View constraintLayout;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        editor = settings.edit();
        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
    }


    public void sync(MaterialDialog dialog) {
        spinner = (Spinner) dialog.findViewById(R.id.intervalDropper);
        syncSwitch = (Switch) dialog.findViewById(R.id.syncSwitch);
        intervalLabel = (TextView) dialog.findViewById(R.id.intervalLabel);
        syncSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> setSpinnerVisibility(isChecked));
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


    public boolean isServerReachable(String processedUrl) throws Exception {
        progressDialog.setMessage("Connecting to server....");
        progressDialog.show();
        Utilities.log("Url " + processedUrl);
        Fuel.get(processedUrl + "/_all_dbs").responseString(new Handler<String>() {
            @Override
            public void success(Request request, Response response, String s) {
                try {
                    progressDialog.dismiss();
                    List<String> myList = Arrays.asList(s.split(","));
                    if (myList.size() < 8) {
                        alertDialogOkay("Check the server address again. What i connected to wasn't the Planet Server");
                    } else {
                        startSync();
                    }
                } catch (Exception e) {
                }
            }

            @Override
            public void failure(Request request, Response response, FuelError fuelError) {
                alertDialogOkay("Device couldn't reach server. Check and try again");
                if (mRealm != null)
                    mRealm.close();
                progressDialog.dismiss();
            }
        });
        return connectionResult;
    }

    public void declareHideKeyboardElements() {
        constraintLayout = findViewById(R.id.constraintLayout);
        constraintLayout.setOnTouchListener((view, ev) -> {
            hideKeyboard(view);
            return false;
        });
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



    public void startSync() {
        SyncManager.getInstance().start(this);
    }

    public String saveConfigAndContinue(MaterialDialog dialog) {
        dialog.dismiss();
        saveSyncInfoToPreference();
        String processedUrl = "";
        String url = ((EditText) dialog.getCustomView().findViewById(R.id.input_server_url)).getText().toString();
        String pin = ((EditText) dialog.getCustomView().findViewById(R.id.input_server_Password)).getText().toString();
        if (isUrlValid(url))
            processedUrl = setUrlParts(url, pin, context);
        return processedUrl;
    }


    @Override
    public void onSyncStarted() {
        progressDialog.setMessage("Syncing data, Please wait...");
        progressDialog.show();
    }


    @Override
    public void onSyncFailed(final String s) {
        runOnUiThread(() -> {
            DialogUtils.showAlert(SyncActivity.this, "Sync Failed", s);
            DialogUtils.showWifiSettingDialog(SyncActivity.this);
        });
    }

    @Override
    public void onSyncComplete() {
        progressDialog.dismiss();
        NotificationUtil.cancellAll(this);
    }

}
