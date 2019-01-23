package org.ole.planet.myplanet.ui.sync;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnRatingChangeListener;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.SettingActivity;
import org.ole.planet.myplanet.ui.rating.RatingFragment;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;

/**
 * Extra class for excess methods in DashboardActivity activities
 */

public abstract class DashboardElementActivity extends AppCompatActivity {

    public UserProfileDbHandler profileDbHandler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        profileDbHandler = new UserProfileDbHandler(this);

    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dashboard, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_profile) {
            return true;
        } else if (id == R.id.menu_logout) {
            logout();
        } else if (id == R.id.action_setting) {
            startActivity(new Intent(this, SettingActivity.class));
        }

        return super.onOptionsItemSelected(item);
    }


    public void logout() {
        profileDbHandler.onLogout();
        Intent loginscreen = new Intent(this, LoginActivity.class);
        loginscreen.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(loginscreen);
        this.finish();
    }

    public void showRatingDialog(String type, String resource_id, String title, OnRatingChangeListener listener) {
        RatingFragment f = RatingFragment.newInstance(type, resource_id, title);
        f.setListener(listener);
        f.show(getSupportFragmentManager(), "");
    }


}
