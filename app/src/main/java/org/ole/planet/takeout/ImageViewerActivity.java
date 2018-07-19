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
import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;

import java.io.File;

public class ImageViewerActivity extends AppCompatActivity {

    private String fileName;
    private String filePath;
    private TextView mImageFileNameTitle;
    private ImageView mImageViewer;

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
        filePath = Environment.getExternalStorageDirectory() + File.separator + "ole" + File.separator;
    }

    private void renderImageFile() {
        // File name to be viewed

        Intent imageOpenIntent = getIntent();
        fileName = imageOpenIntent.getStringExtra("TOUCHED_FILE");
        Log.i("THE_FILE", filePath);

        if (fileName != null || fileName != "") {
            mImageFileNameTitle.setText(fileName);
            mImageFileNameTitle.setVisibility(View.VISIBLE);
        } else {
            mImageFileNameTitle.setVisibility(View.INVISIBLE);
        }

        try{
            Glide.with(getApplicationContext())
                    .load(new File(filePath, fileName))
                    .into(mImageViewer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
