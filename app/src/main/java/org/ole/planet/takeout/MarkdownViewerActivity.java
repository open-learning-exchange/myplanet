package org.ole.planet.takeout;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import br.tiagohm.markdownview.MarkdownView;


public class MarkdownViewerActivity extends AppCompatActivity {

    private TextView mMarkdownNameTitle;
    private MarkdownView mMarkdownContent;
    private String filePath;
    private String fileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_markdown_viewer);
        declareElements();
        renderMarkdownFile();

    }

    private void declareElements() {
        mMarkdownNameTitle = (TextView) findViewById(R.id.markdownFileName);
        mMarkdownContent = (MarkdownView) findViewById(R.id.markdown_view);
        filePath = new DashboardFragment().globalFilePath;
    }

    private void renderMarkdownFile() {
        // File name to be viewed

        Intent markdownOpenIntent = getIntent();
        fileName = markdownOpenIntent.getStringExtra("TOUCHED_FILE");

        if (fileName != null && !fileName.isEmpty()) {
            mMarkdownNameTitle.setText(fileName);
            mMarkdownNameTitle.setVisibility(View.VISIBLE);
        }

        mMarkdownContent.loadMarkdownFromFile(new File(filePath, fileName));
    }
}