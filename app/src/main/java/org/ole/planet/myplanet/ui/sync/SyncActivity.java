package org.ole.planet.myplanet.ui.sync;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.SyncListener;
import org.ole.planet.myplanet.datamanager.ApiClient;
import org.ole.planet.myplanet.datamanager.ApiInterface;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.datamanager.Service;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.SyncManager;
import org.ole.planet.myplanet.ui.team.AdapterTeam;
import org.ole.planet.myplanet.utilities.AndroidDecrypter;
import org.ole.planet.myplanet.utilities.DialogUtils;
import org.ole.planet.myplanet.utilities.NotificationUtil;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public abstract class SyncActivity extends ProcessUserDataActivity implements SyncListener {
    public static final String PREFS_NAME = "OLE_PLANET";
    public TextView syncDate, lblLastSyncDate;
    public TextView intervalLabel, tvNodata;
    public Spinner spinner;
    public Switch syncSwitch;
    int convertedDate;
    boolean connectionResult;
    Realm mRealm;
    SharedPreferences.Editor editor;
    int[] syncTimeInteval = {60 * 60, 3 * 60 * 60};
    ImageView syncIcon;
    AnimationDrawable syncIconDrawable;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        editor = settings.edit();
        mRealm = new DatabaseService(this).getRealmInstance();
        requestPermission();
        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
    }

    public void clearInternalStorage() {
        File myDir = new File(Utilities.SD_PATH);
        if (myDir.isDirectory()) {
            String[] children = myDir.list();
            for (int i = 0; i < children.length; i++) {
                new File(myDir, children[i]).delete();
            }
        }
        settings.edit().putBoolean("firstRun", false).commit();
    }


    public void sync(MaterialDialog dialog) {
        spinner = (Spinner) dialog.findViewById(R.id.intervalDropper);
        syncSwitch = (Switch) dialog.findViewById(R.id.syncSwitch);
        intervalLabel = (TextView) dialog.findViewById(R.id.intervalLabel);
        syncSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> setSpinnerVisibility(isChecked));
        syncSwitch.setChecked(settings.getBoolean("autoSync", true));
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


    public void setUpChildMode() {
        if (!settings.getBoolean("isChild", false))
            return;
        RecyclerView rvTeams = findViewById(R.id.rv_teams);
        TextView tvNodata = findViewById(R.id.tv_nodata);

        List<RealmMyTeam> teams = mRealm.where(RealmMyTeam.class).isEmpty("teamId").findAll();
        rvTeams.setLayoutManager(new GridLayoutManager(this, 3));
        rvTeams.setAdapter(new AdapterTeam(this, teams, mRealm));
        if (teams.size() > 0) {
            tvNodata.setVisibility(View.GONE);
        } else {
            tvNodata.setText(R.string.no_team_available);
            tvNodata.setVisibility(View.VISIBLE);
        }
    }


    public boolean isServerReachable(String processedUrl) throws Exception {
        progressDialog.setMessage("Connecting to server....");
        progressDialog.show();
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        Utilities.log(processedUrl + "/_all_dbs");
        apiInterface.isPlanetAvailable(processedUrl + "/_all_dbs").enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    progressDialog.dismiss();
                    String ss = response.body().string();
                    List<String> myList = Arrays.asList(ss.split(","));
                    Utilities.log("List size " + ss);
                    if (myList.size() < 8) {
                        alertDialogOkay("Check the server address again. What i connected to wasn't the Planet Server");
                    } else {
                        startSync();
                    }
                } catch (Exception e) {
                    alertDialogOkay("Device couldn't reach server. Check and try again");
                    progressDialog.dismiss();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                alertDialogOkay("Device couldn't reach server. Check and try again");
                if (mRealm != null)
                    mRealm.close();
                progressDialog.dismiss();
            }
        });
//        Fuel.get(processedUrl + "/_all_dbs").responseString(new Handler<String>() {
//            @Override
//            public void success(Request request, Response response, String s) {
//
//            }
//
//            @Override
//            public void failure(Request request, Response response, FuelError fuelError) {
//
//            }
//        });
        return connectionResult;
    }

    public void declareHideKeyboardElements() {
        findViewById(R.id.constraintLayout).setOnTouchListener((view, ev) -> {
            hideKeyboard(view);
            return false;
        });
    }

    private void dateCheck(MaterialDialog dialog) {
        // Check if the user never synced
        syncDate = (TextView) dialog.findViewById(R.id.lastDateSynced);
        syncDate.setText(getString(R.string.last_sync_date) + convertDate());
        syncDropdownAdd();
    }


    // Converts OS date to human date
    public String convertDate() {
        // Context goes here
        long lastSynced = settings.getLong("LastSync", 0);
        if (lastSynced == 0) {
            return " Never Synced";
        }
        return Utilities.getRelativeTime(lastSynced); // <=== modify this when implementing this method
    }

    // Create items in the spinner
    public void syncDropdownAdd() {
        List<String> list = new ArrayList<>();
//        list.add("15 Minutes");
//        list.add("30 Minutes");
        list.add("1 Hour");
        list.add("3 Hours");
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, list);
        spinnerArrayAdapter.setDropDownViewResource(R.layout.spinner_item);
        spinner.setAdapter(spinnerArrayAdapter);
    }

    public void saveSyncInfoToPreference() {
        editor.putBoolean("autoSync", syncSwitch.isChecked());
        editor.putInt("autoSyncInterval", syncTimeInteval[spinner.getSelectedItemPosition()]);
        editor.putInt("autoSyncPosition", spinner.getSelectedItemPosition());
        editor.commit();
    }

    public boolean authenticateUser(SharedPreferences settings, String username, String password, boolean isManagerMode) {
        this.settings = settings;
        if (mRealm.isEmpty()) {
            alertDialogOkay("Server not configured properly. Connect this device with Planet server");
            return false;
        } else {
            return checkName(username, password, isManagerMode);
        }
    }

    @Nullable
    private Boolean checkName(String username, String password, boolean isManagerMode) {
        try {
            AndroidDecrypter decrypt = new AndroidDecrypter();
            RealmResults<RealmUserModel> db_users = mRealm.where(RealmUserModel.class)
                    .equalTo("name", username)
                    .findAll();
            for (RealmUserModel user : db_users) {
                if (user.get_id().isEmpty()) {
                    if (username.equals(user.getName()) && password.equals(user.getPassword())) {
                        saveUserInfoPref(settings, password, user);
                        return true;
                    }
                } else {
                    if (decrypt.AndroidDecrypter(username, password, user.getDerived_key(), user.getSalt())) {
                        if (isManagerMode && !user.isManager())
                            return false;
                        saveUserInfoPref(settings, password, user);
                        return true;
                    }
                }
            }
        } catch (Exception err) {
            err.printStackTrace();
            return false;
        }
        return false;
    }


    public void startSync() {
        Utilities.log("Start sync");

        SyncManager.getInstance().start(SyncActivity.this);

    }

    public String saveConfigAndContinue(MaterialDialog dialog) {
        dialog.dismiss();
        saveSyncInfoToPreference();
        String processedUrl = "";
        String protocol = ((EditText) dialog.getCustomView().findViewById(R.id.input_server_url_protocol)).getText().toString();
        String url = ((EditText) dialog.getCustomView().findViewById(R.id.input_server_url)).getText().toString();
        String pin = ((EditText) dialog.getCustomView().findViewById(R.id.input_server_Password)).getText().toString();
        settings.edit().putString("customDeviceName", ((EditText) dialog.getCustomView().findViewById(R.id.deviceName)).getText().toString()).commit();
        url = protocol + url;
        if (isUrlValid(url))
            processedUrl = setUrlParts(url, pin, this);
        return processedUrl;
    }


    @Override
    public void onSyncStarted() {
        progressDialog.setMessage("Syncing data, Please wait...");
        progressDialog.show();
    }


    @Override
    public void onSyncFailed(final String s) {
        syncIconDrawable = (AnimationDrawable) syncIcon.getDrawable();
        syncIconDrawable.stop();
        syncIconDrawable.selectDrawable(0);
        syncIcon.invalidateDrawable(syncIconDrawable);

        runOnUiThread(() -> {
            DialogUtils.showAlert(SyncActivity.this, "Sync Failed", s);
            DialogUtils.showWifiSettingDialog(SyncActivity.this);
        });
    }

    @Override
    public void onSyncComplete() {
        progressDialog.dismiss();
        runOnUiThread(() -> {
            syncIconDrawable = (AnimationDrawable) syncIcon.getDrawable();
            syncIconDrawable.stop();
            syncIconDrawable.selectDrawable(0);
            syncIcon.invalidateDrawable(syncIconDrawable);
        });
        DialogUtils.showSnack(findViewById(android.R.id.content), "Sync Completed");

        if (settings.getBoolean("isChild", false)) {
            runOnUiThread(() -> setUpChildMode());
        }

        NotificationUtil.cancellAll(this);
    }

}
