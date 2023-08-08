package org.ole.planet.myplanet.ui.viewer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;

import br.tiagohm.markdownview.MarkdownView;

public class MarkdownViewerActivity extends AppCompatActivity {
    private TextView mMarkdownNameTitle;
    private MarkdownView mMarkdownContent;
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
    }

    private void renderMarkdownFile() {
        Intent markdownOpenIntent = getIntent();
        String fileName = markdownOpenIntent.getStringExtra("TOUCHED_FILE");

        if (fileName != null && !fileName.isEmpty()) {
            mMarkdownNameTitle.setText(fileName);
            mMarkdownNameTitle.setVisibility(View.VISIBLE);
        }

        try {
            File basePath = getExternalFilesDir(null);
            File markdownFile = new File(basePath, "ole/" + fileName);

            if (markdownFile.exists()) {
                mMarkdownContent.loadMarkdownFromFile(markdownFile);
            } else {
                Toast.makeText(this, getString(R.string.unable_to_load) + fileName, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}