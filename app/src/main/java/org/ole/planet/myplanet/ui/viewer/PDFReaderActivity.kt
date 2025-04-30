package org.ole.planet.myplanet.ui.viewer

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityPdfreaderBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.service.AudioRecorderPermission
import org.ole.planet.myplanet.service.AudioRecorderService
import org.ole.planet.myplanet.service.AudioRecorderService.AudioRecordListener
import org.ole.planet.myplanet.ui.resources.AddResourceFragment
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.IntentUtils.openAudioFile
import org.ole.planet.myplanet.utilities.NotificationUtil.cancelAll
import org.ole.planet.myplanet.utilities.NotificationUtil.create
import org.ole.planet.myplanet.utilities.Utilities
import java.io.File

class PDFReaderActivity : AppCompatActivity(), OnPageChangeListener, OnLoadCompleteListener, OnPageErrorListener, AudioRecordListener {
    private lateinit var activityPdfReaderBinding: ActivityPdfreaderBinding
    private var fileName: String? = null
    private lateinit var recordingPermissionManager: AudioRecorderPermission
    private lateinit var audioRecorderService: AudioRecorderService
    private lateinit var library: RealmMyLibrary
    private lateinit var mRealm: Realm
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityPdfReaderBinding = ActivityPdfreaderBinding.inflate(layoutInflater)
        setContentView(activityPdfReaderBinding.root)
        audioRecorderService = AudioRecorderService().setAudioRecordListener(this)
        audioRecorderService.setCaller(this, this)
        mRealm = DatabaseService(this).realmInstance
        if (intent.hasExtra("resourceId")) {
            val resourceID = intent.getStringExtra("resourceId")
            library = mRealm.where(RealmMyLibrary::class.java).equalTo("id", resourceID).findFirst()!!
        }
        renderPdfFile()
        activityPdfReaderBinding.fabRecord.setOnClickListener { audioRecorderService.onRecordClicked()}
        activityPdfReaderBinding.fabPlay.setOnClickListener {
            if (this::library.isInitialized && !TextUtils.isEmpty(library.translationAudioPath)) {
                openAudioFile(this, library.translationAudioPath)
            }
        }
    }

    private fun renderPdfFile() {
        val pdfOpenIntent = intent
        fileName = pdfOpenIntent.getStringExtra("TOUCHED_FILE")
        if (!fileName.isNullOrEmpty()) {
            activityPdfReaderBinding.pdfFileName.text = FileUtils.nameWithoutExtension(fileName)
            activityPdfReaderBinding.pdfFileName.visibility = View.VISIBLE
        } else {
            activityPdfReaderBinding.pdfFileName.text = getString(R.string.message_placeholder, "No file selected")
            activityPdfReaderBinding.pdfFileName.visibility = View.VISIBLE
        }
        val file = File(getExternalFilesDir(null), "ole/$fileName")
        if (file.exists()) {
            try {
                activityPdfReaderBinding.pdfView.fromFile(file).defaultPage(0)
                    .enableAnnotationRendering(true).onLoad(this).onPageChange(this)
                    .scrollHandle(DefaultScrollHandle(this)).load()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(applicationContext, getString(R.string.unable_to_load) + fileName, Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(applicationContext, "File not found: $fileName", Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun loadComplete(nbPages: Int) {}
    override fun onPageChanged(page: Int, pageCount: Int) {}
    override fun onPageError(page: Int, t: Throwable) {}

    override fun onRecordStarted() {
        Utilities.toast(this, getString(R.string.recording_started))
        create(this, R.drawable.ic_mic, "Recording Audio", getString(R.string.ole_is_recording_audio))
        activityPdfReaderBinding.fabRecord.setImageResource(R.drawable.ic_stop)
    }

    override fun onRecordStopped(outputFile: String?) {
        Utilities.toast(this, getString(R.string.recording_stopped))
        cancelAll(this)
        updateTranslation(outputFile)
        AddResourceFragment.showAlert(this, outputFile)
        activityPdfReaderBinding.fabRecord.setImageResource(R.drawable.ic_mic)
    }

    private fun updateTranslation(outputFile: String?) {
        if (this::library.isInitialized) {
            if (!mRealm.isInTransaction) mRealm.beginTransaction()
            library.translationAudioPath = outputFile
            mRealm.commitTransaction()
            Utilities.toast(this, getString(R.string.audio_file_saved_in_database))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::audioRecorderService.isInitialized && audioRecorderService.isRecording()) {
            audioRecorderService.stopRecording()
        }
    }

    override fun onError(error: String?) {
        cancelAll(this)
        Utilities.toast(this, error)
        activityPdfReaderBinding.fabRecord.setImageResource(R.drawable.ic_mic)
    }
}
