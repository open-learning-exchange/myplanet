package org.ole.planet.myplanet.callback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.ole.planet.myplanet.model.Download;

import static org.ole.planet.myplanet.ui.dashboard.DashboardActivity.MESSAGE_PROGRESS;

public class ProgressBroadcastReceiver extends BroadcastReceiver {

    private OnProgressChange onProgressChange;

    public void setOnProgressChange(OnProgressChange onProgressChange) {
        this.onProgressChange = onProgressChange;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(MESSAGE_PROGRESS)) {
            Download download = intent.getParcelableExtra("download");
            if (onProgressChange != null) {
                onProgressChange.onProgressChange(String.format("Downloading file %d/%d KB\n%d%% Completed.", download.getCurrentFileSize(), download.getTotalFileSize(), download.getProgress()));
            }
        }
    }

    public interface OnProgressChange {
        void onProgressChange(String s);
    }
}
