package org.ole.planet.myplanet.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import org.ole.planet.myplanet.R;
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

    public static class SettingFragment extends PreferenceFragment {
        UserProfileDbHandler profileDbHandler;
        RealmUserModel user;

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

            ListPreference lp = (ListPreference) findPreference("app_language");
           // lp.setSummary(PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("app_language", ""));
            lp.setOnPreferenceChangeListener((preference, o) -> {
                LocaleHelper.setLocale(getActivity(),o.toString());
                getActivity().recreate();
                return true;
            });
//
//            SwitchPreference theme = (SwitchPreference) findPreference("bell_theme");
//            theme.setChecked(settings.getBoolean("bell_theme", false));
//            theme.setOnPreferenceChangeListener((preference, o) -> {
//                SharedPreferences.Editor editor = settings.edit();
//                editor.putBoolean("bell_theme",(boolean) o );
//                editor.commit();
//                return true;
//            });

        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            profileDbHandler.onDestory();
        }

    }

    @Override
    public void finish() {
        super.finish();
        startActivity(new Intent(this, DashboardActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
    }

}
