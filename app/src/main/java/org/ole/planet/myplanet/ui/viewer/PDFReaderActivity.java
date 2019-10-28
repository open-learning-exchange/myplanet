package org.ole.planet.myplanet.ui.viewer;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener;
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;
import com.github.clans.fab.FloatingActionButton;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.service.AudioRecorderService;
import org.ole.planet.myplanet.ui.library.AddResourceFragment;
import org.ole.planet.myplanet.utilities.IntentUtils;
import org.ole.planet.myplanet.utilities.NotificationUtil;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;

import io.realm.Realm;

public class PDFReaderActivity extends AppCompatActivity implements OnPageChangeListener, OnLoadCompleteListener,
        OnPageErrorListener, AudioRecorderService.AudioRecordListener {

    private static final String TAG = "PDF Reader Log";
    private TextView mPdfFileNameTitle;
    private String fileName;
    private PDFView pdfView;
    private FloatingActionButton fabRecord, fabPlay;
    private AudioRecorderService audioRecorderService;
    private RealmMyLibrary library;
    private Realm mRealm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdfreader);
        audioRecorderService = new AudioRecorderService().setAudioRecordListener(this);
        mRealm = new DatabaseService(this).getRealmInstance();
        if (getIntent().hasExtra("resourceId")) {
            String resourceID = getIntent().getStringExtra("resourceId");
            library = mRealm.where(RealmMyLibrary.class).equalTo("id", resourceID).findFirst();
        }
        declareElements();
        renderPdfFile();
    }

    private void declareElements() {
        mPdfFileNameTitle = findViewById(R.id.pdfFileName);
        pdfView = findViewById(R.id.pdfView);
        fabRecord = findViewById(R.id.fab_record);
        fabPlay = findViewById(R.id.fab_play);
        fabRecord.setOnClickListener(view -> {
            if (audioRecorderService.isRecording()) {
                audioRecorderService.stopRecording();
            } else {
                audioRecorderService.startRecording();
            }
        });

        fabPlay.setOnClickListener(view -> {
            if (library!=null && !TextUtils.isEmpty(library.getTranslationAudioPath())){
                IntentUtils.openAudioFile(this, library.getTranslationAudioPath());
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
        if (outputFile != null) {
            updateTranslation(outputFile);
            AddResourceFragment.showAlert(this,outputFile);
        }
        fabRecord.setImageResource(R.drawable.ic_mic);
    }

    private void updateTranslation(String outputFile) {
        if (library != null) {
            if (!mRealm.isInTransaction())
                mRealm.beginTransaction();
            library.setTranslationAudioPath(outputFile);
            mRealm.commitTransaction();
            Utilities.toast(this, "Audio file saved in database.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioRecorderService != null && audioRecorderService.isRecording())
            audioRecorderService.stopRecording();
    }

    @Override
    public void onError(String error) {
        NotificationUtil.cancellAll(this);
        Utilities.toast(this, error);
        fabRecord.setImageResource(R.drawable.ic_mic);
    }
}
