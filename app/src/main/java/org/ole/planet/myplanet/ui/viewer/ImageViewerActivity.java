package org.ole.planet.myplanet.ui.viewer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import org.ole.planet.myplanet.databinding.ActivityImageViewerBinding;

import java.io.File;

public class ImageViewerActivity extends AppCompatActivity {
    private ActivityImageViewerBinding activityImageViewerBinding;
    private boolean isFullPath = false;

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
        String fileName = imageOpenIntent.getStringExtra("TOUCHED_FILE");
        if (fileName != null && !fileName.isEmpty()) {
            activityImageViewerBinding.imageFileName.setText(fileName);
            activityImageViewerBinding.imageFileName.setVisibility(View.VISIBLE);
        }

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
