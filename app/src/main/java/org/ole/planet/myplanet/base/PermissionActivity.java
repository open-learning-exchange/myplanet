package org.ole.planet.myplanet.base;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;

public abstract class PermissionActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE_FILE = 111;
    private static final int PERMISSION_REQUEST_CODE_CAMERA = 112;

    public boolean checkPermission(String strPermission) {
        int result = ContextCompat.checkSelfPermission(this, strPermission);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public void checkUsagesPermission() {
        if (!getUsagesPermission(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Utilities.toast(this, getString(R.string.please_allow_usages_permission_to_myplanet_app));
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            }
        }
    }

    public void requestPermission(String strPermission, int perCode) {
        if (!checkPermission(strPermission)) {
            ActivityCompat.requestPermissions(this, new String[]{strPermission}, perCode);
        }
    }

    public boolean getUsagesPermission(Context context) {
        boolean granted = false;
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.getPackageName());

        if (mode == AppOpsManager.MODE_DEFAULT) {
            granted = (context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED);
        } else {
            granted = (mode == AppOpsManager.MODE_ALLOWED);
        }
        return granted;
    }

    public void requestAllPermissions() {
        ArrayList<String> permissions = new ArrayList<>();

        if (!checkPermission(Manifest.permission.RECORD_AUDIO)) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }

        if (!checkPermission(Manifest.permission.CAMERA)) {
            permissions.add(Manifest.permission.CAMERA);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, PERMISSION_REQUEST_CODE_FILE);
            }
        } else {
            if (!checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE_FILE);
        }

        if (!permissions.isEmpty()) {
            String[] permissionsArray = permissions.toArray(new String[0]);
            ActivityCompat.requestPermissions(this, permissionsArray, PERMISSION_REQUEST_CODE_FILE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE_FILE || requestCode == PERMISSION_REQUEST_CODE_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.permissions_granted, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.permissions_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }
}