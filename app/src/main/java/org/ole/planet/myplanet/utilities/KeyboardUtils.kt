package org.ole.planet.myplanet.utilities

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

object KeyboardUtils {
    fun hideSoftKeyboard(activity: Activity) {
        try {
            val inputMethodManager = activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(activity.currentFocus?.windowToken, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

//    fun showSoftKeyboard(activity: Activity) {
//        val inputMethodManager =
//            activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
//        inputMethodManager.showSoftInputFromInputMethod(activity.currentFocus!!.windowToken, 0)
//    }

    fun setupUI(v: View, activity: Activity) {
        // Set up touch listener for non-text box views to hide keyboard.
        val onTouchListener = View.OnTouchListener { _: View?, _: MotionEvent? ->
            hideSoftKeyboard(activity)
            false
        }
        if (v !is EditText) {
            v.setOnTouchListener(onTouchListener)
        }

        //If a layout container, iterate over children and seed recursion.
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                val innerView = v.getChildAt(i)
                setupUI(innerView, activity)
            }
        }
    }
}
