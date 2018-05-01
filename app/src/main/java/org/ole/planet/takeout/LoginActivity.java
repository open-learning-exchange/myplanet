package org.ole.planet.takeout;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
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


public class LoginActivity extends AppCompatActivity {
    private EditText inputName, inputPassword;
    private TextInputLayout inputLayoutName, inputLayoutPassword;
    private Button btnSignIn;
    private ImageButton imgBtnSetting;
    Context context;
    private View positiveAction;
    dbSetup dbsetup =  new dbSetup();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        context = this.getApplicationContext();
        ImageView logo = findViewById(R.id.logoImageView);
        final int newColor = getResources().getColor(android.R.color.white);
        int alphaWhite = adjustAlpha(newColor,10);
        logo.setColorFilter(alphaWhite, PorterDuff.Mode.SRC_ATOP);
        decleareElements();
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
        dbsetup.dbSetup(this.context);

    }

    public void decleareElements(){
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
        }
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }
        public void afterTextChanged(Editable editable) {
            switch (view.getId()) {
                case R.id.input_name:
                    validateEditText(inputName,inputLayoutName,getString(R.string.err_msg_name));
                    break;
                case R.id.input_password:
                    validateEditText(inputPassword,inputLayoutPassword,getString(R.string.err_msg_password));
                    break;
            }
        }
    }

    public void  settingDialog(){
        boolean wrapInScrollView = true;
        MaterialDialog.Builder builder = new MaterialDialog.Builder(LoginActivity.this)
                .title(R.string.action_settings)
                .customView(R.layout.dialog_server_url, wrapInScrollView)
                .positiveText(R.string.btn_connect);
        MaterialDialog dialog = builder.build();
        positiveAction = dialog.getActionButton(DialogAction.POSITIVE);
        EditText serverUrl = dialog.getCustomView().findViewById(R.id.input_server_url);
        serverUrl.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        positiveAction.setEnabled(s.toString().trim().length() > 0 && URLUtil.isValidUrl(s.toString()));
                    }

                    @Override
                    public void afterTextChanged(Editable s) {}
        });
        positiveAction.setEnabled(false);
        dialog.show();
    }

}
