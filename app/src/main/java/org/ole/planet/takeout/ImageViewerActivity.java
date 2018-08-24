package org.ole.planet.takeout;

import android.content.Intent;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;

import java.io.File;

public class ImageViewerActivity extends AppCompatActivity {

    private String fileName;
    private String filePath;
    private TextView mImageFileNameTitle;
    private ImageView mImageViewer;
    String url, auth = "";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        Intent intent = getIntent();
        url = intent.getStringExtra("url");
        auth = intent.getStringExtra("auth");

        declareElements();
        renderImageFile();
    }

    public ImageViewerActivity()
    {
        filePath = new DashboardFragment().globalFilePath;
    }

    private void declareElements() {
        mImageFileNameTitle = (TextView) findViewById(R.id.imageFileName);
        mImageViewer = (ImageView) findViewById(R.id.imageViewer);
    }

    private void renderImageFile() {
        // File name to be viewed
        Log.e("Hello","IMAGE");
        Intent imageOpenIntent = getIntent();
        fileName = imageOpenIntent.getStringExtra("TOUCHED_FILE");

        if (fileName != null && !fileName.isEmpty()) {
            mImageFileNameTitle.setText(fileName);
            mImageFileNameTitle.setVisibility(View.VISIBLE);
        }

        try{
            GlideUrl glideUrl = new GlideUrl(url, new LazyHeaders.Builder()
                    .addHeader("Accept", "application/json")
                    .addHeader("Cookie", auth)
                    .build());
            Glide.with(getApplicationContext())
                    .load(glideUrl)
                    .into(mImageViewer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
