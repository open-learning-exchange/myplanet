package org.ole.planet.myplanet.ui.viewer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.gson.Gson;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.ActivityExoPlayerVideoBinding;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.AuthSessionUpdater;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;
import java.util.Map;

public class VideoPlayerActivity extends AppCompatActivity implements AuthSessionUpdater.AuthCallback {
    private ActivityExoPlayerVideoBinding activityExoPlayerVideoBinding;
    SimpleExoPlayer exoPlayer;
    String auth = "";
    String videoURL = "";
    SharedPreferences settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityExoPlayerVideoBinding = ActivityExoPlayerVideoBinding.inflate(getLayoutInflater());
        setContentView(activityExoPlayerVideoBinding.getRoot());
        settings = getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE);

        Intent intentExtras = getIntent();
        Bundle extras = intentExtras.getExtras();

        String videoType = extras.getString("videoType");
        videoURL = extras.getString("videoURL");
        Utilities.log("Video url " + videoURL);
        auth = extras.getString("Auth");

        if (videoType.equals("offline")) {
            prepareExoPlayerFromFileUri(videoURL);
        } else if (videoType.equals("online")) {
            new AuthSessionUpdater(this, settings);
        }
    }

    @Override
    public void setAuthSession(@NonNull Map<String, ? extends List<String>> responseHeader) {
        Utilities.log("Error " + new Gson().toJson(responseHeader));
        String[] headerauth = responseHeader.get("Set-Cookie").get(0).split(";");
        auth = headerauth[0];
        runOnUiThread(() -> streamVideoFromUrl(videoURL, auth));
    }

    @Override
    public void onError(String s) {
        runOnUiThread(() -> Utilities.toast(VideoPlayerActivity.this, getString(R.string.connection_failed_reason) + s));
    }

    public void streamVideoFromUrl(String videoUrl, String auth) {
        TrackSelector trackSelectorDef = new DefaultTrackSelector();
        exoPlayer= ExoPlayerFactory.newSimpleInstance(this, trackSelectorDef);

        Uri videoUri = Uri.parse(videoUrl);

        HttpDataSource.Factory defaultHttpDataSourceFactory = new DefaultHttpDataSourceFactory("ExoPlayer", null);
        defaultHttpDataSourceFactory.setDefaultRequestProperty("Cookie", auth);
        MediaSource mediaSource = new ProgressiveMediaSource.Factory(defaultHttpDataSourceFactory).createMediaSource(videoUri);

        activityExoPlayerVideoBinding.exoPlayerSimple.setPlayer(exoPlayer);
        exoPlayer.prepare(mediaSource);
        exoPlayer.setPlayWhenReady(true);
    }

    public void prepareExoPlayerFromFileUri(String uristring) {
        Uri uri = Uri.parse(uristring);
        exoPlayer = ExoPlayerFactory.newSimpleInstance(this, new DefaultTrackSelector(), new DefaultLoadControl());

        DataSpec dataSpec = new DataSpec(uri);
        final FileDataSource fileDataSource = new FileDataSource();
        try {
            fileDataSource.open(dataSpec);
        } catch (FileDataSource.FileDataSourceException e) {
            e.printStackTrace();
        }

        DataSource.Factory factory = () -> fileDataSource;
        MediaSource audioSource = new ExtractorMediaSource(fileDataSource.getUri(), factory, new DefaultExtractorsFactory(), null, null);

        activityExoPlayerVideoBinding.exoPlayerSimple.setPlayer(exoPlayer);
        exoPlayer.prepare(audioSource);
        exoPlayer.setPlayWhenReady(true);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (exoPlayer != null) exoPlayer.stop();
    }

}
