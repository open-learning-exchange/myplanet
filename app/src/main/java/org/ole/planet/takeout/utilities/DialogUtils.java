package org.ole.planet.takeout.utilities;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import org.ole.planet.takeout.datamanager.MyDownloadService;
import java.util.ArrayList;


public class DialogUtils {

    public static ProgressDialog getProgressDialog(final Context context) {
        final ProgressDialog prgDialog = new ProgressDialog(context);
        prgDialog.setTitle("Downloading file...");
        prgDialog.setMax(100);
        prgDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        prgDialog.setCancelable(false);
        prgDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Dismiss", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                prgDialog.dismiss();
            }
        });
        prgDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Stop Download", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                context.stopService(new Intent(context, MyDownloadService.class));
            }
        });
        return prgDialog;
    }

    public static void handleCheck(ArrayList<Integer> selectedItemsList, boolean b, Integer i) {
        if (b) {
            selectedItemsList.add(i);
        } else if (selectedItemsList.contains(i)) {
            selectedItemsList.remove(i);
        }
    }

    public static void showError(ProgressDialog prgDialog, String message) {
        prgDialog.setTitle(message);
        if (prgDialog.getButton(ProgressDialog.BUTTON_NEGATIVE) != null)
            prgDialog.getButton(ProgressDialog.BUTTON_NEGATIVE).setEnabled(false);
    }
}
