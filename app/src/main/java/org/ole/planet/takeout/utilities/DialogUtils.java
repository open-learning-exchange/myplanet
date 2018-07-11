package org.ole.planet.takeout.utilities;

import android.content.Context;

import com.afollestad.materialdialogs.MaterialDialog;

import org.ole.planet.takeout.R;

public class DialogUtils {
    public static MaterialDialog.Builder getDowloadDialog(Context context) {
        return new MaterialDialog.Builder(context)
                .title(R.string.download_suggestion)
                .positiveText(R.string.download_selected)
                .negativeText(R.string.txt_cancel)
                .neutralText(R.string.download_all)
                .alwaysCallMultiChoiceCallback();
    }
}
