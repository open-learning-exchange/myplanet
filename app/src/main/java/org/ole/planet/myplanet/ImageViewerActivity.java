package org.ole.planet.myplanet;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.io.File;

public class ImageViewerActivity extends AppCompatActivity {

    private String fileName;
    private String filePath;
    private TextView mImageFileNameTitle;
    private ImageView mImageViewer;

    public ImageViewerActivity() {
        filePath = new DashboardFragment().globalFilePath;
    }

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

        Intent imageOpenIntent = getIntent();
        fileName = imageOpenIntent.getStringExtra("TOUCHED_FILE");

        if (fileName != null && !fileName.isEmpty()) {
            mImageFileNameTitle.setText(fileName);
            mImageFileNameTitle.setVisibility(View.VISIBLE);
        }

        try {
            Glide.with(getApplicationContext())
                    .load(new File(filePath, fileName))
                    .into(mImageViewer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
