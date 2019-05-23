package org.ole.planet.myplanet.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.SyncListener;
import org.ole.planet.myplanet.datamanager.ManagerSync;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity;
import org.ole.planet.myplanet.utilities.LocaleHelper;
import org.ole.planet.myplanet.utilities.Utilities;

import static org.ole.planet.myplanet.base.BaseResourceFragment.settings;

public class SettingActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.onAttach(base));
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingFragment())
                .commit();
        setTitle(getString(R.string.action_settings));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;

        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingFragment extends PreferenceFragment implements SyncListener {
        UserProfileDbHandler profileDbHandler;
        RealmUserModel user;
        ProgressDialog dialog;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref);
            profileDbHandler = new UserProfileDbHandler(getActivity());
            user = profileDbHandler.getUserModel();
            SwitchPreference p = (SwitchPreference) findPreference("show_topbar");
            p.setChecked(user.getShowTopbar());
            p.setOnPreferenceChangeListener((preference, o) -> {
                profileDbHandler.changeTopbarSetting((boolean) o);
                return true;
            });
            dialog = new ProgressDialog(getActivity());

            ListPreference lp = (ListPreference) findPreference("app_language");
            // lp.setSummary(PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("app_language", ""));
            lp.setOnPreferenceChangeListener((preference, o) -> {
                LocaleHelper.setLocale(getActivity(), o.toString());
                getActivity().recreate();
                return true;
            });

            Preference preference = findPreference("add_manager");
            preference.setOnPreferenceClickListener(preference1 -> {
                managerLogin();
                return false;
            });




        }

        private void managerLogin() {
            View v = LayoutInflater.from(getActivity()).inflate(R.layout.alert_manager_login, null);
            EditText etUserName = v.findViewById(R.id.et_user_name);
            EditText etPassword = v.findViewById(R.id.et_password);
            new AlertDialog.Builder(getActivity()).setTitle("Add Manager Account")
                    .setView(v)
                    .setPositiveButton("Ok", (dialogInterface, i) -> {

                        String username = etUserName.getText().toString();
                        String password = etPassword.getText().toString();
                        if (username.isEmpty()){
                            Utilities.toast(getActivity(),"Please enter username");

                        }else if(password.isEmpty()){
                            Utilities.toast(getActivity(),"Please enter password");
                        }else{
                            ManagerSync.getInstance().login(username, password, this);
                        }
                    }).setNegativeButton("Cancel", null).show();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            profileDbHandler.onDestory();
        }

        @Override
        public void onSyncStarted() {
            dialog.show();
        }

        @Override
        public void onSyncComplete() {
            getActivity().runOnUiThread(() -> {
                Utilities.toast(getActivity(),"Added manager user");
                dialog.dismiss();
            });
        }

        @Override
        public void onSyncFailed(String msg) {
          getActivity().runOnUiThread(() -> {
              Utilities.toast(getActivity(),msg);
              dialog.dismiss();
          });
        }
    }

    @Override
    public void finish() {
        super.finish();
        startActivity(new Intent(this, DashboardActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
    }

}
