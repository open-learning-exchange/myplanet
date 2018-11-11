package org.ole.planet.myplanet;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import org.ole.planet.myplanet.base.RatingFragment;
import org.ole.planet.myplanet.userprofile.SettingActivity;
import org.ole.planet.myplanet.userprofile.UserProfileDbHandler;

/**
 * Extra class for excess methods in Dashboard activities
 */

public abstract class DashboardElements extends AppCompatActivity {

    public EditText feedbackText;
    public UserProfileDbHandler profileDbHandler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        profileDbHandler = new UserProfileDbHandler(this);
    }

    /**
     * Disables the submit button until the feedback form is complete
     */


    public void disableSubmit(MaterialDialog dialog) {
        final View submitButton = dialog.getActionButton(DialogAction.POSITIVE);
        submitButton.setEnabled(false);
        feedbackText = dialog.getCustomView().findViewById(R.id.user_feedback);
        feedbackText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().length() != 0) submitButton.setEnabled(true);
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.toString().length() == 0) submitButton.setEnabled(false);
            }
        });
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
            // Toast.makeText(this, "Action clicked", Toast.LENGTH_LONG).show();
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

    public void showRatingDialog(String type, String resource_id, String title) {
        RatingFragment fragment = new RatingFragment();
        Bundle b = new Bundle();
        b.putString("id", resource_id);
        b.putString("title", title);
        b.putString("type", type);
        fragment.setArguments(b);
        fragment.show(getSupportFragmentManager(), "");
    }


}
