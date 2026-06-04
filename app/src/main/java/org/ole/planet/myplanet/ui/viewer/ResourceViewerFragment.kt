package org.ole.planet.myplanet.ui.viewer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.pdf.PdfRenderer
import android.media.AudioManager
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.registerReceiver
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnAudioRecordListener
import org.ole.planet.myplanet.data.auth.AuthSessionUpdater
import org.ole.planet.myplanet.databinding.FragmentResourceViewerBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.repository.PersonalsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.services.AudioRecorder
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.IntentUtils
import org.ole.planet.myplanet.utils.MarkdownUtils
import org.ole.planet.myplanet.utils.NotificationUtils
import org.ole.planet.myplanet.utils.TTSManager
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
class ResourceViewerFragment : Fragment(), AuthSessionUpdater.AuthCallback {

    enum class ResourceType {
        VIDEO, AUDIO, PDF, IMAGE, TEXT, MARKDOWN, CSV, UNKNOWN
    }

    private var _binding: FragmentResourceViewerBinding? = null
    private val binding get() = _binding!!

    private var resourceId: String? = null
    private var filePath: String? = null
    private var title: String? = null
    private var type: ResourceType = ResourceType.UNKNOWN
    private var isOnline: Boolean = false
    private var isFullPath: Boolean = false
    private var auth: String = ""

    private var exoPlayer: ExoPlayer? = null
    private var noisyReceiverRegistered = false
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var library: RealmMyLibrary
    private var pdfText: String = ""
    private var isExtractingText = false

    @Inject lateinit var personalsRepository: PersonalsRepository
    @Inject lateinit var resourcesRepository: ResourcesRepository
    @Inject lateinit var userSessionManager: UserSessionManager
    @Inject lateinit var dispatcherProvider: DispatcherProvider
    @Inject lateinit var ttsManager: TTSManager
    @Inject lateinit var authSessionUpdaterFactory: AuthSessionUpdater.Factory
    private var authSessionUpdater: AuthSessionUpdater? = null

    private val audioRecordListener = object : OnAudioRecordListener {
        override fun onRecordStarted() {
            Utilities.toast(requireContext(), getString(R.string.recording_started))
            NotificationUtils.create(requireContext(), R.drawable.ic_mic, "Recording Audio", getString(R.string.ole_is_recording_audio))
            binding.fabRecord.setImageResource(R.drawable.ic_stop)
        }

        override fun onRecordStopped(outputFile: String?) {
            Utilities.toast(requireContext(), getString(R.string.recording_stopped))
            NotificationUtils.cancelAll(requireContext())
            if (::library.isInitialized) {
                lifecycleScope.launch {
                    val id = library.id ?: return@launch
                    resourcesRepository.updateLibraryItem(id) { it.translationAudioPath = outputFile }
                }
            }
            binding.fabRecord.setImageResource(R.drawable.ic_mic)
        }

        override fun onError(error: String?) {
            Utilities.toast(requireContext(), "Recording error: ${error.orEmpty()}")
        }
    }

    private val audioBecomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                exoPlayer?.pause()
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) {
            Utilities.toast(requireContext(), getString(R.string.microphone_permission_required))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            resourceId = it.getString(ARG_RESOURCE_ID)
            filePath = it.getString(ARG_FILE_PATH)
            title = it.getString(ARG_TITLE)
            type = ResourceType.valueOf(it.getString(ARG_TYPE) ?: ResourceType.UNKNOWN.name)
            isOnline = it.getBoolean(ARG_IS_ONLINE, false)
            isFullPath = it.getBoolean(ARG_IS_FULL_PATH, false)
            auth = it.getString(ARG_AUTH) ?: ""
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResourceViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        audioRecorder = AudioRecorder().setAudioRecordListener(audioRecordListener)
        audioRecorder.setCaller(requireActivity(), requireContext())

        lifecycleScope.launch {
            resourceId?.let {
                library = resourcesRepository.getLibraryItemById(it) ?: return@launch
            }
            setupViewer()
        }
    }

    private fun setupViewer() {
        when (type) {
            ResourceType.VIDEO -> setupVideoViewer()
            ResourceType.AUDIO -> setupAudioViewer()
            ResourceType.PDF -> setupPdfViewer()
            ResourceType.IMAGE -> setupImageViewer()
            ResourceType.TEXT, ResourceType.MARKDOWN, ResourceType.CSV -> setupTextViewer()
            else -> Utilities.toast(requireContext(), "Unsupported file type")
        }
    }

    private fun setupVideoViewer() {
        binding.stubVideo.visibility = View.VISIBLE
        if (isOnline) {
            authSessionUpdater = authSessionUpdaterFactory.create(this)
        } else {
            prepareVideoPlayer(filePath)
        }
        
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(requireContext(), audioBecomingNoisyReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        noisyReceiverRegistered = true
    }

    @OptIn(UnstableApi::class)
    private fun prepareVideoPlayer(uriString: String?) {
        val uri = uriString?.toUri() ?: return
        val dataSpec = DataSpec(uri)
        val fileDataSource = FileDataSource()
        try {
            fileDataSource.open(dataSpec)
        } catch (e: FileDataSource.FileDataSourceException) {
            e.printStackTrace()
            return
        }
        val factory = DataSource.Factory { fileDataSource }
        val fileUri = fileDataSource.uri ?: return

        val trackSelector = DefaultTrackSelector(requireContext())
        exoPlayer = ExoPlayer.Builder(requireContext())
            .setTrackSelector(trackSelector)
            .setLoadControl(DefaultLoadControl())
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .build()

        val playerView = binding.root.findViewById<PlayerView>(R.id.video_player)
        playerView.player = exoPlayer

        val audioSource = ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(fileUri))
        exoPlayer?.apply {
            setMediaSource(audioSource)
            prepare()
            playWhenReady = true
        }
    }

    @OptIn(UnstableApi::class)
    private fun streamVideoFromUrl(videoUrl: String, authCookie: String) {
        val uri = videoUrl.toUri()
        val requestProperties = hashMapOf("Cookie" to authCookie)
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("ExoPlayer")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(requestProperties)

        val mediaSource: MediaSource = ProgressiveMediaSource.Factory(httpDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uri))

        val trackSelector = DefaultTrackSelector(requireContext())
        exoPlayer = ExoPlayer.Builder(requireContext())
            .setTrackSelector(trackSelector)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .build()

        val playerView = binding.root.findViewById<PlayerView>(R.id.video_player)
        playerView.player = exoPlayer
        exoPlayer?.apply {
            setMediaSource(mediaSource)
            playWhenReady = true
            prepare()
        }
    }

    private fun setupAudioViewer() {
        binding.stubAudio.visibility = View.VISIBLE
        val trackTitle = binding.root.findViewById<TextView>(R.id.trackTitle)
        val artistName = binding.root.findViewById<TextView>(R.id.artistName)
        val backgroundImage = binding.root.findViewById<ImageView>(R.id.backgroundImage)
        val playerView = binding.root.findViewById<PlayerView>(R.id.audio_player_view)

        trackTitle.text = FileUtils.nameWithoutExtension(filePath)
        artistName.text = title ?: "Unknown Artist"

        val isDarkMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val bgRes = if (isDarkMode) R.drawable.bg_player_dark else R.drawable.bg_player_white
        Glide.with(this).load(bgRes).into(backgroundImage)

        initializeAudioPlayer(playerView)
    }

    @OptIn(UnstableApi::class)
    private fun initializeAudioPlayer(playerView: PlayerView) {
        val fullPath = resolveAudioPath(filePath)
        exoPlayer = ExoPlayer.Builder(requireContext()).build().also { player ->
            playerView.player = player
            player.setMediaItem(MediaItem.fromUri(fullPath))
            player.prepare()
            player.playWhenReady = true

            val timeBar = playerView.findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)
            timeBar?.apply {
                setPlayedColor(ContextCompat.getColor(requireContext(), R.color.daynight_textColor))
                setScrubberColor(ContextCompat.getColor(requireContext(), R.color.daynight_textColor))
            }
        }
    }

    private fun resolveAudioPath(originalPath: String?): String {
        if (isFullPath) return originalPath ?: ""
        val processedPath = originalPath?.let {
            val uuidPattern = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/")
            val matcher = uuidPattern.matcher(it)
            if (matcher.find()) it.substring(matcher.end()) else it
        }
        return File(requireContext().getExternalFilesDir(null), "ole/$processedPath").absolutePath
    }

    private fun setupPdfViewer() {
        binding.stubPdf.visibility = View.VISIBLE
        binding.fabMenu.visibility = View.VISIBLE
        val pdfFileName = binding.root.findViewById<TextView>(R.id.pdfFileName)
        pdfFileName.text = title

        renderPdf()
        extractPdfText()
        setupPdfFabActions()
    }

    private fun renderPdf() {
        val file = File(requireContext().getExternalFilesDir(null), "ole/$filePath")
        if (file.exists()) {
            try {
                val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val pdfRenderer = PdfRenderer(fileDescriptor)
                val page = pdfRenderer.openPage(0)
                val bitmap = createBitmap(page.width, page.height)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val pdfPlaceholder = binding.root.findViewById<TextView>(R.id.pdfPlaceholder)
                pdfPlaceholder.visibility = View.GONE
                val parent = pdfPlaceholder.parent as ViewGroup
                val imageView = ImageView(requireContext())
                imageView.setImageBitmap(bitmap)
                imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                parent.addView(imageView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
                (imageView.layoutParams as android.widget.LinearLayout.LayoutParams).weight = 1f

                page.close()
                pdfRenderer.close()
                fileDescriptor.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun extractPdfText() {
        val file = File(requireContext().getExternalFilesDir(null), "ole/$filePath")
        if (!file.exists()) return
        isExtractingText = true
        lifecycleScope.launch(dispatcherProvider.io) {
            pdfText = try {
                PDFBoxResourceLoader.init(requireContext().applicationContext)
                val document = PDDocument.load(file)
                val text = PDFTextStripper().getText(document).trim()
                document.close()
                text
            } catch (e: Exception) { "" }
            withContext(dispatcherProvider.main) { isExtractingText = false }
        }
    }

    private fun setupPdfFabActions() {
        binding.fabRecord.setOnClickListener { audioRecorder.onRecordClicked() }
        binding.fabPlay.setOnClickListener {
            if (::library.isInitialized && !TextUtils.isEmpty(library.translationAudioPath)) {
                IntentUtils.openAudioFile(requireActivity(), library.translationAudioPath)
            }
        }
        binding.fabReadAloud.setOnClickListener {
            if (ttsManager.isSpeaking) ttsManager.stop() else ttsManager.speak(pdfText)
        }
    }

    private fun setupImageViewer() {
        binding.stubImage.visibility = View.VISIBLE
        val imageFileName = binding.root.findViewById<TextView>(R.id.imageFileName)
        val imageViewer = binding.root.findViewById<ImageView>(R.id.imageViewer)
        imageFileName.text = title

        val imageFile = if (isFullPath) filePath?.let { File(it) }
                        else File(requireContext().getExternalFilesDir(null), "ole/$filePath")
        Glide.with(this)
            .load(imageFile)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.ole_logo)
            .into(imageViewer)
    }

    private fun setupTextViewer() {
        binding.stubText.visibility = View.VISIBLE
        val textFileTitle = binding.root.findViewById<TextView>(R.id.textFileTitle)
        val textContent = binding.root.findViewById<TextView>(R.id.textContent)
        textFileTitle.text = title

        val file = File(requireContext().getExternalFilesDir(null), "ole/$filePath")
        if (file.exists()) {
            val text = file.readText()
            if (type == ResourceType.MARKDOWN) {
                MarkdownUtils.setMarkdownText(textContent, text)
            } else {
                textContent.text = text
            }
        }
    }

    override fun setAuthSession(responseHeader: Map<String, List<String>>) {
        val headerAuth = responseHeader["Set-Cookie"]?.get(0)?.split(";") ?: return
        auth = headerAuth[0]
        lifecycleScope.launch {
            val url = filePath ?: return@launch
            streamVideoFromUrl(url, auth)
            if (isOnline && !FileUtils.checkFileExist(requireContext(), url)) {
                DownloadUtils.openDownloadService(requireContext(), arrayListOf(url), false)
            }
        }
    }

    override fun onError(s: String) { Utilities.toast(requireContext(), "Auth error: $s") }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
        ttsManager.stop()
    }

    override fun onDestroyView() {
        authSessionUpdater?.stop()
        exoPlayer?.release()
        exoPlayer = null
        if (noisyReceiverRegistered) {
            requireContext().unregisterReceiver(audioBecomingNoisyReceiver)
            noisyReceiverRegistered = false
        }
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_RESOURCE_ID = "resourceId"
        private const val ARG_FILE_PATH = "filePath"
        private const val ARG_TITLE = "title"
        private const val ARG_TYPE = "type"
        private const val ARG_IS_ONLINE = "isOnline"
        private const val ARG_IS_FULL_PATH = "isFullPath"
        private const val ARG_AUTH = "auth"

        fun newInstance(resourceId: String?, filePath: String?, title: String?, type: ResourceType, isOnline: Boolean = false, auth: String = "", isFullPath: Boolean = false): ResourceViewerFragment {
            return ResourceViewerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_RESOURCE_ID, resourceId)
                    putString(ARG_FILE_PATH, filePath)
                    putString(ARG_TITLE, title)
                    putString(ARG_TYPE, type.name)
                    putBoolean(ARG_IS_ONLINE, isOnline)
                    putBoolean(ARG_IS_FULL_PATH, isFullPath)
                    putString(ARG_AUTH, auth)
                }
            }
        }
    }
}
