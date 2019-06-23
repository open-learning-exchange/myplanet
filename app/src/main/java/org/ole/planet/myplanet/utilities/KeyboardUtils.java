package org.ole.planet.myplanet.utilities;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.view.View.OnTouchListener;

public class KeyboardUtils {

    public static void hideSoftKeyboard(Activity activity) {
        InputMethodManager inputMethodManager =
                (InputMethodManager) activity.getSystemService(
                        Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(
                activity.getCurrentFocus().getWindowToken(), 0);
    }

    public static void showSoftKeyboard(Activity activity) {
        InputMethodManager inputMethodManager =
                (InputMethodManager) activity.getSystemService(
                        Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.showSoftInputFromInputMethod(
                activity.getCurrentFocus().getWindowToken(), 0);
    }

    public void setupUI(View v, Activity activity) {
        // Set up touch listener for non-text box views to hide keyboard.
        OnTouchListener onTouchListener= new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                hideSoftKeyboard(activity);
                return false;
            }
        };

        if (!(v instanceof EditText) && v != null) {
            v.setOnTouchListener(onTouchListener);
        }

        //If a layout container, iterate over children and seed recursion.
        if (v instanceof ViewGroup && v != null) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                View innerView = ((ViewGroup) v).getChildAt(i);
                setupUI(innerView, activity);
            }
        }

    }
}
