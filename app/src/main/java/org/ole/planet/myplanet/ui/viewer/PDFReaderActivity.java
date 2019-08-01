package org.ole.planet.myplanet.ui.viewer;

import android.content.Intent;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
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
import org.ole.planet.myplanet.service.AudioRecorderService;
import org.ole.planet.myplanet.utilities.NotificationUtil;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PDFReaderActivity extends AppCompatActivity implements OnPageChangeListener, OnLoadCompleteListener,
        OnPageErrorListener, AudioRecorderService.AudioRecordListener {

    private static final String TAG = "PDF Reader Log";
    private TextView mPdfFileNameTitle;
    private String fileName;
    private PDFView pdfView;
    private FloatingActionButton fabRecord;
    private AudioRecorderService audioRecorderService;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdfreader);
        audioRecorderService = new AudioRecorderService().setAudioRecordListener(this);
        declareElements();
        renderPdfFile();
    }

    private void declareElements() {
        mPdfFileNameTitle = findViewById(R.id.pdfFileName);
        pdfView = findViewById(R.id.pdfView);
        fabRecord = findViewById(R.id.fab_record);
        fabRecord.setOnClickListener(view -> {
            if (audioRecorderService.isRecording()){
                audioRecorderService.stopRecording();
            }else{
                audioRecorderService.startRecording();
            }
        });
    }

    private void renderPdfFile() {
        Intent pdfOpenIntent = getIntent();
        fileName = pdfOpenIntent.getStringExtra("TOUCHED_FILE");
        if (fileName != null && !fileName.isEmpty()) {
            mPdfFileNameTitle.setText(fileName);
            mPdfFileNameTitle.setVisibility(View.VISIBLE);
        }
        try {
            Utilities.log(new File(Utilities.SD_PATH, fileName).getAbsolutePath());
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
    }

    @Override
    public void onPageChanged(int page, int pageCount) {
    }

    @Override
    public void onPageError(int page, Throwable t) {
        Log.e(TAG, "Cannot load page " + page);
    }

    @Override
    public void onRecordStarted() {
        Utilities.toast(this, "Recording started....");
        NotificationUtil.create(this, R.drawable.ic_mic, "Recording Audio", "Ole is recording audio");
        fabRecord.setImageResource(R.drawable.ic_stop);
    }

    @Override
    public void onRecordStopped(String outputFile) {
        Utilities.toast(this, "Recording stopped.");
        NotificationUtil.cancellAll(this);
        fabRecord.setImageResource(R.drawable.ic_mic);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioRecorderService!=null && audioRecorderService.isRecording())
            audioRecorderService.stopRecording();
    }

    @Override
    public void onError(String error) {
        NotificationUtil.cancellAll(this);
        Utilities.toast(this, error);
        fabRecord.setImageResource(R.drawable.ic_mic);
    }
}
