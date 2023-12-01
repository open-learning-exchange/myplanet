package org.ole.planet.myplanet.ui.sync;

import static org.ole.planet.myplanet.MainApplication.context;
import static org.ole.planet.myplanet.ui.dashboard.DashboardActivity.MESSAGE_PROGRESS;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.AnimationDrawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.URLUtil;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.material.textfield.TextInputLayout;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.SyncListener;
import org.ole.planet.myplanet.databinding.AlertGuestLoginBinding;
import org.ole.planet.myplanet.databinding.DialogServerUrlBinding;
import org.ole.planet.myplanet.databinding.LayoutChildLoginBinding;
import org.ole.planet.myplanet.datamanager.ApiClient;
import org.ole.planet.myplanet.datamanager.ApiInterface;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.datamanager.ManagerSync;
import org.ole.planet.myplanet.datamanager.Service;
import org.ole.planet.myplanet.model.MyPlanet;
import org.ole.planet.myplanet.model.RealmCommunity;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.model.User;
import org.ole.planet.myplanet.service.SyncManager;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.team.AdapterTeam;
import org.ole.planet.myplanet.ui.userprofile.BecomeMemberActivity;
import org.ole.planet.myplanet.utilities.AndroidDecrypter;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.DialogUtils;
import org.ole.planet.myplanet.utilities.LocaleHelper;
import org.ole.planet.myplanet.utilities.NetworkUtils;
import org.ole.planet.myplanet.utilities.NotificationUtil;
import org.ole.planet.myplanet.utilities.SharedPrefManager;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public abstract class SyncActivity extends ProcessUserDataActivity implements SyncListener, Service.CheckVersionCallback, AdapterTeam.OnUserSelectedListener {
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
    public static Calendar cal_today, cal_last_Sync;
    EditText inputName, inputPassword, serverUrl, serverUrlProtocol,serverPassword;
    TextInputLayout inputLayoutName, inputLayoutPassword;
    SharedPrefManager prefData;
    private UserProfileDbHandler profileDbHandler;
    Spinner spnCloud;
    RadioGroup protocol_checkin;
    ArrayList<String> teamList = new ArrayList<>();
    ArrayAdapter<String> teamAdapter;
    String selectedTeamId = null;
    View positiveAction;
    String processedUrl;
    boolean isSync = false, forceSync = false;
    Button btnSignIn, becomeMember, btnGuestLogin, btnLang, openCommunity, btnFeedback;
    TextView customDeviceName, lblVersion, tvAvailableSpace;
    SharedPreferences defaultPref;
    ImageButton imgBtnSetting;
    Service service;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        editor = settings.edit();
        mRealm = new DatabaseService(this).getRealmInstance();
        requestAllPermissions();
        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        prefData = new SharedPrefManager(this);
        profileDbHandler = new UserProfileDbHandler(this);
        defaultPref = PreferenceManager.getDefaultSharedPreferences(this);
        processedUrl = Utilities.getUrl();
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

    public boolean isServerReachable(String processedUrl) throws Exception {
        progressDialog.setMessage(getString(R.string.connecting_to_server));
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
                        alertDialogOkay(getString(R.string.check_the_server_address_again_what_i_connected_to_wasn_t_the_planet_server));
                    } else {
                        startSync();
                    }
                } catch (Exception e) {
                    alertDialogOkay(getString(R.string.device_couldn_t_reach_server_check_and_try_again));
                    progressDialog.dismiss();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                alertDialogOkay(getString(R.string.device_couldn_t_reach_server_check_and_try_again));
                if (mRealm != null) mRealm.close();
                progressDialog.dismiss();
            }
        });
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
        try {
            mRealm = Realm.getDefaultInstance();
            this.settings = settings;
            if (mRealm.isEmpty()) {
                alertDialogOkay(getString(R.string.server_not_configured_properly_connect_this_device_with_planet_server));
                return false;
            } else {
                return checkName(username, password, isManagerMode);
            }
        } finally {
            if (mRealm != null && !mRealm.isClosed()) {
                mRealm.close();
            }
        }
    }

    @Nullable
    private Boolean checkName(String username, String password, boolean isManagerMode) {
        try {
            mRealm = Realm.getDefaultInstance();
            AndroidDecrypter decrypt = new AndroidDecrypter();
            RealmResults<RealmUserModel> db_users = mRealm.where(RealmUserModel.class).equalTo("name", username).findAll();
            for (RealmUserModel user : db_users) {
                if (user.get_id().isEmpty()) {
                    if (username.equals(user.getName()) && password.equals(user.getPassword())) {
                        saveUserInfoPref(settings, password, user);
                        return true;
                    }
                } else {
                    if (decrypt.AndroidDecrypter(username, password, user.getDerived_key(), user.getSalt())) {
                        if (isManagerMode && !user.isManager()) return false;
                        saveUserInfoPref(settings, password, user);
                        return true;
                    }
                }
            }
        } catch (Exception err) {
            err.printStackTrace();
            if (mRealm != null && !mRealm.isClosed()) {
                mRealm.close();
            }
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
        if (isUrlValid(url)) processedUrl = setUrlParts(url, pin, this);
        return processedUrl;
    }

    @Override
    public void onSyncStarted() {
        progressDialog.setMessage(getString(R.string.syncing_data_please_wait));
        progressDialog.show();
    }

    @Override
    public void onSyncFailed(final String s) {
        syncIconDrawable = (AnimationDrawable) syncIcon.getDrawable();
        syncIconDrawable.stop();
        syncIconDrawable.selectDrawable(0);
        syncIcon.invalidateDrawable(syncIconDrawable);

        runOnUiThread(() -> {
            DialogUtils.showAlert(SyncActivity.this, getString(R.string.sync_failed), s);
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

            DialogUtils.showSnack(findViewById(android.R.id.content), getString(R.string.sync_completed));

            NotificationUtil.cancellAll(this);
        });
    }

    public void declareElements() {
        if (!defaultPref.contains("beta_addImageToMessage")) {
            defaultPref.edit().putBoolean("beta_addImageToMessage", true).commit();
        }
        customDeviceName.setText(getCustomDeviceName());

        btnSignIn.setOnClickListener(view -> {
            if(TextUtils.isEmpty(inputName.getText().toString())){
                inputName.setError(getString(R.string.err_msg_name));
            } else if(TextUtils.isEmpty(inputPassword.getText().toString())){
                inputPassword.setError(getString(R.string.err_msg_password));
            }else{
                submitForm(inputName.getText().toString(), inputPassword.getText().toString());
            }
        });
        if (!settings.contains("serverProtocol"))
            settings.edit().putString("serverProtocol", "http://").commit();
        becomeMember.setOnClickListener(v -> {
            inputName.setText("");
            becomeAMember();
        });
        imgBtnSetting.setOnClickListener(view -> {
            inputName.setText("");
            settingDialog(this);
        });
        btnGuestLogin.setOnClickListener(view -> {
            inputName.setText("");
            showGuestLoginDialog();
        });
    }

    public void declareMoreElements() {
        try {
            mRealm = Realm.getDefaultInstance();
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
            lblVersion.setText(getResources().getText(R.string.version) + " " + getResources().getText(R.string.app_version));
            inputName.addTextChangedListener(new MyTextWatcher(inputName));
            inputPassword.addTextChangedListener(new MyTextWatcher(inputPassword));
            inputPassword.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                        (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    btnSignIn.performClick();
                    return true;
                }
                return false;
            });

            setUplanguageButton();

            if (defaultPref.getBoolean("saveUsernameAndPassword", false)) {
                inputName.setText(settings.getString(getString(R.string.login_user), ""));
                inputPassword.setText(settings.getString(getString(R.string.login_password), ""));
            }

            if (NetworkUtils.isNetworkConnected()) {
                service.syncPlanetServers(mRealm, success -> Utilities.toast(this, success));
            }

            inputName.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String lowercaseText = s.toString().toLowerCase(Locale.ROOT);
                    if (!s.toString().equals(lowercaseText)) {
                        inputName.setText(lowercaseText);
                        inputName.setSelection(lowercaseText.length());
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        } finally {
            if (mRealm != null && !mRealm.isClosed()) {
                mRealm.close();
            }
        }
    }

    private void setUplanguageButton() {
        String[] languageKey = getResources().getStringArray(R.array.language_keys);
        String[] languages = getResources().getStringArray(R.array.language);
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        int index = Arrays.asList(languageKey).indexOf(pref.getString("app_language", "en"));
        btnLang.setText(languages[index]);
        btnLang.setOnClickListener(view -> {
            new AlertDialog.Builder(this).setTitle(R.string.select_language).setSingleChoiceItems(getResources().getStringArray(R.array.language), index, null).setPositiveButton(R.string.ok, (dialog, whichButton) -> {
                dialog.dismiss();
                int selectedPosition = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                String lang = languageKey[selectedPosition];
                LocaleHelper.setLocale(this, lang);
                recreate();
            }).setNegativeButton(R.string.cancel, null).show();
        });
    }

    public void submitForm(String name, String password) {
        if (forceSyncTrigger()) {
            return;
        }

        SharedPreferences.Editor editor = settings.edit();
        editor.putString("loginUserName", name);
        editor.putString("loginUserPassword", password);

        boolean isLoggedIn = authenticateUser(settings, name, password, false);
        if (isLoggedIn) {
            Toast.makeText(getApplicationContext(), getString(R.string.thank_you), Toast.LENGTH_SHORT).show();
            onLogin();
            saveUsers(inputName.getText().toString(), inputPassword.getText().toString(), "member");
        } else {
            ManagerSync.getInstance().login(name, password, new SyncListener() {
                @Override
                public void onSyncStarted() {
                    progressDialog.setMessage(getString(R.string.please_wait));
                    progressDialog.show();
                }

                @Override
                public void onSyncComplete() {
                    progressDialog.dismiss();
                    Utilities.log("on complete");
                    boolean log = authenticateUser(settings, name, password, true);
                    if (log) {
                        Toast.makeText(getApplicationContext(), getString(R.string.thank_you), Toast.LENGTH_SHORT).show();
                        onLogin();
                        saveUsers(inputName.getText().toString(), inputPassword.getText().toString(), "member");
                    } else {
                        alertDialogOkay(getString(R.string.err_msg_login));
                    }
                    syncIconDrawable.stop();
                    syncIconDrawable.selectDrawable(0);
                }

                @Override
                public void onSyncFailed(String msg) {
                    Utilities.toast(context, msg);
                    progressDialog.dismiss();
                    syncIconDrawable.stop();
                    syncIconDrawable.selectDrawable(0);
                }
            });
        }
        editor.commit();
    }

    public void becomeAMember() {
        if (!Utilities.getUrl().isEmpty()) {
            startActivity(new Intent(this, BecomeMemberActivity.class));
        } else {
            Utilities.toast(this, getString(R.string.please_enter_server_url_first));
            settingDialog(this);
        }
    }

    public boolean forceSyncTrigger() {
        lblLastSyncDate.setText(getString(R.string.last_sync) + Utilities.getRelativeTime(settings.getLong(getString(R.string.last_syncs), 0)) + " >>");
        if (Constants.autoSynFeature(Constants.KEY_AUTOSYNC_, getApplicationContext()) && Constants.autoSynFeature(Constants.KEY_AUTOSYNC_WEEKLY, getApplicationContext())) {
            return checkForceSync(7);
        } else if (Constants.autoSynFeature(Constants.KEY_AUTOSYNC_, getApplicationContext()) && Constants.autoSynFeature(Constants.KEY_AUTOSYNC_MONTHLY, getApplicationContext())) {
            return checkForceSync(30);
        }
        return false;
    }

    public void showWifiDialog() {
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
            alertDialogBuilder.setMessage(getString(R.string.it_has_been_more_than) + (daysDiff - 1) + getString(R.string.days_since_you_last_synced_this_device) + getString(R.string.connect_it_to_the_server_over_wifi_and_sync_it_to_reactivate_this_tablet));
            alertDialogBuilder.setPositiveButton(R.string.okay, (arg0, arg1) -> Toast.makeText(getApplicationContext(), getString(R.string.connect_to_the_server_over_wifi_and_sync_your_device_to_continue), Toast.LENGTH_LONG).show());
            alertDialogBuilder.show();
            return true;
        } else {
            Log.e("Sync Date ", "Not up to  - " + maxDays);
            return false;
        }
    }

    public void showGuestLoginDialog() {
        try {
            mRealm = Realm.getDefaultInstance();
            mRealm.refresh();
            editor = settings.edit();

            AlertGuestLoginBinding alertGuestLoginBinding = AlertGuestLoginBinding.inflate(LayoutInflater.from(this));
            View v = alertGuestLoginBinding.getRoot();

            alertGuestLoginBinding.etUserName.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String input = s.toString();
                    char firstChar = input.length() > 0 ? input.charAt(0) : '\0';
                    boolean hasInvalidCharacters = false;
                    boolean hasSpecialCharacters = false;
                    boolean hasDiacriticCharacters = false;

                    String normalizedText = Normalizer.normalize(s, Normalizer.Form.NFD);

                    for (int i = 0; i < input.length(); i++) {
                        char c = input.charAt(i);
                        if (c != '_' && c != '.' && c != '-' && !Character.isDigit(c) && !Character.isLetter(c)) {
                            hasInvalidCharacters = true;
                            break;
                        }
                    }

                    String regex = ".*[ßäöüéèêæÆœøØ¿àìòùÀÈÌÒÙáíóúýÁÉÍÓÚÝâîôûÂÊÎÔÛãñõÃÑÕëïÿÄËÏÖÜŸåÅŒçÇðÐ].*";
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(input);
                    hasSpecialCharacters = matcher.matches();

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        hasDiacriticCharacters = !normalizedText.codePoints().allMatch(
                                codePoint -> Character.isLetterOrDigit(codePoint) || codePoint == '.' || codePoint == '-' || codePoint == '_'
                        );
                    }

                    if (!Character.isDigit(firstChar) && !Character.isLetter(firstChar)) {
                        alertGuestLoginBinding.etUserName.setError(getString(R.string.must_start_with_letter_or_number));
                    } else if (hasInvalidCharacters || hasDiacriticCharacters || hasSpecialCharacters) {
                        alertGuestLoginBinding.etUserName.setError(getString(R.string.only_letters_numbers_and_are_allowed));
                    } else {
                        String lowercaseText = input.toLowerCase(Locale.ROOT);
                        if (!input.equals(lowercaseText)) {
                            alertGuestLoginBinding.etUserName.setText(lowercaseText);
                            alertGuestLoginBinding.etUserName.setSelection(lowercaseText.length());
                        }
                        alertGuestLoginBinding.etUserName.setError(null);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Login As Guest")
                    .setView(v)
                    .setPositiveButton("Login", null)
                    .setNegativeButton("Cancel", null);
            AlertDialog dialog = builder.create();
            dialog.show();

            Button login = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            login.setOnClickListener(view -> {
                if (mRealm.isEmpty()) {
                    alertDialogOkay(getString(R.string.this_device_not_configured_properly_please_check_and_sync));
                    return;
                }
                String username = alertGuestLoginBinding.etUserName.getText().toString().trim();
                Character firstChar = username.isEmpty() ? null : username.charAt(0);
                boolean hasInvalidCharacters = false;
                boolean hasDiacriticCharacters = false;
                boolean hasSpecialCharacters = false;
                boolean isValid = true;
                String normalizedText = Normalizer.normalize(username, Normalizer.Form.NFD);

                String regex = ".*[ßäöüéèêæÆœøØ¿àìòùÀÈÌÒÙáíóúýÁÉÍÓÚÝâîôûÂÊÎÔÛãñõÃÑÕëïÿÄËÏÖÜŸåÅŒçÇðÐ].*";
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(username);

                if (TextUtils.isEmpty(username)) {
                    alertGuestLoginBinding.etUserName.setError(getString(R.string.username_cannot_be_empty));
                    isValid = false;
                }

                if (firstChar != null && !Character.isDigit(firstChar) && !Character.isLetter(firstChar)) {
                    alertGuestLoginBinding.etUserName.setError(getString(R.string.must_start_with_letter_or_number));
                    isValid = false;
                } else {
                    for (char c : username.toCharArray()) {
                        if (c != '_' && c != '.' && c != '-' && !Character.isDigit(c) && !Character.isLetter(c)) {
                            hasInvalidCharacters = true;
                            break;
                        }

                        hasSpecialCharacters = matcher.matches();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            hasDiacriticCharacters = !normalizedText.codePoints().allMatch(
                                    codePoint -> Character.isLetterOrDigit(codePoint) || codePoint == '.' || codePoint == '-' || codePoint == '_'
                            );
                        }
                    }

                    if (hasInvalidCharacters || hasDiacriticCharacters || hasSpecialCharacters) {
                        alertGuestLoginBinding.etUserName.setError(getString(R.string.only_letters_numbers_and_are_allowed));
                        isValid = false;
                    }
                }

                if (isValid) {
                    RealmUserModel existingUser = mRealm.where(RealmUserModel.class).equalTo("name", username).findFirst();
                    dialog.dismiss();

                    if (existingUser != null) {
                        if (existingUser.get_id().contains("guest")) {
                            showGuestDialog(username);
                        } else if (existingUser.get_id().contains("org.couchdb.user:")) {
                            showUserAlreadyMemberDialog(username);
                        }
                    } else {
                        RealmUserModel model = mRealm.copyFromRealm(RealmUserModel.createGuestUser(username, mRealm, settings));
                        if (model == null) {
                            Utilities.toast(this, getString(R.string.unable_to_login));
                        } else {
                            saveUsers(username, "", "guest");
                            saveUserInfoPref(settings, "", model);
                            onLogin();
                        }
                    }
                }
            });

            cancel.setOnClickListener(view -> dialog.dismiss());
        } finally {
            if (mRealm != null && !mRealm.isClosed()) {
                mRealm.close();
            }
        }
    }

    public void showGuestDialog(String username) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(username + " is already a guest");
        builder.setMessage("Continue only if this is you");
        builder.setCancelable(false);
        builder.setNegativeButton("cancel", (dialog, which) -> dialog.dismiss());

        builder.setPositiveButton("continue", (dialog, which) -> {
            dialog.dismiss();
            RealmUserModel model = mRealm.copyFromRealm(RealmUserModel.createGuestUser(username, mRealm, settings));
            if (model == null) {
                Utilities.toast(this, getString(R.string.unable_to_login));
            } else {
                saveUserInfoPref(settings, "", model);
                onLogin();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void showUserAlreadyMemberDialog(String username) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(username + " is already a member");
        builder.setMessage("Continue to login if this is you");
        builder.setCancelable(false);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        builder.setPositiveButton("login", (dialog, which) -> {
            dialog.dismiss();
            inputName.setText(username);
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void saveUsers(String name, String password, String source) {
        if(source == "guest"){
            User newUser = new User("", name, password, "", "guest");
            List<User> existingUsers = new ArrayList<>(prefData.getSAVEDUSERS1());

            boolean newUserExists = false;

            for (User user : existingUsers) {
                if (user.getName().equals(newUser.getName().trim())) {
                    newUserExists = true;
                    break;
                }
            }

            if (!newUserExists) {
                existingUsers.add(newUser);
                prefData.setSAVEDUSERS1(existingUsers);
            }
        } else if(source == "member"){
            String userProfile = profileDbHandler.getUserModel().getUserImage();
            String fullName = profileDbHandler.getUserModel().getFullName();

            if (userProfile == null) {
                userProfile = "";
            }

            if (fullName.trim().length() == 0) {
                fullName = profileDbHandler.getUserModel().getName();
            }

            User newUser = new User(fullName, name, password, userProfile, "member");
            List<User> existingUsers = new ArrayList<>(prefData.getSAVEDUSERS1());

            boolean newUserExists = false;

            for (User user : existingUsers) {
                if (user.getFullName().equals(newUser.getFullName().trim())) {
                    newUserExists = true;
                    break;
                }
            }

            if (!newUserExists) {
                existingUsers.add(newUser);
                prefData.setSAVEDUSERS1(existingUsers);
            }
        }
    }

    public void onLogin() {
        UserProfileDbHandler handler = new UserProfileDbHandler(this);
        handler.onLogin();
        handler.onDestory();
        editor.putBoolean(Constants.KEY_LOGIN, true).commit();
        openDashboard();
    }

    public void settingDialog(SyncActivity activity) {
        try {
            mRealm = Realm.getDefaultInstance();
            DialogServerUrlBinding dialogServerUrlBinding = DialogServerUrlBinding.inflate(LayoutInflater.from(this));
            spnCloud = dialogServerUrlBinding.spnCloud;
            protocol_checkin = dialogServerUrlBinding.radioProtocol;
            serverUrl = dialogServerUrlBinding.inputServerUrl;
            serverPassword = dialogServerUrlBinding.inputServerPassword;
            serverUrlProtocol = dialogServerUrlBinding.inputServerUrlProtocol;
            dialogServerUrlBinding.deviceName.setText(NetworkUtils.getDeviceName());
            MaterialDialog.Builder builder = new MaterialDialog.Builder(this);
            builder.title(R.string.action_settings)
                    .customView(dialogServerUrlBinding.getRoot(), true)
                    .positiveText(R.string.btn_sync)
                    .negativeText(R.string.btn_sync_cancel)
                    .neutralText(R.string.btn_sync_save)
                    .onPositive((dialog, which) -> continueSync(dialog))
                    .onNeutral((dialog, which) -> {
                        if (selectedTeamId == null){
                            saveConfigAndContinue(dialog);
                        } else {
                            String url = serverUrlProtocol.getText().toString() + serverUrl.getText().toString();
                            if (isUrlValid(url)) {
                                prefData.setSELECTEDTEAMID1(selectedTeamId);
                                if (!prefData.getTEAMMODE1()){
                                    prefData.setTEAMMODE1(true);
                                    Intent intent = new Intent(this, TeamLoginActivity.class);
                                    startActivity(intent);
                                } else if (prefData.getTEAMMODE1() && activity instanceof TeamLoginActivity) {
                                    ((TeamLoginActivity) activity).getTeamMembers();
                                }
                                saveConfigAndContinue(dialog);
                            } else {
                                saveConfigAndContinue(dialog);
                            }
                        }
                    });

            if (!prefData.getMANUALCONFIG1()) {
                dialogServerUrlBinding.manualConfiguration.setChecked(false);
                showConfigurationUIElements(dialogServerUrlBinding, false);
            } else {
                dialogServerUrlBinding.manualConfiguration.setChecked(true);
                showConfigurationUIElements(dialogServerUrlBinding, true);
            }

            MaterialDialog dialog = builder.build();
            positiveAction = dialog.getActionButton(DialogAction.POSITIVE);

            dialogServerUrlBinding.manualConfiguration.setOnCheckedChangeListener((compoundButton, isChecked) -> {
                if (isChecked) {
                    prefData.setMANUALCONFIG1(true);
                    settings.edit().putString("serverURL", "").apply();
                    settings.edit().putString("serverPin", "").apply();
                    dialogServerUrlBinding.radioHttp.setChecked(true);
                    settings.edit().putString("serverProtocol", getString(R.string.http_protocol)).commit();

                    showConfigurationUIElements(dialogServerUrlBinding, true);
                    List<RealmCommunity> communities = mRealm.where(RealmCommunity.class).sort("weight", Sort.ASCENDING).findAll();
                    List<RealmCommunity> nonEmptyCommunities = new ArrayList<>();
                    for (RealmCommunity community : communities) {
                        if (community.isValid() && !TextUtils.isEmpty(community.getName())) {
                            nonEmptyCommunities.add(community);
                        }
                    }
                    dialogServerUrlBinding.spnCloud.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, nonEmptyCommunities));

                    dialogServerUrlBinding.spnCloud.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                            onChangeServerUrl();
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> adapterView) {

                        }
                    });
                    dialogServerUrlBinding.switchServerUrl.setOnCheckedChangeListener((compoundBtn, b) -> {
                        settings.edit().putBoolean("switchCloudUrl", b).commit();
                        dialogServerUrlBinding.spnCloud.setVisibility(b ? View.VISIBLE : View.GONE);
                        setUrlAndPin(dialogServerUrlBinding.switchServerUrl.isChecked());
                    });
                    serverUrl.addTextChangedListener(new LoginActivity.MyTextWatcher(serverUrl));
                    dialogServerUrlBinding.switchServerUrl.setChecked(settings.getBoolean("switchCloudUrl", false));
                    setUrlAndPin(settings.getBoolean("switchCloudUrl", false));
                    protocol_semantics();
                } else {
                    prefData.setMANUALCONFIG1(false);
                    showConfigurationUIElements(dialogServerUrlBinding, false);
                    settings.edit().putBoolean("switchCloudUrl", false).commit();
                }
            });

            dialogServerUrlBinding.radioProtocol.setOnCheckedChangeListener((group, checkedId) -> {
                switch (checkedId) {
                    case R.id.radio_http ->
                            settings.edit().putString("serverProtocol", getString(R.string.http_protocol)).commit();
                    case R.id.radio_https ->
                            settings.edit().putString("serverProtocol", getString(R.string.https_protocol)).commit();
                }
            });

            dialog.show();
            sync(dialog);
        } finally {
            if (mRealm != null && !mRealm.isClosed()) {
                mRealm.close();
            }
        }
    }

    public void showConfigurationUIElements(DialogServerUrlBinding binding, boolean show) {
        binding.radioProtocol.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.switchServerUrl.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.ltProtocol.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.ltIntervalLabel.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.ltSyncSwitch.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.ltDeviceName.setVisibility(show ? View.VISIBLE : View.GONE);

        if (show) {
            if (settings.getString("serverURL", "").equals("https://planet.learning.ole.org")){
                settings.edit().putString("serverURL", "").apply();
                settings.edit().putString("serverPin", "").apply();
            }

            if (settings.getString("serverProtocol", "").equals(getString(R.string.http_protocol))) {
                binding.radioHttp.setChecked(true);
                settings.edit().putString("serverProtocol", getString(R.string.http_protocol)).commit();
            }

            if (settings.getString("serverProtocol", "").equals(getString(R.string.https_protocol))
                    && !settings.getString("serverURL", "").equals("")
                    && !settings.getString("serverURL", "").equals("https://planet.learning.ole.org")){
                binding.radioHttps.setChecked(true);
                settings.edit().putString("serverProtocol", getString(R.string.https_protocol)).commit();
            }

            serverUrl.setText(removeProtocol(settings.getString("serverURL", "")));
            serverPassword.setText(settings.getString("serverPin", ""));
            serverUrl.setEnabled(true);
            serverPassword.setEnabled(true);
        } else {
            serverUrl.setText("planet.learning.ole.org");
            serverPassword.setText("1983");
            serverUrl.setEnabled(false);
            serverPassword.setEnabled(false);
            settings.edit().putString("serverProtocol", getString(R.string.https_protocol)).commit();
            serverUrlProtocol.setText(getString(R.string.https_protocol));
        }
        try {
            mRealm = Realm.getDefaultInstance();
            List<RealmMyTeam> teams = mRealm.where(RealmMyTeam.class).isEmpty("teamId").findAll();
            if (teams.size() > 0 && show && !binding.inputServerUrl.getText().toString().equals("")) {
                binding.team.setVisibility(View.VISIBLE);
                teamAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, teamList);
                teamAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                teamList.clear();
                teamList.add("Select team");
                for (RealmMyTeam team : teams) {
                    if (team.isValid()) {
                        teamList.add(team.getName());
                    }
                }
                binding.team.setAdapter(teamAdapter);
                binding.team.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                        if (position > 0) {
                            RealmMyTeam selectedTeam = teams.get(position - 1);
                            if (selectedTeam != null) {
                                selectedTeamId = selectedTeam.get_id();
                            }
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parentView) {
                        // Do nothing when nothing is selected
                    }
                });
            }
            else if (teams.size() > 0 && show && binding.inputServerUrl.getText().toString().equals("")){
                binding.team.setVisibility(View.GONE);
            } else {
                binding.team.setVisibility(View.GONE);
            }
        } finally {
            if (mRealm != null && !mRealm.isClosed()) {
                mRealm.close();
            }
        }
    }

    public void onChangeServerUrl() {
        try {
            mRealm = Realm.getDefaultInstance();
            RealmCommunity selected = (RealmCommunity) spnCloud.getSelectedItem();
            if (selected == null) {
                return;
            }
            if (selected.isValid()) {
                serverUrl.setText(selected.getLocalDomain());
                protocol_checkin.check(R.id.radio_https);
                settings.getString("serverProtocol", getString(R.string.https_protocol));
                serverPassword.setText(selected.getWeight() == 0 ? "1983" : "");
                serverPassword.setEnabled(selected.getWeight() != 0);
            }
        } finally {
            if (mRealm != null && !mRealm.isClosed()) {
                mRealm.close();
            }
        }
    }

    public void setUrlAndPin(boolean checked) {
        if (checked) {
            onChangeServerUrl();
        } else {
            serverUrl.setText(removeProtocol(settings.getString("serverURL", "")));
            serverPassword.setText(settings.getString("serverPin", ""));
            protocol_checkin.check(TextUtils.equals(settings.getString("serverProtocol", ""), "http://") ? R.id.radio_http : R.id.radio_https);
            serverUrlProtocol.setText(settings.getString("serverProtocol", ""));
        }
        serverUrl.setEnabled(!checked);
        serverPassword.setEnabled(!checked);
        serverPassword.clearFocus();
        serverUrl.clearFocus();
        protocol_checkin.setEnabled(!checked);
    }

    public void protocol_semantics() {
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

    public String removeProtocol(String url) {
        url = url.replaceFirst(getString(R.string.https_protocol), "");
        url = url.replaceFirst(getString(R.string.http_protocol), "");
        return url;
    }

    public void continueSync(MaterialDialog dialog) {
        processedUrl = saveConfigAndContinue(dialog);
        if (TextUtils.isEmpty(processedUrl)) return;
        isSync = true;
        if (checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) && settings.getBoolean("firstRun", true)) {
            clearInternalStorage();
        }

        new Service(this).isPlanetAvailable(new Service.PlanetAvailableListener() {
            @Override
            public void isAvailable() {
                new Service(context).checkVersion(SyncActivity.this, settings);
            }

            @Override
            public void notAvailable() {
                if (!isFinishing()) {
                    DialogUtils.showAlert(context, "Error", getString(R.string.planet_server_not_reachable));
                }
            }
        });
    }

    @Override
    public void onSuccess(String s) {
        Utilities.log("Sync completed ");
        if (progressDialog.isShowing() && s.contains("Crash")) progressDialog.dismiss();
        DialogUtils.showSnack(btnSignIn, s);
        settings.edit().putLong("lastUsageUploaded", new Date().getTime()).commit();

        // Update last sync text
        lblLastSyncDate.setText(getString(R.string.last_sync) + Utilities.getRelativeTime(new Date().getTime()) + " >>");
    }

    @Override
    public void onUpdateAvailable(MyPlanet info, boolean cancelable) {
        try {
            mRealm = Realm.getDefaultInstance();
            AlertDialog.Builder builder = DialogUtils.getUpdateDialog(this, info, progressDialog);
            if (cancelable || NetworkUtils.getCustomDeviceName(this).endsWith("###")) {
                builder.setNegativeButton(R.string.update_later, (dialogInterface, i) -> continueSyncProcess());
            } else {
                mRealm.executeTransactionAsync(realm -> realm.deleteAll());
            }
            builder.setCancelable(cancelable);
            builder.show();
        } finally {
            if (mRealm != null && !mRealm.isClosed()) {
                mRealm.close();
            }
        }
    }

    @Override
    public void onCheckingVersion() {
        progressDialog.setMessage(getString(R.string.checking_version));
        progressDialog.show();
    }

    public void registerReceiver() {
        LocalBroadcastManager bManager = LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MESSAGE_PROGRESS);
        bManager.registerReceiver(broadcastReceiver, intentFilter);
    }

    @Override
    public void onError(String msg, boolean block) {
        Utilities.toast(this, msg);
        if (msg.startsWith("Config")) {
            settingDialog(this);
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
            e.printStackTrace();
        }
    }

    @Override
    public void onSelectedUser(RealmUserModel userModel) {
        try {
            mRealm = Realm.getDefaultInstance();
            LayoutChildLoginBinding layoutChildLoginBinding = LayoutChildLoginBinding.inflate(getLayoutInflater());
            new AlertDialog.Builder(this).setView(layoutChildLoginBinding.getRoot()).setTitle(R.string.please_enter_your_password).setPositiveButton(R.string.login, (dialogInterface, i) -> {
                String password = layoutChildLoginBinding.etChildPassword.getText().toString();
                if (authenticateUser(settings, userModel.getName(), password, false)) {
                    Toast.makeText(getApplicationContext(), getString(R.string.thank_you), Toast.LENGTH_SHORT).show();
                    onLogin();
                } else {
                    alertDialogOkay(getString(R.string.err_msg_login));
                }
            }).setNegativeButton(R.string.cancel, null).show();
        } finally {
            if (mRealm != null && !mRealm.isClosed()) {
                mRealm.close();
            }
        }
    }

    public String getCustomDeviceName() {
        return settings.getString("customDeviceName", NetworkUtils.getDeviceName());
    }

    public void resetGuestAsMember(String username) {
        List<User> existingUsers = prefData.getSAVEDUSERS1();

        boolean newUserExists = false;

        for (User user : existingUsers) {
            if (user.getName().equals(username)) {
                newUserExists = true;
                break;
            }
        }

        if (newUserExists){
            Iterator<User> iterator = existingUsers.iterator();
            while (iterator.hasNext()) {
                User user = iterator.next();
                if (user.getName().equals(username)) {
                    iterator.remove();
                }
            }
            prefData.setSAVEDUSERS1(existingUsers);
        }
    }

    public class MyTextWatcher implements TextWatcher {
        public View view;

        public MyTextWatcher(View view) {
            this.view = view;
        }

        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

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
                    if(!prefData.getTEAMMODE1()) {
                        validateEditText(inputPassword, inputLayoutPassword, getString(R.string.err_msg_password));
                    }
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mRealm != null && !mRealm.isClosed()) mRealm.close();
    }
}