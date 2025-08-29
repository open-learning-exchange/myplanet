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
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import java.io.File
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityPdfreaderBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.service.AudioRecorderService
import org.ole.planet.myplanet.service.AudioRecorderService.AudioRecordListener
import org.ole.planet.myplanet.ui.resources.AddResourceFragment
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.IntentUtils.openAudioFile
import org.ole.planet.myplanet.utilities.NotificationUtils.cancelAll
import org.ole.planet.myplanet.utilities.NotificationUtils.create
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils

@AndroidEntryPoint
class PDFReaderActivity : AppCompatActivity(), OnPageChangeListener, OnLoadCompleteListener, OnPageErrorListener, AudioRecordListener {
    private lateinit var binding: ActivityPdfreaderBinding
    private lateinit var audioRecorderService: AudioRecorderService
    private var fileName: String? = null
    @Inject
    lateinit var databaseService: DatabaseService
    private lateinit var library: RealmMyLibrary
    private lateinit var mRealm: Realm
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfreaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdgeWithNoPadding(this, binding.root)
        audioRecorderService = AudioRecorderService().setAudioRecordListener(this)
        audioRecorderService.setCaller(this, this)
        mRealm = databaseService.realmInstance
        if (intent.hasExtra("resourceId")) {
            val resourceID = intent.getStringExtra("resourceId")
            library = mRealm.where(RealmMyLibrary::class.java).equalTo("id", resourceID).findFirst()!!
        }
        renderPdfFile()
        binding.fabRecord.setOnClickListener { audioRecorderService.onRecordClicked()}
        binding.fabPlay.setOnClickListener {
            if (this::library.isInitialized && !TextUtils.isEmpty(library.translationAudioPath)) {
                openAudioFile(this, library.translationAudioPath)
            }
        }
    }

    private fun renderPdfFile() {
        val pdfOpenIntent = intent
        fileName = pdfOpenIntent.getStringExtra("TOUCHED_FILE")
        if (!fileName.isNullOrEmpty()) {
            binding.pdfFileName.text = FileUtils.nameWithoutExtension(fileName)
            binding.pdfFileName.visibility = View.VISIBLE
        } else {
            binding.pdfFileName.text = getString(R.string.message_placeholder, "No file selected")
            binding.pdfFileName.visibility = View.VISIBLE
        }
        val file = File(getExternalFilesDir(null), "ole/$fileName")
        if (file.exists()) {
            try {
                binding.pdfView.fromFile(file).defaultPage(0)
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
        binding.fabRecord.setImageResource(R.drawable.ic_stop)
    }

    override fun onRecordStopped(outputFile: String?) {
        Utilities.toast(this, getString(R.string.recording_stopped))
        cancelAll(this)
        updateTranslation(outputFile)
        AddResourceFragment.showAlert(this, outputFile, databaseService)
        binding.fabRecord.setImageResource(R.drawable.ic_mic)
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
        if (this::audioRecorderService.isInitialized && audioRecorderService.isRecording()) {
            audioRecorderService.stopRecording()
        }
        if (this::mRealm.isInitialized && !mRealm.isClosed) {
            if (mRealm.isInTransaction) {
                try {
                    mRealm.commitTransaction()
                } catch (e: Exception) {
                    mRealm.cancelTransaction()
                }
            }
            mRealm.close()
        }
        super.onDestroy()
    }

    override fun onError(error: String?) {
        cancelAll(this)
        Utilities.toast(this, error)
        binding.fabRecord.setImageResource(R.drawable.ic_mic)
    }
}
