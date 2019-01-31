package org.ole.planet.myplanet.ui.viewer;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import com.example.jean.jcplayer.JcPlayerManagerListener;
import com.example.jean.jcplayer.general.JcStatus;
import com.example.jean.jcplayer.model.JcAudio;
import com.example.jean.jcplayer.view.JcPlayerView;

import org.jetbrains.annotations.NotNull;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;
import java.util.ArrayList;

public class AudioPlayerActivity extends AppCompatActivity implements JcPlayerManagerListener {
    JcPlayerView jcplayer;
    ArrayList<JcAudio> jcAudios;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_player);
        jcplayer = findViewById(R.id.jcplayer);
        String filePath = getIntent().getStringExtra("TOUCHED_FILE");
        // String title = getIntent().getStringExtra("title");
        jcAudios = new ArrayList<>();
        Utilities.log("File " + String.valueOf(new File(Utilities.SD_PATH, filePath)));
        jcAudios.add(JcAudio.createFromFilePath(String.valueOf(new File(Utilities.SD_PATH, filePath))));
        jcplayer.initPlaylist(jcAudios, null);
        jcplayer.getRootView().findViewById(R.id.btnNext).setVisibility(View.GONE);
        jcplayer.getRootView().findViewById(R.id.btnPrev).setVisibility(View.GONE);
        jcplayer.getRootView().findViewById(R.id.btnRepeatOne).setVisibility(View.GONE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home){
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (jcplayer != null && jcplayer.isPlaying()) {
            jcplayer.pause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (jcplayer != null)
            jcplayer.kill();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (jcplayer != null && jcAudios.size() > 0) {
            jcplayer.playAudio(jcAudios.get(0));
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
