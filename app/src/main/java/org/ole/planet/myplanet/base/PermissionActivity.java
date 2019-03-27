package org.ole.planet.myplanet.base;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

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
        if (!checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) || !checkPermission(Manifest.permission.CAMERA) || !checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION) || !checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, PERMISSION_REQUEST_CODE_FILE);
        }
    }

}
