package org.ole.planet.myplanet;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.URLUtil;
import android.widget.EditText;
import android.widget.Toast;

import org.ole.planet.myplanet.Data.realm_UserModel;
import org.ole.planet.myplanet.callback.SuccessListener;
import org.ole.planet.myplanet.service.UploadManager;
import org.ole.planet.myplanet.utilities.DialogUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import okhttp3.internal.Util;

public abstract class ProcessUserData extends AppCompatActivity implements SuccessListener {
    SharedPreferences settings;

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


    private void requestFocus(View view) {
        if (view.requestFocus()) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }


    public boolean isUrlValid(String url) {
        if (!URLUtil.isValidUrl(url) || url.equals("http://") || url.equals("https://")) {
            DialogUtils.showAlert(this, "Invalid Url", "Please enter valid url to continue.");
            return false;
        }
        return true;
    }

    public void startUpload() {
        UploadManager.getInstance().uploadUserActivities(this);
        UploadManager.getInstance().uploadExamResult(this);
        UploadManager.getInstance().uploadFeedback(this);
        UploadManager.getInstance().uploadToshelf(this);
        UploadManager.getInstance().uploadResourceActivities("");
        UploadManager.getInstance().uploadResourceActivities("sync");
        UploadManager.getInstance().uploadRating(this);
        Toast.makeText(this, "Uploading activities to server, please wait...", Toast.LENGTH_SHORT).show();
    }


    protected void hideKeyboard(View view) {
        InputMethodManager in = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        in.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
    }

    public void saveUserInfoPref(SharedPreferences settings, String password, realm_UserModel user) {
        this.settings = settings;
        Utilities.log("UserId "  + user.getId());
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("userId", user.getId());
        editor.putString("name", user.getName());
        editor.putString("password", password);
        editor.putString("firstName", user.getFirstName());
        editor.putString("lastName", user.getLastName());
        editor.putString("middleName", user.getMiddleName());
        editor.putBoolean("isUserAdmin", user.getUserAdmin());
        editor.commit();
    }

    public void alertDialogOkay(String Message) {
        AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
        builder1.setMessage(Message);
        builder1.setCancelable(true);
        builder1.setNegativeButton("Okay",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert11 = builder1.create();
        alert11.show();
    }


    protected void saveUrlScheme(SharedPreferences.Editor editor, Uri uri) {
        editor.putString("url_Scheme", uri.getScheme());
        editor.putString("url_Host", uri.getHost());
        editor.putInt("url_Port", uri.getPort());
    }

}
