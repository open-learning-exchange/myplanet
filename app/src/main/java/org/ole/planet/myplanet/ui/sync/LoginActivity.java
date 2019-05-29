package org.ole.planet.myplanet.ui.sync;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SwitchCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.SyncListener;
import org.ole.planet.myplanet.datamanager.ManagerSync;
import org.ole.planet.myplanet.datamanager.Service;
import org.ole.planet.myplanet.model.MyPlanet;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.GPSService;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.team.AdapterTeam;
import org.ole.planet.myplanet.ui.viewer.WebViewActivity;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.DialogUtils;
import org.ole.planet.myplanet.utilities.LocaleHelper;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Arrays;

import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageButton;

import static org.ole.planet.myplanet.ui.dashboard.DashboardActivity.MESSAGE_PROGRESS;


public class LoginActivity extends SyncActivity implements Service.CheckVersionCallback, AdapterTeam.OnUserSelectedListener {
    EditText serverUrl;
    EditText serverPassword;
    private EditText inputName, inputPassword;
    private TextInputLayout inputLayoutName, inputLayoutPassword;
    private Button btnSignIn, btnGuestLogin;
    private ImageButton imgBtnSetting;
    private View positiveAction;
    private GifDrawable gifDrawable;
    private GifImageButton syncIcon;
    private CheckBox save, managerialLogin;
    private boolean isSync = false, isUpload = false, forceSync = false;
    String processedUrl;
    private SwitchCompat switchChildMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(settings.getBoolean("isChild", false) ? R.layout.activity_child_login : R.layout.activity_login);
        changeLogoColor();
        declareElements();
        declareMoreElements();
        showWifiDialog();
        registerReceiver();
        forceSync = getIntent().getBooleanExtra("forceSync", false);
        if (forceSync) {
            isUpload = false;
            isSync = false;
            processedUrl = Utilities.getUrl();
        }
        if (getIntent().hasExtra("versionInfo")) {
            onUpdateAvailable((MyPlanet) getIntent().getSerializableExtra("versionInfo"), getIntent().getBooleanExtra("cancelable", false));
        } else {
            new Service(this).checkVersion(this, settings);
        }
        new GPSService(this);
        setUpChildMode();
    }

    private void showWifiDialog() {
        if (getIntent().getBooleanExtra("showWifiDialog", false)) {
            DialogUtils.showWifiSettingDialog(this);
        }
    }


    public void declareElements() {
        inputLayoutName = findViewById(R.id.input_layout_name);
        inputLayoutPassword = findViewById(R.id.input_layout_password);
        imgBtnSetting = findViewById(R.id.imgBtnSetting);
        btnGuestLogin = findViewById(R.id.btn_guest_login);
        save = findViewById(R.id.save);
        managerialLogin = findViewById(R.id.manager_login);
        btnSignIn = findViewById(R.id.btn_signin); //buttons
        btnSignIn.setOnClickListener(view -> submitForm());
        findViewById(R.id.become_member).setOnClickListener(v -> {
            if (!Utilities.getUrl().isEmpty()) {
                startActivity(new Intent(this, WebViewActivity.class).putExtra("title", "Become a member")
                        .putExtra("link", Utilities.getUrl().replaceAll("/db", "") + "/eng/login/newmember"));
            } else {
                Utilities.toast(this, "Please enter server url first.");
                settingDialog();
            }
        });
        imgBtnSetting.setOnClickListener(view -> settingDialog());
        btnGuestLogin.setOnClickListener(view -> showGuestLoginDialog());
        switchChildMode = findViewById(R.id.switch_child_mode);
        switchChildMode.setChecked(settings.getBoolean("isChild", false));
        switchChildMode.setOnCheckedChangeListener((compoundButton, b) -> {
            settings.edit().putBoolean("isChild", b).commit();
            recreate();
        });
    }

    private void showGuestLoginDialog() {
        editor = settings.edit();
        View v = LayoutInflater.from(this).inflate(R.layout.alert_guest_login, null);
        TextInputEditText etUserName = v.findViewById(R.id.et_user_name);
        new AlertDialog.Builder(this).setTitle("Login As Guest")
                .setView(v)
                .setPositiveButton("Login", (dialogInterface, i) -> {
                    if (mRealm.isEmpty()) {
                        alertDialogOkay("Server not configured properly. Connect this device with Planet server");
                        return;
                    }
                    String username = etUserName.getText().toString().toLowerCase();
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
                    //   mRealm.commitTransaction();
                }).setNegativeButton("Cancel", null).show();
    }


    private void continueSync(MaterialDialog dialog) {
        processedUrl = saveConfigAndContinue(dialog);
        if (TextUtils.isEmpty(processedUrl)) return;
        isUpload = false;
        isSync = true;
        if (checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) && settings.getBoolean("firstRun", true)) {
            clearInternalStorage();
        }
        new Service(this).checkVersion(this, settings);
    }


    public void declareMoreElements() {
        syncIcon = findViewById(R.id.syncIcon);
        syncIcon.setImageResource(R.drawable.sync_icon);
        syncIcon.getScaleType();
        gifDrawable = (GifDrawable) syncIcon.getDrawable();
        gifDrawable.setSpeed(3.0f);
        gifDrawable.stop();
        syncIcon.setOnClickListener(v -> {
            gifDrawable.reset();
            isUpload = true;
            isSync = false;
            new Service(this).checkVersion(this, settings);
        });
        declareHideKeyboardElements();
        TextView txtVersion = findViewById(R.id.lblVersion);
        txtVersion.setText(getResources().getText(R.string.version) + " " + getResources().getText(R.string.app_version));
        inputName = findViewById(R.id.input_name);//editText
        inputPassword = findViewById(R.id.input_password);
        inputName.addTextChangedListener(new MyTextWatcher(inputName));
        inputPassword.addTextChangedListener(new MyTextWatcher(inputPassword));
        setUplanguageButton();
        if (settings.getBoolean("saveUsernameAndPassword", false)) {
            inputName.setText(settings.getString("loginUserName", ""));
            inputPassword.setText(settings.getString("loginUserPassword", ""));
            save.setChecked(true);
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
        SharedPreferences.Editor editor = settings.edit();
        if (!validateEditText(inputName, inputLayoutName, getString(R.string.err_msg_name))) {
            return;
        }
        if (!validateEditText(inputPassword, inputLayoutPassword, getString(R.string.err_msg_password))) {
            return;
        }
        editor.putBoolean("saveUsernameAndPassword", save.isChecked());
        if (save.isChecked()) {
            editor.putString("loginUserName", inputName.getText().toString());
            editor.putString("loginUserPassword", inputPassword.getText().toString());
        }
        boolean isLoggedIn = authenticateUser(settings, inputName.getText().toString(), inputPassword.getText().toString(), managerialLogin.isChecked());


        if (isLoggedIn) {
            Toast.makeText(getApplicationContext(), "Thank You!", Toast.LENGTH_SHORT).show();
            onLogin();
        } else if (managerialLogin.isChecked()) {
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
                    if (log){
                        Toast.makeText(getApplicationContext(), "Thank You!", Toast.LENGTH_SHORT).show();
                        onLogin();
                    }else{
                        alertDialogOkay(getString(R.string.err_msg_login));
                    }
                }

                @Override
                public void onSyncFailed(String msg) {
                    Utilities.log("On failed");
                    progressDialog.dismiss();
                }
            });
        } else {
            alertDialogOkay(getString(R.string.err_msg_login));
        }
        editor.commit();

    }

    private void managerLogin() {

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
        serverUrl = dialog.getCustomView().findViewById(R.id.input_server_url);
        serverPassword = dialog.getCustomView().findViewById(R.id.input_server_Password);
        serverUrl.setText(settings.getString("serverURL", ""));
        serverPassword.setText(settings.getString("serverPin", ""));
        serverUrl.addTextChangedListener(new MyTextWatcher(serverUrl));
        dialog.show();
        sync(dialog);
    }


    @Override
    public void onSuccess(String s) {
        if (progressDialog.isShowing() && s.contains("Crash"))
            progressDialog.dismiss();
        DialogUtils.showSnack(btnSignIn, s);
    }

    @Override
    public void onUpdateAvailable(MyPlanet info, boolean cancelable) {
        AlertDialog.Builder builder = DialogUtils.getUpdateDialog(this, info, progressDialog);
        if (cancelable) {
            builder.setNegativeButton("Update Later", (dialogInterface, i) -> {
                continueSyncProcess();
            });
        } else {
            if (!mRealm.isInTransaction()) {
                mRealm.beginTransaction();
                mRealm.deleteAll();
                mRealm.commitTransaction();
            }
        }
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
        if (!block)
            continueSyncProcess();
    }

    public void continueSyncProcess() {
        try {
            if (isSync) {
                isServerReachable(processedUrl);
            } else if (isUpload) {
                startUpload();
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

    private class MyTextWatcher implements TextWatcher {
        private View view;

        private MyTextWatcher(View view) {
            this.view = view;
        }

        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        public void onTextChanged(CharSequence s, int i, int i1, int i2) {
            if (view.getId() == R.id.input_server_url)
                positiveAction.setEnabled(s.toString().trim().length() > 0 && URLUtil.isValidUrl(s.toString()));

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mRealm != null && !mRealm.isClosed())
            mRealm.close();
    }
}
