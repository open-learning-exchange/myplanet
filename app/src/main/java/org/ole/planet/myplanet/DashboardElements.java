package org.ole.planet.myplanet;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import org.ole.planet.myplanet.base.RatingFragment;
import org.ole.planet.myplanet.callback.OnRatingChangeListener;
import org.ole.planet.myplanet.userprofile.SettingActivity;
import org.ole.planet.myplanet.userprofile.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.Utilities;

/**
 * Extra class for excess methods in Dashboard activities
 */

public abstract class DashboardElements extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE_FILE = 111;
    private static final int PERMISSION_REQUEST_CODE_CAMERA = 112;
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
    public void requestPermission(String strPermission, int perCode) {
        ActivityCompat.requestPermissions(this, new String[]{strPermission}, perCode);
    }

    public boolean checkPermission(String strPermission) {
        int result = ContextCompat.checkSelfPermission(this, strPermission);
        if (result == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            return false;
        }
    }


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

    public void showRatingDialog(String type, String resource_id, String title, OnRatingChangeListener listener) {
        RatingFragment f = RatingFragment.newInstance(type, resource_id, title);
        f.setListener(listener);
        f.show(getSupportFragmentManager(), "");
    }

    public void requestPermission() {
        if (!checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) || !checkPermission(Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE_FILE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d("Main Activity", "onRequestPermissionsResult: permission granted");
        } else {
            Utilities.toast(this, "Download and camera Function will not work, please grant the permission.");
        }
    }



}
