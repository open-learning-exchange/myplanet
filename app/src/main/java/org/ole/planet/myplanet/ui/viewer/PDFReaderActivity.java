package org.ole.planet.myplanet.ui.viewer;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener;
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.SourceFile;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PDFReaderActivity extends AppCompatActivity implements OnPageChangeListener, OnLoadCompleteListener,
        OnPageErrorListener {

    private static final String TAG = "PDF Reader Log";
    private TextView mPdfFileNameTitle;
    private String fileName;
    private PDFView pdfView;
    private int currentPageNumber, totalPages;
    private String currentUsername = "admin";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdfreader);
        // Declare variables
        declareElements();
        renderPdfFile();
    }

    private void declareElements() {
        mPdfFileNameTitle = (TextView) findViewById(R.id.pdfFileName);
        pdfView = (PDFView) findViewById(R.id.pdfView);
    }

    private void renderPdfFile() {
        // File name to be viewed

        Intent pdfOpenIntent = getIntent();
        fileName = pdfOpenIntent.getStringExtra("TOUCHED_FILE");

        if (fileName != null && !fileName.isEmpty()) {
            mPdfFileNameTitle.setText(fileName);
            mPdfFileNameTitle.setVisibility(View.VISIBLE);
        }

        try {
            pdfView.fromFile(new File(Utilities.SD_PATH, fileName))
                    .defaultPage(0)
                    .enableAnnotationRendering(true)
                    .onLoad(this)
                    .onPageChange(this)
                    .scrollHandle(new DefaultScrollHandle(this))
                    .load();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "Unable to load " + fileName, Toast.LENGTH_LONG).show();
        }


    }

    @Override
    public void loadComplete(int nbPages) {
        totalPages = nbPages;
    }

    @Override
    public void onPageChanged(int page, int pageCount) {
        //Page contains che current page
        currentPageNumber = page;
    }

    // Saves the current page
    private void saveCurrentPage(int currentPage, String sourceName) {
        Map<String, List<SourceFile>> wordkey = new HashMap<>();
        // (source name, page number)
        // TODO SourceFile currentPDF = new SourceFile(fileName,); <---- pass the values the class requires)

        // Map it with current user
        // TODO wordkey.put(currentUsername, Arrays.asList(currentPDF));

        // then TODO update CouchDB
    }

    @Override
    public void onPageError(int page, Throwable t) {
        Log.e(TAG, "Cannot load page " + page);
    }

    // Save last page accessed when the user closes the pdf
    @Override
    public void onStop() {
        super.onStop();
        Log.e(TAG, "User left... saving at page " + currentPageNumber);
        saveCurrentPage(currentPageNumber, fileName);
    }
}
