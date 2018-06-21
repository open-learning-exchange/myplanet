package org.ole.planet.takeout;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.kittinunf.fuel.Fuel;
import com.github.kittinunf.fuel.core.FuelError;
import com.github.kittinunf.fuel.core.Handler;
import com.github.kittinunf.fuel.core.Request;
import com.github.kittinunf.fuel.core.Response;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmConfiguration;


public class LoginActivity extends SyncActivity {
    private EditText inputName, inputPassword;
    private TextInputLayout inputLayoutName, inputLayoutPassword;
    private Button btnSignIn;
    private ImageButton imgBtnSetting, syncOption;
    Context context;
    private View positiveAction;
    boolean connectionResult;
    dbSetup dbsetup = new dbSetup();
    EditText serverUrl;
    Fuel ful = new Fuel();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        context = this.getApplicationContext();
        settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        changeLogoColor();
        //layouts
        inputLayoutName = findViewById(R.id.input_layout_name);
        inputLayoutPassword = findViewById(R.id.input_layout_password);
        declareElements();
        btnSignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                submitForm();
            }
        });
        //listeners / actions
        inputName.addTextChangedListener(new MyTextWatcher(inputName));
        inputPassword.addTextChangedListener(new MyTextWatcher(inputPassword));
        dbsetup.Setup_db(this.context);

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

    public void declareElements() {
        //editText
        inputName = findViewById(R.id.input_name);
        inputPassword = findViewById(R.id.input_password);
        //buttons
        btnSignIn = findViewById(R.id.btn_signin);
        imgBtnSetting = findViewById(R.id.imgBtnSetting);
        // textviews
        syncOption = findViewById(R.id.syncOption);

        imgBtnSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MaterialDialog.Builder builder = new MaterialDialog.Builder(LoginActivity.this).title(R.string.action_settings).customView(R.layout.dialog_server_url, true).positiveText(R.string.btn_connect).onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        dialog.dismiss();
                        serverUrl = dialog.getCustomView().findViewById(R.id.input_server_url);
                        isServerReachable(serverUrl.getText().toString());
                    }
                });
                settingDialog(builder);
            }
        });
        syncOption.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                feedbackDialog();
            }
        });
    }

    /**
     * Form  Validation
     */
    private void submitForm() {
        if (!validateEditText(inputName, inputLayoutName, getString(R.string.err_msg_name))) {
            return;
        }
        if (!validateEditText(inputPassword, inputLayoutPassword, getString(R.string.err_msg_password))) {
            return;
        }
        if (authenticateUser(settings, inputName.getText().toString(), inputPassword.getText().toString(), context)) {
            Toast.makeText(getApplicationContext(), "Thank You!", Toast.LENGTH_SHORT).show();
            Intent dashboard = new Intent(getApplicationContext(), Dashboard.class);
            startActivity(dashboard);
        } else {
            alertDialogOkay(getString(R.string.err_msg_login));
        }
    }

    private class MyTextWatcher implements TextWatcher {
        private View view;

        private MyTextWatcher(View view) {
            this.view = view;
        }

        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            //action before text change
        }

        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            //action on or during text change
        }

        public void afterTextChanged(Editable editable) {
            switch (view.getId()) {
                case R.id.input_name:
                    validateEditText(inputName, inputLayoutName, getString(R.string.err_msg_name));
                    break;
                case R.id.input_password:
                    validateEditText(inputPassword, inputLayoutPassword, getString(R.string.err_msg_password));
                    break;
                default:
                    break;
            }
        }
    }

    public void settingDialog(MaterialDialog.Builder builder) {
        MaterialDialog dialog = builder.build();
        positiveAction = dialog.getActionButton(DialogAction.POSITIVE);
        serverUrl = dialog.getCustomView().findViewById(R.id.input_server_url);
        serverUrl.setText(settings.getString("serverURL", ""));
        serverUrl.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //action before text change
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                positiveAction.setEnabled(s.toString().trim().length() > 0 && URLUtil.isValidUrl(s.toString()));
            }

            @Override
            public void afterTextChanged(Editable s) {
                //action after text change
            }
        });
        positiveAction.setEnabled(false);
        dialog.show();
    }


    public boolean isServerReachable(final String url) {
        ful.get(url + "/_all_dbs").responseString(new Handler<String>() {
            @Override
            public void success(Request request, Response response, String s) {
                try {
                    List<String> myList = new ArrayList<>();
                    myList.clear();
                    myList = Arrays.asList(s.split(","));
                    if (myList.size() < 8) {
                        alertDialogOkay("Check the server address again. What i connected to wasn't the Planet Server");
                    } else {
                        //alertDialogOkay("Test successful. You can now click on \"Save and Proceed\" ");
                        //Todo get password from EditText
                        setUrlParts(url, "", context);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void failure(Request request, Response response, FuelError fuelError) {
                ///Log.d("error", fuelError.toString());
                alertDialogOkay("Device couldn't reach server. Check and try again");
                if (mRealm != null)
                    mRealm.close();
            }
        });
        return connectionResult;
    }

    // Server feedback dialog
    public void feedbackDialog() {
        MaterialDialog dialog = new MaterialDialog.Builder(this).title(R.string.title_sync_settings)
                .customView(R.layout.dialog_sync_feedback, true)
                .positiveText(R.string.btn_sync).negativeText(R.string.btn_sync_cancel).neutralText(R.string.btn_sync_save)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(MaterialDialog dialog, DialogAction which) {
                        Toast.makeText(LoginActivity.this, "Syncing now...", Toast.LENGTH_SHORT).show();
                    }
                })
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(MaterialDialog dialog, DialogAction which) {
                        Log.e("MD: ", "Clicked Negative (Cancel)");
                    }
                })
                .onNeutral(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        Toast.makeText(LoginActivity.this, "Saving sync settings...", Toast.LENGTH_SHORT).show();
                    }
                })
                .build();
        sync(dialog);
        dialog.show();
    }


}
