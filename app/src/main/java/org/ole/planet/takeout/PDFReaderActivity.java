package org.ole.planet.takeout;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import com.github.barteksc.pdfviewer.PDFView;

public class PDFReaderActivity extends AppCompatActivity {
    private TextView mFileNameTitle;
    private String fileName;
    private PDFView pdfView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdfreader);
        // Declare variables
        declareElements();
        renderPdfFile();
    }

    private void declareElements() {
        mFileNameTitle = (TextView) findViewById(R.id.pdfFileName);
        pdfView = (PDFView) findViewById(R.id.pdfView);
    }

    private void renderPdfFile() {
        // File name to be viewed
        fileName = "136.pdf";

        if (fileName != null || fileName != "") {
            mFileNameTitle.setText(fileName);
            mFileNameTitle.setVisibility(View.VISIBLE);
        }

        else {
            mFileNameTitle.setVisibility(View.INVISIBLE);
        }

        pdfView.fromAsset(fileName).enableSwipe(true).load();
    }
}
