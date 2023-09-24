package org.ole.planet.myplanet.ui.viewer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import org.ole.planet.myplanet.databinding.ActivityTextfileViewerBinding;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class TextFileViewerActivity extends AppCompatActivity {
    private ActivityTextfileViewerBinding activityTextfileViewerBinding;
    private String fileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityTextfileViewerBinding = ActivityTextfileViewerBinding.inflate(getLayoutInflater());
        setContentView(activityTextfileViewerBinding.getRoot());
        renderTextFile();
    }

    private void renderTextFile() {
        Intent textFileOpenIntent = getIntent();
        fileName = textFileOpenIntent.getStringExtra("TOUCHED_FILE");
        if (fileName != null && !fileName.isEmpty()) {
            activityTextfileViewerBinding.textFileName.setText(fileName);
            activityTextfileViewerBinding.textFileName.setVisibility(View.VISIBLE);
        }
        renderTextFileThread();
    }
    private void renderTextFileThread() {
        Thread openTextFileThread = new Thread() {
            @Override
            public void run() {
                try {
                    File basePath = getExternalFilesDir(null);
                    File file = new File(basePath, "ole/" + fileName);

                    StringBuilder text = new StringBuilder();

                    BufferedReader reader = new BufferedReader(new FileReader(file));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        text.append(line);
                        text.append('\n');
                    }
                    reader.close();
                    activityTextfileViewerBinding.textFileContent.setText(text.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        openTextFileThread.start();
    }
}