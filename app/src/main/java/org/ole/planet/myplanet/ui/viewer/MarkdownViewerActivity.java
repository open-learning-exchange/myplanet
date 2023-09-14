package org.ole.planet.myplanet.ui.viewer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.ActivityMarkdownViewerBinding;

import java.io.File;

public class MarkdownViewerActivity extends AppCompatActivity {
    private ActivityMarkdownViewerBinding activityMarkdownViewerBinding;
    private String fileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityMarkdownViewerBinding = ActivityMarkdownViewerBinding.inflate(getLayoutInflater());
        setContentView(activityMarkdownViewerBinding.getRoot());
        renderMarkdownFile();
    }

    private void renderMarkdownFile() {
        Intent markdownOpenIntent = getIntent();
        String fileName = markdownOpenIntent.getStringExtra("TOUCHED_FILE");

        if (fileName != null && !fileName.isEmpty()) {
            activityMarkdownViewerBinding.markdownFileName.setText(fileName);
            activityMarkdownViewerBinding.markdownFileName.setVisibility(View.VISIBLE);
        }

        try {
            File basePath = getExternalFilesDir(null);
            File markdownFile = new File(basePath, "ole/" + fileName);

            if (markdownFile.exists()) {
                activityMarkdownViewerBinding.markdownView.loadMarkdownFromFile(markdownFile);
            } else {
                Toast.makeText(this, getString(R.string.unable_to_load) + fileName, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}