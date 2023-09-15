package org.ole.planet.myplanet.ui.viewer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import org.ole.planet.myplanet.databinding.ActivityImageViewerBinding;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImageViewerActivity extends AppCompatActivity {
    private ActivityImageViewerBinding activityImageViewerBinding;
    private boolean isFullPath = false;
    String fileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityImageViewerBinding = ActivityImageViewerBinding.inflate(getLayoutInflater());
        setContentView(activityImageViewerBinding.getRoot());
        renderImageFile();
    }

    private void renderImageFile() {
        isFullPath = getIntent().getBooleanExtra("isFullPath", false);
        Intent imageOpenIntent = getIntent();
        fileName = imageOpenIntent.getStringExtra("TOUCHED_FILE");
        if (fileName != null && !fileName.isEmpty()) {
            activityImageViewerBinding.imageFileName.setText(fileName);
            activityImageViewerBinding.imageFileName.setVisibility(View.VISIBLE);
        }

        if (fileName.matches(".*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}.*")) {
            displayCapturedImage();
        } else {
            try {
                File imageFile;
                if (isFullPath) {
                    imageFile = new File(fileName);
                } else {
                    File basePath = getExternalFilesDir(null);
                    imageFile = new File(basePath, "ole/" + fileName);
                }
                Glide.with(getApplicationContext()).load(imageFile).into(activityImageViewerBinding.imageViewer);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void displayCapturedImage() {
        Pattern uuidPattern = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/");
        Matcher matcher = uuidPattern.matcher(fileName);

        if (matcher.find()) {
            fileName = fileName.substring(matcher.group().length());
        }

        RequestOptions requestOptions = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true);

        Glide.with(this)
                .load(fileName)
                .apply(requestOptions)
                .into(activityImageViewerBinding.imageViewer);
    }
}
