package org.ole.planet.myplanet.utilities;

import android.content.Context;
import android.content.Intent;

import org.ole.planet.myplanet.ui.viewer.AudioPlayerActivity;

public class IntentUtils {

    public static void openAudioFile(Context context, String path) {
        Intent i = new Intent(context, AudioPlayerActivity.class);
        i.putExtra("isFullPath", true);
        i.putExtra("TOUCHED_FILE", path);
        context.startActivity(i);
    }
}
