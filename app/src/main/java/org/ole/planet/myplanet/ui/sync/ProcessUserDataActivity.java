package org.ole.planet.myplanet.ui.sync;

import android.app.ProgressDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;

import com.google.android.material.textfield.TextInputLayout;

import androidx.appcompat.app.AlertDialog;

import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.URLUtil;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.PermissionActivity;
import org.ole.planet.myplanet.callback.SuccessListener;
import org.ole.planet.myplanet.model.Download;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UploadManager;
import org.ole.planet.myplanet.service.UploadToShelfService;
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity;
import org.ole.planet.myplanet.utilities.DialogUtils;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Objects;

import static org.ole.planet.myplanet.ui.dashboard.DashboardActivity.MESSAGE_PROGRESS;

public abstract class ProcessUserDataActivity extends PermissionActivity implements SuccessListener {
    SharedPreferences settings;
    ProgressDialog progressDialog;
    public BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(MESSAGE_PROGRESS) && progressDialog != null) {
                Download download = intent.getParcelableExtra("download");
                checkDownloadResult(download, progressDialog);
            }
        }
    };

    public boolean validateEditText(EditText textField, TextInputLayout textLayout, String err_message) {
        if (textField.getText().toString().trim().isEmpty()) {
            textLayout.setError(err_message);
            requestFocus(textField);
            return false;
        } else {
            textLayout.setErrorEnabled(false);
        }
        return true;
    }

    public void checkDownloadResult(Download download, ProgressDialog progressDialog) {
        if (!download.isFailed()) {
            progressDialog.setMessage("Downloading .... " + download.getProgress() + "% complete");
            if (download.isCompleteAll()) {
                progressDialog.dismiss();
                FileUtils.installApk(this, download.getFileUrl());
            }
        } else {
            progressDialog.dismiss();
            DialogUtils.showError(progressDialog, download.getMessage());
        }
    }

    public void openDashboard() {
        Intent dashboard = new Intent(getApplicationContext(), DashboardActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(dashboard);
        finish();
    }

    private void requestFocus(View view) {
        if (view.requestFocus()) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }

    public void changeLogoColor() {
        ImageView logo = findViewById(R.id.logoImageView);
        final int newColor = getResources().getColor(android.R.color.white);
        int alpha = Math.round(Color.alpha(newColor) * 10);
        int red = Color.red(newColor);
        int green = Color.green(newColor);
        int blue = Color.blue(newColor);
        int alphaWhite = Color.argb(alpha, red, green, blue);
        logo.setColorFilter(alphaWhite, PorterDuff.Mode.SRC_ATOP);
    }

    public String setUrlParts(String url, String password, Context context) {
        SharedPreferences.Editor editor = settings.edit();
        Uri uri = Uri.parse(url);
        String couchdbURL, url_user, url_pwd;
        if (url.contains("@")) {
            String[] userinfo = getUserInfo(uri);
            url_user = userinfo[0];
            url_pwd = userinfo[1];
            couchdbURL = url;
        } else if (TextUtils.isEmpty(password)) {
            DialogUtils.showAlert(this, "", "Pin is required.");
            return "";
        } else {
            url_user = "satellite";
            url_pwd = password;
            couchdbURL = uri.getScheme() + "://" + url_user + ":" + url_pwd + "@" + uri.getHost() + ":" + (uri.getPort() == -1 ? (Objects.equals(uri.getScheme(), "http") ? 80 : 443) : uri.getPort());
        }

        editor.putString("serverPin", password);
        saveUrlScheme(editor, uri, url, couchdbURL);
        editor.putString("url_user", url_user);
        editor.putString("url_pwd", url_pwd);
        editor.commit();
        if (!couchdbURL.endsWith("db")) {
            couchdbURL += "/db";
        }
        return couchdbURL;
    }

    public boolean isUrlValid(String url) {
        if (!URLUtil.isValidUrl(url) || url.equals("http://") || url.equals("https://")) {
            DialogUtils.showAlert(this, "Invalid Url", "Please enter valid url to continue.");
            return false;
        }
        return true;
    }

    public void startUpload() {
        progressDialog.setMessage("Uploading data to server, please wait.....");
        progressDialog.show();
        Utilities.log("Upload : upload started");
        UploadToShelfService.getInstance().uploadUserData(success -> {
            UploadToShelfService.getInstance().uploadHealth();
        });
        UploadManager.getInstance().uploadUserActivities(this);
        UploadManager.getInstance().uploadExamResult(this);
        UploadManager.getInstance().uploadFeedback(this);
        UploadManager.getInstance().uploadAchievement();
        UploadManager.getInstance().uploadResourceActivities("");
        UploadManager.getInstance().uploadCourseActivities();
        UploadManager.getInstance().uploadSearchActivity();
        UploadManager.getInstance().uploadNews();
        UploadManager.getInstance().uploadTeams();
        UploadManager.getInstance().uploadResource(this);
        UploadManager.getInstance().uploadRating(this);
        UploadManager.getInstance().uploadTeamTask();
        UploadManager.getInstance().uploadCrashLog(this);
//        UploadManager.getInstance().uploadHealth();
        UploadManager.getInstance().uploadSubmitPhotos(this);
        UploadManager.getInstance().uploadActivities(this);
        Toast.makeText(this, "Uploading activities to server, please wait...", Toast.LENGTH_SHORT).show();
    }


    protected void hideKeyboard(View view) {
        InputMethodManager in = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        in.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
    }

    public void saveUserInfoPref(SharedPreferences settings, String password, RealmUserModel user) {
        this.settings = settings;
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("userId", user.getId());
        editor.putString("name", user.getName());
        editor.putString("password", password);
        editor.putString("firstName", user.getFirstName());
        editor.putString("lastName", user.getLastName());
        editor.putString("middleName", user.getMiddleName());
        editor.putBoolean("isUserAdmin", user.getUserAdmin());
        editor.putLong("lastLogin", System.currentTimeMillis());
        editor.commit();
    }

    public void alertDialogOkay(String Message) {
        AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
        builder1.setMessage(Message);
        builder1.setCancelable(true);
        builder1.setNegativeButton("Okay",
                (dialog, id) -> dialog.cancel());
        AlertDialog alert11 = builder1.create();
        alert11.show();
    }

    public String[] getUserInfo(Uri uri) {
        String[] ar = {"", ""};
        String[] info = uri.getUserInfo().split(":");
        if (info.length > 1) {
            ar[0] = info[0];
            ar[1] = info[1];
        }
        return ar;
    }

    protected void saveUrlScheme(SharedPreferences.Editor editor, Uri uri, String url, String couchdbURL) {
        editor.putString("url_Scheme", uri.getScheme());
        editor.putString("url_Host", uri.getHost());
        editor.putInt("url_Port", uri.getPort() == -1 ? (uri.getScheme().equals("http") ? 80 : 443) : uri.getPort());
        editor.putString("serverURL", url);
        editor.putString("couchdbURL", couchdbURL);
    }

}
