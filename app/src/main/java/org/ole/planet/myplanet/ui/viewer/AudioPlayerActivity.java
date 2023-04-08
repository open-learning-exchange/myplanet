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
    ActivityAudioPlayerBinding binding;
    ArrayList<JcAudio> jcAudios;
    boolean isFullPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAudioPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String filePath = getIntent().getStringExtra("TOUCHED_FILE");
        jcAudios = new ArrayList<>();
        isFullPath = getIntent().getBooleanExtra("isFullPath", false);
        String fullPath = String.valueOf(new File(Utilities.SD_PATH, filePath));
        if (isFullPath) fullPath = String.valueOf(new File(filePath));
        jcAudios.add(JcAudio.createFromFilePath(fullPath));
        binding.jcplayer.initPlaylist(jcAudios, null);
        binding.jcplayer.getRootView().findViewById(R.id.btnNext).setVisibility(View.GONE);
        binding.jcplayer.getRootView().findViewById(R.id.btnPrev).setVisibility(View.GONE);
        binding.jcplayer.getRootView().findViewById(R.id.btnRepeatOne).setVisibility(View.GONE);
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
        if (binding.jcplayer != null && binding.jcplayer.isPlaying()) {
            binding.jcplayer.pause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (binding.jcplayer != null)
            binding.jcplayer.kill();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding.jcplayer != null && jcAudios.size() > 0) {
            binding.jcplayer.playAudio(jcAudios.get(0));
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
        Utilities.toast(this, "Unable to play audio.");
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
