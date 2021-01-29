package org.ole.planet.myplanet.ui.sync;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.SyncListener;
import org.ole.planet.myplanet.datamanager.ManagerSync;
import org.ole.planet.myplanet.datamanager.Service;
import org.ole.planet.myplanet.model.MyPlanet;
import org.ole.planet.myplanet.model.RealmCommunity;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.GPSService;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.community.HomeCommunityDialogFragment;
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment;
import org.ole.planet.myplanet.ui.team.AdapterTeam;
import org.ole.planet.myplanet.ui.userprofile.BecomeMemberActivity;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.DialogUtils;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.LocaleHelper;
import org.ole.planet.myplanet.utilities.NetworkUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.realm.Sort;
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseSequence;
import uk.co.deanwild.materialshowcaseview.ShowcaseConfig;

import static org.ole.planet.myplanet.ui.dashboard.DashboardActivity.MESSAGE_PROGRESS;


public class LoginActivity extends SyncActivity implements Service.CheckVersionCallback, AdapterTeam.OnUserSelectedListener {
    public static Calendar cal_today, cal_last_Sync;
    private EditText serverUrl, serverUrlProtocol;
    private EditText serverPassword;
    private String processedUrl;
    private RadioGroup protocol_checkin;
    private EditText inputName, inputPassword;
    private TextInputLayout inputLayoutName, inputLayoutPassword;
    private Button btnSignIn;
    private View positiveAction;
    //    private GifDrawable gifDrawable;
    //    private CheckBox managerialLogin;
    private ImageButton imgBtnSetting;
    private boolean isSync = false, forceSync = false;
    private SwitchCompat switchServerUrl;
    private SharedPreferences defaultPref;
    private Service service;
    private Spinner spnCloud;
    private TextView tvAvailableSpace;


    private void showShowCaseView() {
        ShowcaseConfig config = new ShowcaseConfig();
        config.setDelay(500);
        MaterialShowcaseSequence sequence = new MaterialShowcaseSequence(this, "1");
        sequence.setConfig(config);
        sequence.addSequenceItem(syncIcon,
                "Press the Sync Button to Sync your Planet account data with your myPlanet application data", "GOT IT");
        sequence.addSequenceItem(imgBtnSetting,
                "Press the Settings Button to access your myPlanet/Planet account server settings to properly set up your syncing process.", "GOT IT");
        sequence.start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(settings.getBoolean("isChild", false) ? R.layout.activity_child_login : R.layout.activity_login);

        // Find and show space available on the device
        tvAvailableSpace = findViewById(R.id.tv_available_space);
        tvAvailableSpace.setText(FileUtils.getAvailableOverTotalMemoryFormattedString());

        changeLogoColor();
        service = new Service(this);
        defaultPref = PreferenceManager.getDefaultSharedPreferences(this);
        declareElements();
        declareMoreElements();
        showWifiDialog();
        registerReceiver();

        forceSync = getIntent().getBooleanExtra("forceSync", false);
        processedUrl = Utilities.getUrl();
        if (forceSync) {
            isSync = false;
        }

        if (getIntent().hasExtra("versionInfo")) {
            onUpdateAvailable((MyPlanet) getIntent().getSerializableExtra("versionInfo"), getIntent().getBooleanExtra("cancelable", false));
        } else {
            service.checkVersion(this, settings);
        }
        checkUsagesPermission();
        new GPSService(this);
        setUpChildMode();
        lblLastSyncDate = findViewById(R.id.lblLastSyncDate);
        forceSyncTrigger();
        Button btnOpenCommunity = findViewById(R.id.open_community);
        if (!Utilities.getUrl().isEmpty()) {
            btnOpenCommunity.setVisibility(View.VISIBLE);
            btnOpenCommunity.setOnClickListener(v -> {
                new HomeCommunityDialogFragment().show(getSupportFragmentManager(), "");
            });
            new HomeCommunityDialogFragment().show(getSupportFragmentManager(), "");
        } else {
            btnOpenCommunity.setVisibility(View.GONE);
        }
        findViewById(R.id.btn_feedback).setOnClickListener(view -> new FeedbackFragment().show(getSupportFragmentManager(), ""));

        if (settings.getBoolean("firstRun", true))
            showShowCaseView();
    }

    private boolean forceSyncTrigger() {
        lblLastSyncDate.setText(getString(R.string.last_sync) + Utilities.getRelativeTime(settings.getLong(getString(R.string.last_syncs), 0)) + " >>");
        if (Constants.autoSynFeature(Constants.KEY_AUTOSYNC_, getApplicationContext()) && Constants.autoSynFeature(Constants.KEY_AUTOSYNC_WEEKLY, getApplicationContext())) {
            return checkForceSync(7);
        } else if (Constants.autoSynFeature(Constants.KEY_AUTOSYNC_, getApplicationContext()) && Constants.autoSynFeature(Constants.KEY_AUTOSYNC_MONTHLY, getApplicationContext())) {
            return checkForceSync(30);
        }
        return false;
    }


    private void showWifiDialog() {
        if (getIntent().getBooleanExtra("showWifiDialog", false)) {
            DialogUtils.showWifiSettingDialog(this);
        }
    }

    public boolean checkForceSync(int maxDays) {
        cal_today = Calendar.getInstance(Locale.ENGLISH);
        cal_last_Sync = Calendar.getInstance(Locale.ENGLISH);
        cal_last_Sync.setTimeInMillis(settings.getLong("LastSync", 0));
        cal_today.setTimeInMillis(new Date().getTime());
        long msDiff = Calendar.getInstance().getTimeInMillis() - cal_last_Sync.getTimeInMillis();
        long daysDiff = TimeUnit.MILLISECONDS.toDays(msDiff);
        if (daysDiff >= maxDays) {
            Log.e("Sync Date ", "Expired - ");
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
            alertDialogBuilder.setMessage("It has been more than " + (daysDiff - 1) + " days since you last synced this device." +
                    "\nConnect it to the server over wifi and sync it to reactivate this tablet");
            alertDialogBuilder.setPositiveButton("Okay", (arg0, arg1) -> Toast.makeText(getApplicationContext(), "Connect to the server over WiFi and sync your device to continue", Toast.LENGTH_LONG).show());
            alertDialogBuilder.show();
            return true;
        } else {
            Log.e("Sync Date ", "Not up to  - " + maxDays);
            return false;
        }
    }


    public void declareElements() {
        if (!defaultPref.contains("beta_addImageToMessage")) {
            defaultPref.edit().putBoolean("beta_addImageToMessage", true).commit();
        }
        inputLayoutName = findViewById(R.id.input_layout_name);
        inputLayoutPassword = findViewById(R.id.input_layout_password);
        imgBtnSetting = findViewById(R.id.imgBtnSetting);
        Button btnGuestLogin = findViewById(R.id.btn_guest_login);
        TextView customDeviceName = findViewById(R.id.customDeviceName);
        customDeviceName.setText(getCustomDeviceName());
        btnSignIn = findViewById(R.id.btn_signin); //buttons
        btnSignIn.setOnClickListener(view -> submitForm());
        if (!settings.contains("serverProtocol"))
            settings.edit().putString("serverProtocol", "http://").commit();
        findViewById(R.id.become_member).setOnClickListener(v -> becomeAMember());
        imgBtnSetting.setOnClickListener(view -> settingDialog());
        btnGuestLogin.setOnClickListener(view -> showGuestLoginDialog());
        SwitchCompat switchChildMode = findViewById(R.id.switch_child_mode);
        switchChildMode.setChecked(settings.getBoolean("isChild", false));
        switchChildMode.setOnCheckedChangeListener((compoundButton, b) -> {
            settings.edit().putBoolean("isChild", b).commit();
            recreate();
        });

    }

    private void becomeAMember() {
        if (!Utilities.getUrl().isEmpty()) {
            startActivity(new Intent(this, BecomeMemberActivity.class));
        } else {
            Utilities.toast(this, "Please enter server url first.");
            settingDialog();
        }
    }

    private void showGuestLoginDialog() {
        editor = settings.edit();
        View v = LayoutInflater.from(this).inflate(R.layout.alert_guest_login, null);
        TextInputEditText etUserName = v.findViewById(R.id.et_user_name);
        new AlertDialog.Builder(this).setTitle("Login As Guest")
                .setView(v)
                .setPositiveButton("Login", (dialogInterface, i) -> {
                    if (mRealm.isEmpty()) {
                        alertDialogOkay("This device not configured properly. Please check and sync.");
                        return;
                    }
                    String username = etUserName.getText().toString().toLowerCase().trim();
                    if (username.isEmpty()) {
                        Utilities.toast(this, "Username cannot be empty");
                        return;
                    }
                    RealmUserModel model = mRealm.copyFromRealm(RealmUserModel.createGuestUser(username, mRealm, settings));
                    if (model == null) {
                        Utilities.toast(this, "Unable to login");
                    } else {
                        saveUserInfoPref(settings, "", model);
                        onLogin();
                    }
                }).setNegativeButton("Cancel", null).show();
    }


    private void continueSync(MaterialDialog dialog) {
        processedUrl = saveConfigAndContinue(dialog);
        if (TextUtils.isEmpty(processedUrl)) return;
        isSync = true;
        if (checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) && settings.getBoolean("firstRun", true)) {
            clearInternalStorage();
        }

        new Service(this).isPlanetAvailable(new Service.PlanetAvailableListener() {
            @Override
            public void isAvailable() {
                new Service(LoginActivity.this).checkVersion(LoginActivity.this, settings);
            }

            @Override
            public void notAvailable() {
                DialogUtils.showAlert(LoginActivity.this, "Error", "Planet server not reachable.");
            }
        });
    }


    public void declareMoreElements() {
        syncIcon = findViewById(R.id.syncIcon);
        syncIcon.setImageDrawable(getResources().getDrawable(R.drawable.login_file_upload_animation));
        syncIcon.getScaleType();
        syncIconDrawable = (AnimationDrawable) syncIcon.getDrawable();
        syncIcon.setOnClickListener(v -> {
            syncIconDrawable.start();
            isSync = false;
            forceSync = true;
            service.checkVersion(this, settings);
        });
        declareHideKeyboardElements();
        TextView txtVersion = findViewById(R.id.lblVersion);
        txtVersion.setText(getResources().getText(R.string.version) + " " + getResources().getText(R.string.app_version));
        inputName = findViewById(R.id.input_name);//editText
        inputPassword = findViewById(R.id.input_password);
        inputName.addTextChangedListener(new MyTextWatcher(inputName));
        inputPassword.addTextChangedListener(new MyTextWatcher(inputPassword));
        setUplanguageButton();
        if (defaultPref.getBoolean("saveUsernameAndPassword", false)) {
            inputName.setText(settings.getString(getString(R.string.login_user), ""));
            inputPassword.setText(settings.getString(getString(R.string.login_password), ""));
        }
        if (NetworkUtils.isNetworkConnected()) {
            service.syncPlanetServers(mRealm, success -> Utilities.toast(LoginActivity.this, success));
        }
    }

    private void setUplanguageButton() {
        Button btnlang = findViewById(R.id.btn_lang);
        String[] languageKey = getResources().getStringArray(R.array.language_keys);
        String[] languages = getResources().getStringArray(R.array.language);
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        int index = Arrays.asList(languageKey).indexOf(pref.getString("app_language", "en"));
        btnlang.setText(languages[index]);
        btnlang.setOnClickListener(view -> {
            new AlertDialog.Builder(this)
                    .setTitle("Select Language")
                    .setSingleChoiceItems(getResources().getStringArray(R.array.language), index, null)
                    .setPositiveButton("OK", (dialog, whichButton) -> {
                        dialog.dismiss();
                        int selectedPosition = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                        String lang = languageKey[selectedPosition];
                        LocaleHelper.setLocale(LoginActivity.this, lang);
                        recreate();
                    }).setNegativeButton("Cancel", null)
                    .show();
        });
    }


    /**
     * Form  Validation
     */
    private void submitForm() {
        if (forceSyncTrigger()) {
            return;
        }
        SharedPreferences.Editor editor = settings.edit();
        if (!validateEditText(inputName, inputLayoutName, getString(R.string.err_msg_name))) {
            return;
        }
        if (!validateEditText(inputPassword, inputLayoutPassword, getString(R.string.err_msg_password))) {
            return;
        }
        editor.putString("loginUserName", inputName.getText().toString());
        editor.putString("loginUserPassword", inputPassword.getText().toString());
        boolean isLoggedIn = authenticateUser(settings, inputName.getText().toString(), inputPassword.getText().toString(), false);
        if (isLoggedIn) {
            Toast.makeText(getApplicationContext(), "Thank You!", Toast.LENGTH_SHORT).show();
            onLogin();
        } else {
            ManagerSync.getInstance().login(inputName.getText().toString(), inputPassword.getText().toString(), new SyncListener() {
                @Override
                public void onSyncStarted() {
                    progressDialog.setMessage("Please wait....");
                    progressDialog.show();
                }

                @Override
                public void onSyncComplete() {
                    progressDialog.dismiss();
                    Utilities.log("on complete");
                    boolean log = authenticateUser(settings, inputName.getText().toString(), inputPassword.getText().toString(), true);
                    if (log) {
                        Toast.makeText(getApplicationContext(), "Thank You!", Toast.LENGTH_SHORT).show();
                        onLogin();
                    } else {
                        alertDialogOkay(getString(R.string.err_msg_login));
                    }
                    syncIconDrawable.stop();
                    syncIconDrawable.selectDrawable(0);
                }

                @Override
                public void onSyncFailed(String msg) {
                    Utilities.toast(LoginActivity.this, msg);
                    progressDialog.dismiss();
                    syncIconDrawable.stop();
                    syncIconDrawable.selectDrawable(0);
                }

            });
        }
        editor.commit();
    }


    private void onLogin() {
        UserProfileDbHandler handler = new UserProfileDbHandler(this);
        handler.onLogin();
        handler.onDestory();
        editor.putBoolean(Constants.KEY_LOGIN, true).commit();
        openDashboard();
    }


    public void settingDialog() {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(LoginActivity.this);
        builder.title(R.string.action_settings).customView(R.layout.dialog_server_url_, true)
                .positiveText(R.string.btn_sync).negativeText(R.string.btn_sync_cancel).neutralText(R.string.btn_sync_save)
                .onPositive((dialog, which) -> continueSync(dialog)).onNeutral((dialog, which) -> saveConfigAndContinue(dialog));

        MaterialDialog dialog = builder.build();
        positiveAction = dialog.getActionButton(DialogAction.POSITIVE);
        spnCloud = dialog.getCustomView().findViewById(R.id.spn_cloud);
        List<RealmCommunity> communities = mRealm.where(RealmCommunity.class).sort("weight", Sort.ASCENDING).findAll();
        spnCloud.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, communities));
        spnCloud.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                onChangeServerUrl();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
        protocol_checkin = dialog.getCustomView().findViewById(R.id.radio_protocol);
        serverUrl = dialog.getCustomView().findViewById(R.id.input_server_url);
        serverPassword = dialog.getCustomView().findViewById(R.id.input_server_Password);
        switchServerUrl = dialog.getCustomView().findViewById(R.id.switch_server_url);
        serverUrlProtocol = dialog.getCustomView().findViewById(R.id.input_server_url_protocol);
        switchServerUrl.setOnCheckedChangeListener((compoundButton, b) -> {
            settings.edit().putBoolean("switchCloudUrl", b).commit();
            spnCloud.setVisibility(b ? View.VISIBLE : View.GONE);
            setUrlAndPin(switchServerUrl.isChecked());
        });
        serverUrl.addTextChangedListener(new MyTextWatcher(serverUrl));
        EditText customDeviceName = dialog.getCustomView().findViewById(R.id.deviceName);
        customDeviceName.setText(getCustomDeviceName());
//        setUrlAndPin(switchServerUrl.isChecked());
        switchServerUrl.setChecked(settings.getBoolean("switchCloudUrl", false));
        setUrlAndPin(settings.getBoolean("switchCloudUrl", false));
        protocol_semantics();
        dialog.show();
        sync(dialog);
    }

    private void onChangeServerUrl() {
        RealmCommunity selected = (RealmCommunity) spnCloud.getSelectedItem();
        Utilities.log((selected == null) + " selected ");
        if (selected == null){
            return;
        }
        serverUrl.setText(selected.getLocalDomain());
        protocol_checkin.check(R.id.radio_https);
        settings.getString("serverProtocol", "https://");
        serverPassword.setText(selected.getWeight() == 0 ? "0660" : "");
        serverPassword.setEnabled(selected.getWeight() != 0);
    }

    private void setUrlAndPin(boolean checked) {
        if (checked) {
            onChangeServerUrl();
        } else {
            serverUrl.setText(removeProtocol(settings.getString("serverURL", "")));
            serverPassword.setText(settings.getString("serverPin", ""));
            protocol_checkin.check(TextUtils.equals(settings.getString("serverProtocol", ""), "http://") ? R.id.radio_http : R.id.radio_https);
            serverUrlProtocol.setText(settings.getString("serverProtocol", ""));
        }
        serverUrl.setEnabled(!checked);
        serverPassword.clearFocus();
        serverUrl.clearFocus();
//        serverPassword.setEnabled(!checked);
        protocol_checkin.setEnabled(!checked);
    }

    private void protocol_semantics() {
        settings.edit().putString("serverProtocol", serverUrlProtocol.getText().toString()).commit();
        protocol_checkin.setOnCheckedChangeListener((radioGroup, i) -> {
            switch (i) {
                case R.id.radio_http:
                    serverUrlProtocol.setText(getString(R.string.http_protocol));
                    break;

                case R.id.radio_https:
                    serverUrlProtocol.setText(getString(R.string.https_protocol));
                    break;
            }
            settings.edit().putString("serverProtocol", serverUrlProtocol.getText().toString()).commit();
        });

    }

    @Override
    public void onSuccess(String s) {
        Utilities.log("Sync completed ");
        if (progressDialog.isShowing() && s.contains("Crash"))
            progressDialog.dismiss();
        DialogUtils.showSnack(btnSignIn, s);
        settings.edit().putLong("lastUsageUploaded", new Date().getTime()).commit();

        // Update last sync text
        lblLastSyncDate.setText(getString(R.string.last_sync) + Utilities.getRelativeTime(new Date().getTime()) + " >>");
    }

    @Override
    public void onUpdateAvailable(MyPlanet info, boolean cancelable) {
        AlertDialog.Builder builder = DialogUtils.getUpdateDialog(this, info, progressDialog);
        if (cancelable || NetworkUtils.getCustomDeviceName(this).endsWith("###")) {
            builder.setNegativeButton("Update Later", (dialogInterface, i) -> continueSyncProcess());
        } else {
            mRealm.executeTransactionAsync(realm -> realm.deleteAll());
        }
        builder.setCancelable(cancelable);
        builder.show();
    }

    @Override
    public void onCheckingVersion() {
        progressDialog.setMessage("Checking version....");
        progressDialog.show();
    }

    private void registerReceiver() {
        LocalBroadcastManager bManager = LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MESSAGE_PROGRESS);
        bManager.registerReceiver(broadcastReceiver, intentFilter);
    }


    @Override
    public void onError(String msg, boolean block) {
        Utilities.toast(this, msg);
        if (msg.startsWith("Config")) {
            settingDialog();
        }
        progressDialog.dismiss();
        if (!block) continueSyncProcess();
        else {
            syncIconDrawable.stop();
            syncIconDrawable.selectDrawable(0);
        }
    }

    public void continueSyncProcess() {
        Utilities.log("Upload : Continue sync process");
        try {
            if (isSync) {
                isServerReachable(processedUrl);
            } else if (forceSync) {
                isServerReachable(processedUrl);
                startUpload();
            }
        } catch (Exception e) {
        }
    }

    @Override
    public void onSelectedUser(RealmUserModel userModel) {
        View v = getLayoutInflater().inflate(R.layout.layout_child_login, null);
        EditText et = v.findViewById(R.id.et_child_password);
        new AlertDialog.Builder(this).setView(v).setTitle("Please enter your password").setPositiveButton(R.string.login, (dialogInterface, i) -> {
            String password = et.getText().toString();
            if (authenticateUser(settings, userModel.getName(), password, false)) {
                Toast.makeText(getApplicationContext(), "Thank You!", Toast.LENGTH_SHORT).show();
                onLogin();
            } else {
                alertDialogOkay(getString(R.string.err_msg_login));
            }
        }).setNegativeButton(R.string.cancel, null).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mRealm != null && !mRealm.isClosed())
            mRealm.close();
    }

    public String removeProtocol(String url) {
        url = url.replaceFirst(getString(R.string.https_protocol), "");
        url = url.replaceFirst(getString(R.string.http_protocol), "");
        return url;
    }

    private class MyTextWatcher implements TextWatcher {
        private View view;

        private MyTextWatcher(View view) {
            this.view = view;
        }

        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        public void onTextChanged(CharSequence s, int i, int i1, int i2) {

            String protocol = serverUrlProtocol == null ? settings.getString("serverProtocol", "http://") : serverUrlProtocol.getText().toString();
            if (view.getId() == R.id.input_server_url)
                positiveAction.setEnabled(s.toString().trim().length() > 0 && URLUtil.isValidUrl(protocol + s.toString()));
        }

        public void afterTextChanged(Editable editable) {
            switch (view.getId()) {
                case R.id.input_name:
                    validateEditText(inputName, inputLayoutName, getString(R.string.err_msg_name));
                    break;
                case R.id.input_password:
                    validateEditText(inputPassword, inputLayoutPassword, getString(R.string.err_msg_password));
                    break;
                default:
                    break;
            }
        }
    }

    public String getCustomDeviceName() {
        return settings.getString("customDeviceName", NetworkUtils.getDeviceName());
    }
}
