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
import androidx.viewbinding.ViewBinding;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.ActivityChildLoginBinding;
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
    private Button openCommunity, btnFeedback;
    private boolean guest = false;
    private TextView tvAvailableSpace, previouslyLoggedIn;
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
