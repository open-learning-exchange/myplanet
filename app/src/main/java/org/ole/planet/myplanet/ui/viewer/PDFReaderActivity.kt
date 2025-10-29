package org.ole.planet.myplanet.ui.viewer

import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityPdfreaderBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.repository.LibraryRepository
import org.ole.planet.myplanet.repository.MyPersonalRepository
import org.ole.planet.myplanet.service.AudioRecorderService
import org.ole.planet.myplanet.service.AudioRecorderService.AudioRecordListener
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.resources.AddResourceFragment
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.IntentUtils.openAudioFile
import org.ole.planet.myplanet.utilities.NotificationUtils.cancelAll
import org.ole.planet.myplanet.utilities.NotificationUtils.create
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class PDFReaderActivity : AppCompatActivity(), AudioRecordListener {
    private lateinit var binding: ActivityPdfreaderBinding
    private lateinit var audioRecorderService: AudioRecorderService
    private var fileName: String? = null
    @Inject
    lateinit var myPersonalRepository: MyPersonalRepository
    @Inject
    lateinit var libraryRepository: LibraryRepository
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    private lateinit var library: RealmMyLibrary
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfreaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, binding.root)
        audioRecorderService = AudioRecorderService().setAudioRecordListener(this)
        audioRecorderService.setCaller(this, this)
        if (intent.hasExtra("resourceId")) {
            val resourceID = intent.getStringExtra("resourceId")
            lifecycleScope.launch {
                resourceID?.let {
                    library = libraryRepository.getLibraryItemById(it)!!
                }
            }
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
                val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val pdfRenderer = PdfRenderer(fileDescriptor)
                val page = pdfRenderer.openPage(0)
                val bitmap = createBitmap(page.width, page.height)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val imageView = ImageView(this)
                imageView.setImageBitmap(bitmap)
                imageView.scaleType = ImageView.ScaleType.FIT_CENTER

                binding.pdfPlaceholder.visibility = View.GONE
                val parent = binding.pdfPlaceholder.parent as android.view.ViewGroup
                parent.addView(imageView, android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0))
                (imageView.layoutParams as android.widget.LinearLayout.LayoutParams).weight = 1f

                page.close()
                pdfRenderer.close()
                fileDescriptor.close()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(applicationContext, getString(R.string.unable_to_load) + fileName, Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(applicationContext, "File not found: $fileName", Toast.LENGTH_LONG)
                .show()
        }
    }


    override fun onRecordStarted() {
        Utilities.toast(this, getString(R.string.recording_started))
        create(this, R.drawable.ic_mic, "Recording Audio", getString(R.string.ole_is_recording_audio))
        binding.fabRecord.setImageResource(R.drawable.ic_stop)
    }

    override fun onRecordStopped(outputFile: String?) {
        Utilities.toast(this, getString(R.string.recording_stopped))
        cancelAll(this)
        updateTranslation(outputFile)
        val userModel = userProfileDbHandler.userModel
        if (userModel != null) {
            AddResourceFragment.showAlert(
                this,
                outputFile,
                myPersonalRepository,
                userModel.id,
                userModel._id,
                userModel.name
            )
        }
        binding.fabRecord.setImageResource(R.drawable.ic_mic)
    }

    private fun updateTranslation(outputFile: String?) {
        if (this::library.isInitialized) {
            lifecycleScope.launch {
                libraryRepository.updateLibraryItem(library.id!!) {
                    it.translationAudioPath = outputFile
                }
                library.translationAudioPath = outputFile
                Utilities.toast(
                    this@PDFReaderActivity,
                    getString(R.string.audio_file_saved_in_database)
                )
            }
        }
    }

    override fun onDestroy() {
        if (this::audioRecorderService.isInitialized && audioRecorderService.isRecording()) {
            audioRecorderService.stopRecording()
        }
        userProfileDbHandler.onDestroy()
        super.onDestroy()
    }

    override fun onError(error: String?) {
        cancelAll(this)
        Utilities.toast(this, error)
        binding.fabRecord.setImageResource(R.drawable.ic_mic)
    }
}
