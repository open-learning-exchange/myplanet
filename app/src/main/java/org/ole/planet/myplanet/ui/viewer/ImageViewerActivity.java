package org.ole.planet.myplanet.ui.viewer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;

public class ImageViewerActivity extends AppCompatActivity {

    private TextView mImageFileNameTitle;
    private ImageView mImageViewer;
    private boolean isFullPath = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);
        declareElements();
        renderImageFile();
    }

    private void declareElements() {
        mImageFileNameTitle = (TextView) findViewById(R.id.imageFileName);
        mImageViewer = (ImageView) findViewById(R.id.imageViewer);
    }

    private void renderImageFile() {
        // File name to be viewed
        isFullPath = getIntent().getBooleanExtra("isFullPath", false);
        Intent imageOpenIntent = getIntent();
        String fileName = imageOpenIntent.getStringExtra("TOUCHED_FILE");

        if (fileName != null && !fileName.isEmpty()) {
            mImageFileNameTitle.setText(fileName);
            mImageFileNameTitle.setVisibility(View.VISIBLE);
        }

        try {
            Glide.with(getApplicationContext())
                    .load(isFullPath ? new File(fileName) : new File(Utilities.SD_PATH, fileName))
                    .into(mImageViewer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
