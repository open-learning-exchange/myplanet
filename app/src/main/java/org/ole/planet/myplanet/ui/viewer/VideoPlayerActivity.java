package org.ole.planet.myplanet.ui.viewer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.FileDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;

import com.google.gson.Gson;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.ActivityExoPlayerVideoBinding;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.AuthSessionUpdater;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoPlayerActivity extends AppCompatActivity implements AuthSessionUpdater.AuthCallback {
    private ActivityExoPlayerVideoBinding activityExoPlayerVideoBinding;
    ExoPlayer exoPlayer;
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

        String videoType = null;
        if (extras != null) {
            videoType = extras.getString("videoType");
            videoURL = extras.getString("videoURL");
            Utilities.log("Video url " + videoURL);
            auth = extras.getString("Auth");
        }

        if (videoType != null) {
            if (videoType.equals("offline")) {
                prepareExoPlayerFromFileUri(videoURL);
            } else if (videoType.equals("online")) {
                new AuthSessionUpdater(this, settings);
            }
        }

        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (exoPlayer != null) {
                    exoPlayer.stop();
                }
                finish();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    @Override
    public void setAuthSession(@NonNull Map<String, ? extends List<String>> responseHeader) {
        Utilities.log("Error " + new Gson().toJson(responseHeader));
        String[] headerAuth = responseHeader.get("Set-Cookie").get(0).split(";");
        auth = headerAuth[0];
        runOnUiThread(() -> streamVideoFromUrl(videoURL, auth));
    }

    @Override
    public void onError(@NonNull String s) {
        runOnUiThread(() -> Utilities.toast(VideoPlayerActivity.this, getString(R.string.connection_failed_reason) + s));
    }

    @OptIn(markerClass = UnstableApi.class)
    public void streamVideoFromUrl(String videoUrl, String auth) {
        TrackSelector trackSelectorDef = new DefaultTrackSelector(this);
        exoPlayer = new ExoPlayer.Builder(this).setTrackSelector(trackSelectorDef).build();

        Uri videoUri = Uri.parse(videoUrl);

        Map<String, String> requestProperties = new HashMap<>();
        requestProperties.put("Cookie", auth);

        HttpDataSource.Factory defaultHttpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("ExoPlayer")
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(requestProperties);

        MediaSource mediaSource = new ProgressiveMediaSource.Factory(defaultHttpDataSourceFactory).createMediaSource(MediaItem.fromUri(videoUri));

        activityExoPlayerVideoBinding.exoPlayerSimple.setPlayer(exoPlayer);
        exoPlayer.setMediaSource(mediaSource);
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);
    }

    @OptIn(markerClass = UnstableApi.class)
    public void prepareExoPlayerFromFileUri(String uristring) {
        Uri uri = Uri.parse(uristring);
        exoPlayer = new ExoPlayer.Builder(this)
                .setTrackSelector(new DefaultTrackSelector(this))
                .setLoadControl(new DefaultLoadControl())
                .build();

        DataSpec dataSpec = new DataSpec(uri);
        final FileDataSource fileDataSource = new FileDataSource();
        try {
            fileDataSource.open(dataSpec);
        } catch (FileDataSource.FileDataSourceException e) {
            e.printStackTrace();
        }

        DataSource.Factory factory = () -> fileDataSource;
        MediaSource audioSource;
        if (fileDataSource.getUri() != null) {
            audioSource = new ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(fileDataSource.getUri()));
            activityExoPlayerVideoBinding.exoPlayerSimple.setPlayer(exoPlayer);
            exoPlayer.setMediaSource(audioSource);
            exoPlayer.prepare();
            exoPlayer.setPlayWhenReady(true);
        }
    }
}
