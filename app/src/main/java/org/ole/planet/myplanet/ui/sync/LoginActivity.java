package org.ole.planet.myplanet.ui.sync;

import static org.ole.planet.myplanet.ui.dashboard.DashboardActivity.MESSAGE_PROGRESS;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.AnimationDrawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
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
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewbinding.ViewBinding;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.material.textfield.TextInputLayout;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.SyncListener;
import org.ole.planet.myplanet.databinding.ActivityChildLoginBinding;
import org.ole.planet.myplanet.databinding.ActivityLoginBinding;
import org.ole.planet.myplanet.databinding.AlertGuestLoginBinding;
import org.ole.planet.myplanet.databinding.DialogServerUrlBinding;
import org.ole.planet.myplanet.databinding.LayoutChildLoginBinding;
import org.ole.planet.myplanet.databinding.LayoutUserListBinding;
import org.ole.planet.myplanet.datamanager.ManagerSync;
import org.ole.planet.myplanet.datamanager.Service;
import org.ole.planet.myplanet.model.MyPlanet;
import org.ole.planet.myplanet.model.RealmCommunity;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.model.User;
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
import org.ole.planet.myplanet.utilities.SharedPrefManager;
import org.ole.planet.myplanet.utilities.Utilities;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.realm.Realm;
import io.realm.Sort;

public class LoginActivity extends SyncActivity implements Service.CheckVersionCallback, AdapterTeam.OnUserSelectedListener {
    public static Calendar cal_today, cal_last_Sync;
    private EditText serverUrl, serverUrlProtocol;
    private EditText serverPassword;
    private String processedUrl;
    private RadioGroup protocol_checkin;
    private EditText inputName, inputPassword;
    private TextInputLayout inputLayoutName, inputLayoutPassword;
    private Button btnSignIn, becomeMember, btnGuestLogin, btnLang, openCommunity, btnFeedback;
    private View positiveAction;
    private ImageButton imgBtnSetting;
    private boolean isSync = false, forceSync = false;
    private SwitchCompat switchChildMode;
    private SharedPreferences defaultPref;
    private Service service;
    private Spinner spnCloud;
    private TextView tvAvailableSpace, previouslyLoggedIn, customDeviceName, lblVersion;
    SharedPrefManager prefData;
    private UserProfileDbHandler profileDbHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ViewBinding activityLoginBinding;
        if (settings.getBoolean("isChild", false)) {
            activityLoginBinding = ActivityChildLoginBinding.inflate(getLayoutInflater());
        } else {
            activityLoginBinding = ActivityLoginBinding.inflate(getLayoutInflater());
        }

        setContentView(activityLoginBinding.getRoot());
        prefData = new SharedPrefManager(this);
        profileDbHandler = new UserProfileDbHandler(this);

        if (activityLoginBinding instanceof ActivityLoginBinding) {
            inputName = ((ActivityLoginBinding) activityLoginBinding).inputName;
            inputPassword = ((ActivityLoginBinding) activityLoginBinding).inputPassword;
            inputLayoutName = ((ActivityLoginBinding) activityLoginBinding).inputLayoutName;
            inputLayoutPassword = ((ActivityLoginBinding) activityLoginBinding).inputLayoutPassword;
            btnSignIn = ((ActivityLoginBinding) activityLoginBinding).btnSignin;
            imgBtnSetting = ((ActivityLoginBinding) activityLoginBinding).imgBtnSetting;
            tvAvailableSpace= ((ActivityLoginBinding) activityLoginBinding).tvAvailableSpace;
            previouslyLoggedIn = ((ActivityLoginBinding) activityLoginBinding).previouslyLoggedIn;
            openCommunity = ((ActivityLoginBinding) activityLoginBinding).openCommunity;
            lblLastSyncDate = ((ActivityLoginBinding) activityLoginBinding).lblLastSyncDate;
            btnFeedback =((ActivityLoginBinding) activityLoginBinding).btnFeedback;
            customDeviceName =((ActivityLoginBinding) activityLoginBinding).customDeviceName;
            becomeMember = ((ActivityLoginBinding) activityLoginBinding).becomeMember;
            btnGuestLogin = ((ActivityLoginBinding) activityLoginBinding).btnGuestLogin;
            switchChildMode = ((ActivityLoginBinding) activityLoginBinding).switchChildMode;
            syncIcon = ((ActivityLoginBinding) activityLoginBinding).syncIcon;
            lblVersion = ((ActivityLoginBinding) activityLoginBinding).lblVersion;
            btnLang = ((ActivityLoginBinding) activityLoginBinding).btnLang;
        } else {
            inputName = ((ActivityChildLoginBinding) activityLoginBinding).inputName;
            inputPassword = ((ActivityChildLoginBinding) activityLoginBinding).inputPassword;
            inputLayoutName = ((ActivityChildLoginBinding) activityLoginBinding).inputLayoutName;
            inputLayoutPassword = ((ActivityChildLoginBinding) activityLoginBinding).inputLayoutPassword;
            btnSignIn = ((ActivityChildLoginBinding) activityLoginBinding).btnSignin;
            imgBtnSetting = ((ActivityChildLoginBinding) activityLoginBinding).imgBtnSetting;
            tvAvailableSpace= ((ActivityChildLoginBinding) activityLoginBinding).tvAvailableSpace;
            previouslyLoggedIn = ((ActivityChildLoginBinding) activityLoginBinding).previouslyLoggedIn;
            openCommunity = ((ActivityChildLoginBinding) activityLoginBinding).openCommunity;
            lblLastSyncDate = ((ActivityChildLoginBinding) activityLoginBinding).lblLastSyncDate;
            btnFeedback =((ActivityChildLoginBinding) activityLoginBinding).btnFeedback;
            customDeviceName =((ActivityChildLoginBinding) activityLoginBinding).customDeviceName;
            becomeMember = ((ActivityChildLoginBinding) activityLoginBinding).becomeMember;
            btnGuestLogin = ((ActivityChildLoginBinding) activityLoginBinding).btnGuestLogin;
            switchChildMode = ((ActivityChildLoginBinding) activityLoginBinding).switchChildMode;
            syncIcon = ((ActivityChildLoginBinding) activityLoginBinding).syncIcon;
            lblVersion = ((ActivityChildLoginBinding) activityLoginBinding).lblVersion;
            btnLang = ((ActivityChildLoginBinding) activityLoginBinding).btnLang;
        }

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
        setUpChildMode();
        forceSyncTrigger();

        if (!Utilities.getUrl().isEmpty()) {
            openCommunity.setVisibility(View.VISIBLE);
            openCommunity.setOnClickListener(v -> {
                inputName.setText("");
                new HomeCommunityDialogFragment().show(getSupportFragmentManager(), "");
            });
            new HomeCommunityDialogFragment().show(getSupportFragmentManager(), "");
        } else {
            openCommunity.setVisibility(View.GONE);
        }
        btnFeedback.setOnClickListener(view -> {
            inputName.setText("");
            new FeedbackFragment().show(getSupportFragmentManager(), "");
        });

        previouslyLoggedIn.setOnClickListener(view -> showUserList());
    }

    private void showUserList(){
        LayoutUserListBinding layoutUserListBinding = LayoutUserListBinding.inflate(LayoutInflater.from(this));
        View view = layoutUserListBinding.getRoot();

        AlertDialog.Builder builder = new AlertDialog.Builder(LoginActivity.this);
        builder.setTitle(R.string.select_user_to_login)
                .setView(view)
                .setNegativeButton(R.string.dismiss, null);

        List<User> existingUsers = prefData.getSAVEDUSERS1();
        UserListAdapter adapter = new UserListAdapter(LoginActivity.this, existingUsers);
        adapter.setOnItemClickListener(new UserListAdapter.OnItemClickListener() {
            @Override
            public void onItemClickGuest(String name) {
                RealmUserModel model = mRealm.copyFromRealm(RealmUserModel.createGuestUser(name, mRealm, settings));
                if (model == null) {
                    Utilities.toast(LoginActivity.this, getString(R.string.unable_to_login));
                } else {
                    saveUserInfoPref(settings, "", model);
                    onLogin();
                }
            }

            @Override
            public void onItemClickMember(String name, String password) {
                submitForm(name, password);
            }
        });

        layoutUserListBinding.listUser.setAdapter(adapter);
        layoutUserListBinding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                adapter.getFilter().filter(charSequence);
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        });

        AlertDialog dialog = builder.create();
        dialog.show();
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
            alertDialogBuilder.setMessage(getString(R.string.it_has_been_more_than) + (daysDiff - 1) + getString(R.string.days_since_you_last_synced_this_device) + getString(R.string.connect_it_to_the_server_over_wifi_and_sync_it_to_reactivate_this_tablet));
            alertDialogBuilder.setPositiveButton(R.string.okay, (arg0, arg1) -> Toast.makeText(getApplicationContext(), getString(R.string.connect_to_the_server_over_wifi_and_sync_your_device_to_continue), Toast.LENGTH_LONG).show());
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
            settingDialog();
        });
        btnGuestLogin.setOnClickListener(view -> {
            inputName.setText("");
            showGuestLoginDialog();
        });

        switchChildMode.setChecked(settings.getBoolean("isChild", false));
        switchChildMode.setOnCheckedChangeListener((compoundButton, b) -> {
            inputName.setText("");
            settings.edit().putBoolean("isChild", b).commit();
            recreate();
        });
    }

    private void becomeAMember() {
        if (!Utilities.getUrl().isEmpty()) {
            startActivity(new Intent(this, BecomeMemberActivity.class));
        } else {
            Utilities.toast(this, getString(R.string.please_enter_server_url_first));
            settingDialog();
        }
    }

    private void showGuestLoginDialog() {
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
                        Log.d("model", String.valueOf(existingUser.get_id()));
                        if (existingUser.get_id().contains("guest")) {
                            showGuestDialog(username);
                        } else if (existingUser.get_id().contains("org.couchdb.user:")) {
                            showUserAlreadyMemberDialog(username);
                        }
                    } else {
                        RealmUserModel model = mRealm.copyFromRealm(RealmUserModel.createGuestUser(username, mRealm, settings));
                        if (model == null) {
                            Utilities.toast(LoginActivity.this, getString(R.string.unable_to_login));
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

    private void showGuestDialog(String username) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(username + " is already a guest");
        builder.setMessage("Continue only if this is you");
        builder.setCancelable(false);
        builder.setNegativeButton("cancel", (dialog, which) -> dialog.dismiss());

        builder.setPositiveButton("continue", (dialog, which) -> {
            dialog.dismiss();
            RealmUserModel model = mRealm.copyFromRealm(RealmUserModel.createGuestUser(username, mRealm, settings));
            if (model == null) {
                Utilities.toast(LoginActivity.this, getString(R.string.unable_to_login));
            } else {
                saveUserInfoPref(settings, "", model);
                onLogin();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showUserAlreadyMemberDialog(String username) {
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
                if (!isFinishing()) {
                    DialogUtils.showAlert(LoginActivity.this, "Error", getString(R.string.planet_server_not_reachable));
                }
            }
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
                service.syncPlanetServers(mRealm, success -> Utilities.toast(LoginActivity.this, success));
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
                LocaleHelper.setLocale(LoginActivity.this, lang);
                recreate();
            }).setNegativeButton(R.string.cancel, null).show();
        });
    }

    /**
     * Form  Validation
     */
    private void submitForm(String name, String password) {
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
                    Utilities.toast(LoginActivity.this, msg);
                    progressDialog.dismiss();
                    syncIconDrawable.stop();
                    syncIconDrawable.selectDrawable(0);
                }
            });
        }
        editor.commit();
    }

    private void saveUsers(String name, String password, String source) {
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

    private void onLogin() {
        UserProfileDbHandler handler = new UserProfileDbHandler(this);
        handler.onLogin();
        handler.onDestory();
        editor.putBoolean(Constants.KEY_LOGIN, true).commit();
        openDashboard();
    }

    public void settingDialog() {
        Realm sRealm = null;
        try {
            sRealm = Realm.getDefaultInstance();
            DialogServerUrlBinding dialogServerUrlBinding = DialogServerUrlBinding.inflate(LayoutInflater.from(this));
            MaterialDialog.Builder builder = new MaterialDialog.Builder(LoginActivity.this);
            builder.title(R.string.action_settings)
                    .customView(dialogServerUrlBinding.getRoot(), true)
                    .positiveText(R.string.btn_sync)
                    .negativeText(R.string.btn_sync_cancel)
                    .neutralText(R.string.btn_sync_save)
                    .onPositive((dialog, which) -> continueSync(dialog))
                    .onNeutral((dialog, which) -> saveConfigAndContinue(dialog));

            MaterialDialog dialog = builder.build();
            positiveAction = dialog.getActionButton(DialogAction.POSITIVE);
            spnCloud = dialogServerUrlBinding.spnCloud;

            List<RealmCommunity> communities = sRealm.where(RealmCommunity.class).sort("weight", Sort.ASCENDING).findAll();
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
            protocol_checkin = dialogServerUrlBinding.radioProtocol;
            serverUrl = dialogServerUrlBinding.inputServerUrl;
            serverPassword = dialogServerUrlBinding.inputServerPassword;
            serverUrlProtocol = dialogServerUrlBinding.inputServerUrlProtocol;

            dialogServerUrlBinding.switchServerUrl.setOnCheckedChangeListener((compoundButton, b) -> {
                settings.edit().putBoolean("switchCloudUrl", b).commit();
                dialogServerUrlBinding.spnCloud.setVisibility(b ? View.VISIBLE : View.GONE);
                setUrlAndPin(dialogServerUrlBinding.switchServerUrl.isChecked());
                Log.d("checked", String.valueOf(dialogServerUrlBinding.switchServerUrl.isChecked()));
            });
            serverUrl.addTextChangedListener(new MyTextWatcher(serverUrl));
            dialogServerUrlBinding.deviceName.setText(getCustomDeviceName());
            dialogServerUrlBinding.switchServerUrl.setChecked(settings.getBoolean("switchCloudUrl", false));
            setUrlAndPin(settings.getBoolean("switchCloudUrl", false));
            protocol_semantics();
            dialog.show();
            sync(dialog);
        } finally {
            if (sRealm != null && !sRealm.isClosed()) {
                sRealm.close();
            }
        }
    }

    private void onChangeServerUrl() {
        try {
            mRealm = Realm.getDefaultInstance();
            RealmCommunity selected = (RealmCommunity) spnCloud.getSelectedItem();
            if (selected == null) {
                return;
            }
            if (selected.isValid()) {
                serverUrl.setText(selected.getLocalDomain());
                protocol_checkin.check(R.id.radio_https);
                settings.getString("serverProtocol", "https://");
                serverPassword.setText(selected.getWeight() == 0 ? "1983" : "");
                serverPassword.setEnabled(selected.getWeight() != 0);
            }
        } finally {
            if (mRealm != null && !mRealm.isClosed()) {
                mRealm.close();
            }
        }
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
        serverPassword.setEnabled(!checked);
        serverPassword.clearFocus();
        serverUrl.clearFocus();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mRealm != null && !mRealm.isClosed()) mRealm.close();
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
