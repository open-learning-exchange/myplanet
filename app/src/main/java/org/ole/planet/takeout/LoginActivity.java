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
import android.support.v7.app.AppCompatActivity;
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
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.kittinunf.fuel.Fuel;
import com.github.kittinunf.fuel.core.FuelError;
import com.github.kittinunf.fuel.core.Handler;
import com.github.kittinunf.fuel.core.Request;
import com.github.kittinunf.fuel.core.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class LoginActivity extends AppCompatActivity {
    private EditText inputName, inputPassword;
    private TextInputLayout inputLayoutName, inputLayoutPassword;
    private Button btnSignIn;
    private ImageButton imgBtnSetting;
    Context context;
    private View positiveAction;
    boolean connectionResult;
    dbSetup dbsetup =  new dbSetup();
    Fuel ful = new Fuel();
    public static final String PREFS_NAME = "OLE_PLANET";
    SharedPreferences settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        context = this.getApplicationContext();
        ImageView logo = findViewById(R.id.logoImageView);
        final int newColor = getResources().getColor(android.R.color.white);
        int alphaWhite = adjustAlpha(newColor,10);
        logo.setColorFilter(alphaWhite, PorterDuff.Mode.SRC_ATOP);
        declareElements();
        //listeners / actions
        inputName.addTextChangedListener(new MyTextWatcher(inputName));
        inputPassword.addTextChangedListener(new MyTextWatcher(inputPassword));
        btnSignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                submitForm();
            }
        });
        imgBtnSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                settingDialog();
            }
        });
        dbsetup.Setup_db(this.context);

    }

    public void declareElements(){
        //layouts
        inputLayoutName = findViewById(R.id.input_layout_name);
        inputLayoutPassword = findViewById(R.id.input_layout_password);
        //editText
        inputName = findViewById(R.id.input_name);
        inputPassword = findViewById(R.id.input_password);
        //buttons
        btnSignIn = findViewById(R.id.btn_signin);
        imgBtnSetting = findViewById(R.id.imgBtnSetting);

    }

    public int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    /** Form  Validation  */
    private void submitForm() {
        if (!validateEditText(inputName,inputLayoutName,getString(R.string.err_msg_name))) {
            return;
        }
        if (!validateEditText(inputPassword,inputLayoutPassword,getString(R.string.err_msg_password))) {
            return;
        }
        Toast.makeText(getApplicationContext(), "Thank You!", Toast.LENGTH_SHORT).show();
        Intent dashboard = new Intent(getApplicationContext(),Dashboard.class);
        startActivity(dashboard);

    }
    private boolean validateEditText(EditText textField,TextInputLayout textLayout,String err_message ){
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
                    validateEditText(inputName,inputLayoutName,getString(R.string.err_msg_name));
                    break;
                case R.id.input_password:
                    validateEditText(inputPassword,inputLayoutPassword,getString(R.string.err_msg_password));
                    break;
                    default:
                        break;
            }
        }
    }

    public void  settingDialog(){
        boolean wrapInScrollView = true;
        MaterialDialog.Builder builder = new MaterialDialog.Builder(LoginActivity.this).title(R.string.action_settings).customView(R.layout.dialog_server_url, wrapInScrollView).positiveText(R.string.btn_connect).onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        dialog.dismiss();
                        EditText serverUrl = dialog.getCustomView().findViewById(R.id.input_server_url);
                        isServerReachable(serverUrl.getText().toString());    }
                });
        MaterialDialog dialog = builder.build();
        positiveAction = dialog.getActionButton(DialogAction.POSITIVE);
        EditText serverUrl = dialog.getCustomView().findViewById(R.id.input_server_url);
        serverUrl.setText(settings.getString("serverURL",""));
        serverUrl.setSelection(serverUrl.getText().length());
        editTextListener(serverUrl);
        positiveAction.setEnabled(serverUrl.getText().length() > 0 && URLUtil.isValidUrl(serverUrl.getText().toString()));
        dialog.show();
    }

    private void editTextListener(EditText serverUrl) {
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
    }

    public boolean isServerReachable(final String url) {
        ful.get(url + "/_all_dbs").responseString(new Handler<String>() {
            @Override
            public void success(Request request, Response response, String s) {
                try {
                    List<String> myList = new ArrayList<String>();
                    myList.clear();
                    myList = Arrays.asList(s.split(","));
                    if (myList.size() < 8) {
                        alertDialogOkay("Check the server address again. What i connected to wasn't the BeLL Server");
                    } else {
                        alertDialogOkay("Test successful. You can now click on \"Save and Proceed\" ");
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString("serverURL", url).commit();
                    }
                } catch (Exception e) {e.printStackTrace();}
            }
            @Override
            public void failure(Request request, Response response, FuelError fuelError) {
                Log.d("fuelError", fuelError.toString());
                alertDialogOkay("Device couldn't reach server. Check and try again");
            }
        });
        return connectionResult;
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


}
