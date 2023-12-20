package org.ole.planet.myplanet.ui.viewer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.ActivityMarkdownViewerBinding;
import org.ole.planet.myplanet.utilities.Markdown;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

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
                String markdownContent = readMarkdownFile(markdownFile);
                Markdown.INSTANCE.setMarkdownText(activityMarkdownViewerBinding.markdownView, markdownContent);
            } else {
                Toast.makeText(this, getString(R.string.unable_to_load) + fileName, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private String readMarkdownFile(File file) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        StringBuilder content = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }

        reader.close();
        return content.toString();
    }
}