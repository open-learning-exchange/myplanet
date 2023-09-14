package org.ole.planet.myplanet.ui.viewer;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.jean.jcplayer.JcPlayerManagerListener;
import com.example.jean.jcplayer.general.JcStatus;
import com.example.jean.jcplayer.model.JcAudio;

import org.jetbrains.annotations.NotNull;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.ActivityAudioPlayerBinding;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;
import java.util.ArrayList;

public class AudioPlayerActivity extends AppCompatActivity implements JcPlayerManagerListener {
    private ActivityAudioPlayerBinding activityAudioPlayerBinding;
    ArrayList<JcAudio> jcAudios;
    boolean isFullPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityAudioPlayerBinding = ActivityAudioPlayerBinding.inflate(getLayoutInflater());
        setContentView(activityAudioPlayerBinding.getRoot());
        String filePath = getIntent().getStringExtra("TOUCHED_FILE");
        jcAudios = new ArrayList<>();
        isFullPath = getIntent().getBooleanExtra("isFullPath", false);
        String fullPath;

        if (isFullPath) {
            fullPath = filePath;
        } else {
            File basePath = getExternalFilesDir(null);
            fullPath = new File(basePath, "ole/" + filePath).getAbsolutePath();
        }

        jcAudios.add(JcAudio.createFromFilePath(fullPath));
        activityAudioPlayerBinding.jcplayer.initPlaylist(jcAudios, null);
        activityAudioPlayerBinding.jcplayer.getRootView().findViewById(R.id.btnNext).setVisibility(View.GONE);
        activityAudioPlayerBinding.jcplayer.getRootView().findViewById(R.id.btnPrev).setVisibility(View.GONE);
        activityAudioPlayerBinding.jcplayer.getRootView().findViewById(R.id.btnRepeatOne).setVisibility(View.GONE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (activityAudioPlayerBinding.jcplayer != null && activityAudioPlayerBinding.jcplayer.isPlaying()) {
            activityAudioPlayerBinding.jcplayer.pause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (activityAudioPlayerBinding.jcplayer != null) activityAudioPlayerBinding.jcplayer.kill();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (activityAudioPlayerBinding.jcplayer != null && jcAudios.size() > 0) {
            activityAudioPlayerBinding.jcplayer.playAudio(jcAudios.get(0));
        }
    }

    @Override
    public void onCompletedAudio() {
    }

    @Override
    public void onContinueAudio(@NotNull JcStatus jcStatus) {
    }

    @Override
    public void onJcpError(@NotNull Throwable throwable) {
        Utilities.toast(this, getString(R.string.unable_to_play_audio));
    }

    @Override
    public void onPaused(@NotNull JcStatus jcStatus) {
    }

    @Override
    public void onPlaying(@NotNull JcStatus jcStatus) {
    }

    @Override
    public void onPreparedAudio(@NotNull JcStatus jcStatus) {
    }

    @Override
    public void onStopped(@NotNull JcStatus jcStatus) {
    }

    @Override
    public void onTimeChanged(@NotNull JcStatus jcStatus) {
    }
}
