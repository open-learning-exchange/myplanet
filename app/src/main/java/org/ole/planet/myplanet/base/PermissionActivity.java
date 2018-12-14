package org.ole.planet.myplanet.base;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import org.ole.planet.myplanet.utilities.Utilities;

public abstract class PermissionActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE_FILE = 111;
    private static final int PERMISSION_REQUEST_CODE_CAMERA = 112;
    public boolean checkPermission(String strPermission) {
        int result = ContextCompat.checkSelfPermission(this, strPermission);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public void requestPermission(String strPermission, int perCode) {
        ActivityCompat.requestPermissions(this, new String[]{strPermission}, perCode);
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
