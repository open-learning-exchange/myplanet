package org.ole.planet.myplanet.ui.viewer;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class TextFileViewerActivity extends AppCompatActivity {

    private TextView mTextFileNameTitle;
    private TextView mTextFileContent;
    private String fileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_textfile_viewer);
        declareElements();
        renderTextFile();
    }

    private void declareElements() {
        mTextFileNameTitle = (TextView) findViewById(R.id.textFileName);
        mTextFileContent = (TextView) findViewById(R.id.textFileContent);
    }

    private void renderTextFile() {
        Intent textFileOpenIntent = getIntent();
        fileName = textFileOpenIntent.getStringExtra("TOUCHED_FILE");

        if (fileName != null && !fileName.isEmpty()) {
            mTextFileNameTitle.setText(fileName);
            mTextFileNameTitle.setVisibility(View.VISIBLE);
        }

        renderTextFileThread();
    }

    private void renderTextFileThread() {
        Thread openTextFileThread = new Thread() {
            @Override
            public void run() {
                try {
                    File file = new File(Utilities.SD_PATH, fileName);
                    StringBuilder text = new StringBuilder();

                    BufferedReader reader = new BufferedReader(new FileReader(file));
                    String line;

                    while ((line = reader.readLine()) != null) {
                        text.append(line);
                        text.append('\n');
                    }
                    reader.close();
                    mTextFileContent.setText(text.toString());

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        openTextFileThread.start();
    }
}