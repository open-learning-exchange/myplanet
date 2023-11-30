package org.ole.planet.myplanet.ui.sync;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.ActivityLoginBinding;
import org.ole.planet.myplanet.databinding.LayoutUserListBinding;
import org.ole.planet.myplanet.datamanager.Service;
import org.ole.planet.myplanet.model.MyPlanet;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.model.User;
import org.ole.planet.myplanet.ui.community.HomeCommunityDialogFragment;
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.SharedPrefManager;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

public class LoginActivity extends SyncActivity {
    private ActivityLoginBinding activityLoginBinding;
    private Button openCommunity, btnFeedback;
    private boolean guest = false;
    private TextView tvAvailableSpace, previouslyLoggedIn;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityLoginBinding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(activityLoginBinding.getRoot());

        prefData = new SharedPrefManager(this);
        inputName = activityLoginBinding.inputName;
        inputPassword = activityLoginBinding.inputPassword;
        inputLayoutName = activityLoginBinding.inputLayoutName;
        inputLayoutPassword = activityLoginBinding.inputLayoutPassword;
        btnSignIn = activityLoginBinding.btnSignin;
        imgBtnSetting = activityLoginBinding.imgBtnSetting;
        tvAvailableSpace= activityLoginBinding.tvAvailableSpace;
        previouslyLoggedIn = activityLoginBinding.previouslyLoggedIn;
        openCommunity = activityLoginBinding.openCommunity;
        lblLastSyncDate = activityLoginBinding.lblLastSyncDate;
        btnFeedback = activityLoginBinding.btnFeedback;
        customDeviceName = activityLoginBinding.customDeviceName;
        becomeMember = activityLoginBinding.becomeMember;
        btnGuestLogin = activityLoginBinding.btnGuestLogin;
        syncIcon = activityLoginBinding.syncIcon;
        lblVersion = activityLoginBinding.lblVersion;
        btnLang = activityLoginBinding.btnLang;

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

        guest = getIntent().getBooleanExtra("guest", false);
        String username = getIntent().getStringExtra("username");
        if (guest){
            resetGuestAsMember(username);
        }
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
}