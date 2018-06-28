package org.ole.planet.takeout;

import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener;
import com.github.barteksc.pdfviewer.listener.OnPageScrollListener;
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;
import com.github.barteksc.pdfviewer.util.FitPolicy;
import com.shockwave.pdfium.PdfDocument;

import org.ole.planet.takeout.Data.SourceFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.view.View.GONE;

public class PDFReaderActivity extends AppCompatActivity implements OnPageChangeListener, OnLoadCompleteListener,
        OnPageErrorListener {

    private TextView mFileNameTitle;
    private String fileName;
    private PDFView pdfView;
    private int currentPageNumber, totalPages;
    private static final String TAG = "PDF Reader Log";
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
        pdfView.fromAsset(fileName)
                .defaultPage(0)
                .enableAnnotationRendering(true)
                .onLoad(this)
                .onPageChange(this)
                .scrollHandle(new DefaultScrollHandle(this))
                .load();
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
