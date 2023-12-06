package org.ole.planet.myplanet.ui.viewer;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener;
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.ActivityPdfreaderBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.service.AudioRecorderService;
import org.ole.planet.myplanet.ui.library.AddResourceFragment;
import org.ole.planet.myplanet.utilities.IntentUtils;
import org.ole.planet.myplanet.utilities.NotificationUtil;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;

import io.realm.Realm;

public class PDFReaderActivity extends AppCompatActivity implements OnPageChangeListener, OnLoadCompleteListener, OnPageErrorListener, AudioRecorderService.AudioRecordListener {
    private ActivityPdfreaderBinding activityPdfreaderBinding;
    private static final String TAG = "PDF Reader Log";
    private String fileName;
    private AudioRecorderService audioRecorderService;
    private RealmMyLibrary library;
    private Realm mRealm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityPdfreaderBinding = ActivityPdfreaderBinding.inflate(getLayoutInflater());
        setContentView(activityPdfreaderBinding.getRoot());
        audioRecorderService = new AudioRecorderService().setAudioRecordListener(this);
        mRealm = new DatabaseService(this).getRealmInstance();
        if (getIntent().hasExtra("resourceId")) {
            String resourceID = getIntent().getStringExtra("resourceId");
            library = mRealm.where(RealmMyLibrary.class).equalTo("id", resourceID).findFirst();
        }
        renderPdfFile();

        activityPdfreaderBinding.fabRecord.setOnClickListener(view -> {
            if (audioRecorderService.isRecording()) {
                audioRecorderService.stopRecording();
            } else {
                audioRecorderService.startRecording();
            }
        });

        activityPdfreaderBinding.fabPlay.setOnClickListener(view -> {
            if (library != null && !TextUtils.isEmpty(library.translationAudioPath)) {
                IntentUtils.openAudioFile(this, library.translationAudioPath);
            }
        });
    }

    private void renderPdfFile() {
        Intent pdfOpenIntent = getIntent();
        fileName = pdfOpenIntent.getStringExtra("TOUCHED_FILE");
        if (fileName != null && !fileName.isEmpty()) {
            activityPdfreaderBinding.pdfFileName.setText(fileName);
            activityPdfreaderBinding.pdfFileName.setVisibility(View.VISIBLE);
        }

        File file = new File(getExternalFilesDir(null), "ole/" + fileName);
        if (file.exists()) {
            try {
                Utilities.log(file.getAbsolutePath());
                activityPdfreaderBinding.pdfView.fromFile(file).defaultPage(0).enableAnnotationRendering(true).onLoad(this).onPageChange(this).scrollHandle(new DefaultScrollHandle(this)).load();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(getApplicationContext(), getString(R.string.unable_to_load) + fileName, Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), "File not found: " + fileName, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void loadComplete(int nbPages) {}

    @Override
    public void onPageChanged(int page, int pageCount) {}

    @Override
    public void onPageError(int page, Throwable t) {
        Log.e(TAG, "Cannot load page " + page);
    }

    @Override
    public void onRecordStarted() {
        Utilities.toast(this, getString(R.string.recording_started));
        NotificationUtil.create(this, R.drawable.ic_mic, "Recording Audio", getString(R.string.ole_is_recording_audio));
        activityPdfreaderBinding.fabRecord.setImageResource(R.drawable.ic_stop);
    }

    @Override
    public void onRecordStopped(String outputFile) {
        Utilities.toast(this, getString(R.string.recording_stopped));
        NotificationUtil.cancellAll(this);
        if (outputFile != null) {
            updateTranslation(outputFile);
            AddResourceFragment.showAlert(this, outputFile);
        }
        activityPdfreaderBinding.fabRecord.setImageResource(R.drawable.ic_mic);
    }

    private void updateTranslation(String outputFile) {
        if (library != null) {
            if (!mRealm.isInTransaction()) mRealm.beginTransaction();
            library.translationAudioPath = outputFile;
            mRealm.commitTransaction();
            Utilities.toast(this, getString(R.string.audio_file_saved_in_database));
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
        activityPdfreaderBinding.fabRecord.setImageResource(R.drawable.ic_mic);
    }
}
