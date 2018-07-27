package org.ole.planet.takeout;

import android.content.Intent;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class TextFileViewerActivity extends AppCompatActivity {

    private TextView mTextFileNameTitle;
    private TextView mTextFileContent;
    private String filePath;
    private String fileName;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_textfile_viewer);
        declareElements();
        renderTextFile();
    }

    public TextFileViewerActivity()
    {
        filePath = new DashboardFragment().globalFilePath;
    }

    private void declareElements() {
        mTextFileNameTitle = (TextView) findViewById(R.id.textFileName);
        mTextFileContent = (TextView) findViewById(R.id.textFileContent);
    }

    private void renderTextFile() {
        Intent textFileOpenIntent = getIntent();
        fileName = textFileOpenIntent.getStringExtra("TOUCHED_FILE");
        Log.i("THE_FILE", filePath);

        if (fileName != null || fileName != "") {
            mTextFileNameTitle.setText(fileName);
            mTextFileNameTitle.setVisibility(View.VISIBLE);
        } else {
            mTextFileNameTitle.setVisibility(View.INVISIBLE);
        }

        renderTextFileThread();
    }

    private void renderTextFileThread() {
        Thread openTextFileThread = new Thread() {
            @Override
            public void run() {
                try {
                    File file = new File(filePath, fileName);
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