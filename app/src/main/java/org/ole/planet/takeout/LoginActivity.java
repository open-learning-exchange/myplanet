package org.ole.planet.takeout;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import static java.security.AccessController.getContext;

public class LoginActivity extends AppCompatActivity {

    private EditText inputName, inputPassword;
    private TextInputLayout inputLayoutName, inputLayoutEmail, inputLayoutPassword;
    private Button btnSignIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        ImageView logo = findViewById(R.id.logoImageView);
        animateImageView(logo);

        inputLayoutName = findViewById(R.id.input_layout_name);
        inputLayoutPassword = findViewById(R.id.input_layout_password);
        inputName = findViewById(R.id.input_name);
        inputPassword = findViewById(R.id.input_password);
        btnSignIn = findViewById(R.id.btn_signin);

        inputName.addTextChangedListener(new MyTextWatcher(inputName));
        inputPassword.addTextChangedListener(new MyTextWatcher(inputPassword));

        btnSignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                submitForm();
            }
        });


    }

    public void animateImageView(final ImageView v) {
        final int newColor = getResources().getColor(android.R.color.white);
        final ValueAnimator colorAnim = ObjectAnimator.ofFloat(0f, 1f);
        colorAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float mul = (Float) animation.getAnimatedValue();
                int alphaWhite = adjustAlpha(newColor, mul);
                v.setColorFilter(alphaWhite, PorterDuff.Mode.SRC_ATOP);
                if (mul == 0.0) {
                    v.setColorFilter(null);
                }
            }
        });
        colorAnim.setDuration(1000);
        colorAnim.setRepeatMode(ValueAnimator.REVERSE);
        colorAnim.setRepeatCount(-1);
        colorAnim.start();

    }
    public int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    /**
     * Validating form
     */
    private void submitForm() {
        if (!validateName()) {
            return;
        }
        if (!validatePassword()) {
            return;
        }
        Toast.makeText(getApplicationContext(), "Thank You!", Toast.LENGTH_SHORT).show();
    }

    private boolean validateName() {
        if (inputName.getText().toString().trim().isEmpty()) {
            inputLayoutName.setError(getString(R.string.err_msg_name));
            requestFocus(inputName);
            return false;
        } else {
            inputLayoutName.setErrorEnabled(false);
        }
        return true;
    }

    private boolean validatePassword() {
        if (inputPassword.getText().toString().trim().isEmpty()) {
            inputLayoutPassword.setError(getString(R.string.err_msg_password));
            requestFocus(inputPassword);
            return false;
        } else {
            inputLayoutPassword.setErrorEnabled(false);
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
                    validateName();
                    break;
                case R.id.input_password:
                    validatePassword();
                    break;
            }
        }
    }
}
