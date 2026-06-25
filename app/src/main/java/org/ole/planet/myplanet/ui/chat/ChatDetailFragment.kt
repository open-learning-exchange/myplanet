package org.ole.planet.myplanet.ui.chat

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isNotEmpty
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentChatDetailBinding
import org.ole.planet.myplanet.model.AiProvider
import org.ole.planet.myplanet.model.ChatMessage
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.ChatResult
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.utils.DialogUtils
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
class ChatDetailFragment : Fragment() {
    private var _binding: FragmentChatDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var mAdapter: ChatAdapter
    private val sharedViewModel: ChatViewModel by activityViewModels()
    private lateinit var messageTextWatcher: TextWatcher
    private var _id: String = ""
    private var _rev: String = ""
    private var currentID: String = ""
    private var aiName: String = ""
    private var aiModel: String = ""
    var user: RealmUser? = null
    private var isUserLoaded = false
    private var isAiUnavailable = false
    private var newsId: String? = null
    private var loadingJob: Job? = null
    private var courseTitle: String? = null
    private var stepTitle: String? = null
    private var stepDescription: String? = null
    private var stepNumber: Int = 0
    private var selectedText: String? = null
    private var hasCourseContext = false
    private var contextIncluded = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var textBeforeVoice: String = ""

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            startSpeechToText()
        } else {
            Utilities.toast(requireContext(), getString(R.string.microphone_permission_required))
        }
    }
    @Inject
    lateinit var sharedPrefManager: SharedPrefManager
    lateinit var customProgressDialog: DialogUtils.CustomProgressDialog
    @Inject
    lateinit var chatRepository: ChatRepository
    @Inject
    lateinit var userRepository: UserRepository
    @Inject
    lateinit var serverUrlMapper: ServerUrlMapper
    @Inject
    lateinit var dispatcherProvider: DispatcherProvider
    private val serverUrl: String
        get() = sharedPrefManager.getServerUrl()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        courseTitle = arguments?.getString(ARG_COURSE_TITLE)
        stepTitle = arguments?.getString(ARG_STEP_TITLE)
        stepDescription = arguments?.getString(ARG_STEP_DESCRIPTION)
        stepNumber = arguments?.getInt(ARG_STEP_NUMBER, 0) ?: 0
        selectedText = arguments?.getString(ARG_SELECTED_TEXT)
        hasCourseContext = !courseTitle.isNullOrBlank() || !stepTitle.isNullOrBlank()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatDetailBinding.inflate(inflater, container, false)
        customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (hasCourseContext) {
            sharedViewModel.clearChatState()
        }
        initChatComponents()
        val newsRev = arguments?.getString("newsRev")
        val newsConversations = arguments?.getString("conversations")
        observeAiProviders()
        checkAiProviders()
        setupSendButton()
        setupMicButton()
        setupMessageInputListeners()
        if (newsId != null) {
            loadNewsConversations(newsId, newsRev, newsConversations)
        } else {
            observeViewModelData()
        }
        view.post { clearChatDetail() }
        if (hasCourseContext) {
            binding.courseContextBanner.visibility = View.VISIBLE
            binding.courseContextBanner.text = buildBannerText()
        }
        val prefill = selectedText
        if (!prefill.isNullOrBlank()) {
            binding.editGchatMessage.setText(prefill)
            binding.editGchatMessage.setSelection(prefill.length)
        }
    }

    private fun setupMicButton() {
        binding.buttonGchatMic.setOnClickListener {
            if (isListening) {
                stopSpeechToText()
            } else {
                if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startSpeechToText()
                } else {
                    requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }
            }
        }
        initSpeechRecognizer()
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    binding.buttonGchatMic.setColorFilter(ContextCompat.getColor(requireContext(), R.color.md_red_500))
                    binding.textGchatIndicator.text = getString(R.string.voice_to_text)
                    binding.textGchatIndicator.visibility = View.VISIBLE
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    stopSpeechToText()
                }

                override fun onError(error: Int) {
                    stopSpeechToText()
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                        SpeechRecognizer.ERROR_SERVER -> "Error from server"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "Didn't understand, please try again."
                    }
                    Utilities.toast(requireContext(), message)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val finalMatch = matches[0]
                        val newText = if (textBeforeVoice.isEmpty()) finalMatch else "$textBeforeVoice $finalMatch"
                        binding.editGchatMessage.setText(newText)
                        binding.editGchatMessage.setSelection(newText.length)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val partialMatch = matches[0]
                        val newText = if (textBeforeVoice.isEmpty()) partialMatch else "$textBeforeVoice $partialMatch"
                        binding.editGchatMessage.setText(newText)
                        binding.editGchatMessage.setSelection(newText.length)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } else {
            binding.buttonGchatMic.visibility = View.GONE
        }
    }

    private fun startSpeechToText() {
        textBeforeVoice = binding.editGchatMessage.text.toString().trim()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        speechRecognizer?.startListening(intent)
    }

    private fun stopSpeechToText() {
        speechRecognizer?.stopListening()
        isListening = false
        binding.buttonGchatMic.setColorFilter(ContextCompat.getColor(requireContext(), R.color.md_blue_500))
        binding.textGchatIndicator.visibility = View.GONE
    }

    private fun initChatComponents() {
        isUserLoaded = false
        isAiUnavailable = false
        refreshInputState()
        viewLifecycleOwner.lifecycleScope.launch {
            val userId = sharedPrefManager.getUserId()
            user = userRepository.getUserById(userId)
            isUserLoaded = true
            refreshInputState()
        }
        mAdapter = ChatAdapter(requireContext(), binding.recyclerGchat) { response, onUpdate, onComplete ->
            val job = viewLifecycleOwner.lifecycleScope.launch {
                var currentIndex = 0
                while (currentIndex < response.length) {
                    if (!kotlin.coroutines.coroutineContext.isActive) return@launch
                    onUpdate(response.substring(0, currentIndex + 1))
                    currentIndex++
                    kotlinx.coroutines.delay(10L)
                }
                onComplete()
            }
            return@ChatAdapter { job.cancel() }
        }
        mAdapter.onLoadMoreClick = ::loadMoreConversations
        binding.recyclerGchat.apply {
            adapter = mAdapter
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = true
            setHasFixedSize(true)
        }
        newsId = arguments?.getString("newsId")
        if (mAdapter.itemCount > 0) {
            binding.recyclerGchat.scrollToPosition(mAdapter.itemCount - 1)
            binding.recyclerGchat.smoothScrollToPosition(mAdapter.itemCount - 1)
        }
    }

    private fun setupSendButton() {
        binding.buttonGchatSend.setOnClickListener {
            val aiProvider = AiProvider(name = aiName, model = aiModel)
            binding.textGchatIndicator.visibility = View.GONE
            if (TextUtils.isEmpty("${binding.editGchatMessage.text}".trim())) {
                binding.textGchatIndicator.visibility = View.VISIBLE
                binding.textGchatIndicator.text = context?.getString(R.string.kindly_enter_message)
            } else {
                val message = "${binding.editGchatMessage.text}".replace("\n", " ")
                mAdapter.addQuery(message)
                when {
                    _id.isNotEmpty() -> {
                        viewLifecycleOwner.lifecycleScope.launch {
                            val newRev = getLatestRev(_id) ?: _rev
                            launchContinueChatRequest(message, user?.name, aiProvider, _id, newRev)
                        }
                    }
                    currentID.isNotEmpty() -> {
                        launchContinueChatRequest(message, user?.name, aiProvider, currentID, _rev)
                    }
                    else -> {
                        launchNewChatRequest(message, user?.name, aiProvider)
                    }
                }
                binding.editGchatMessage.text.clear()
                binding.textGchatIndicator.visibility = View.GONE
            }
        }
    }

    private fun setupMessageInputListeners() {
        binding.editGchatMessage.setOnKeyListener { _, _, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.keyCode == KeyEvent.KEYCODE_ENTER && event.isShiftPressed) {
                    binding.editGchatMessage.append("\n")
                    return@setOnKeyListener true
                } else if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
                    binding.buttonGchatSend.performClick()
                    return@setOnKeyListener true
                }
            }
            false
        }
        messageTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.textGchatIndicator.visibility = View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.editGchatMessage.addTextChangedListener(messageTextWatcher)
    }

    private fun loadNewsConversations(newsId: String?, newsRev: String?, newsConversations: String?) {
        _id = newsId ?: ""
        _rev = newsRev ?: ""
        loadingJob?.cancel()
        loadingJob = viewLifecycleOwner.lifecycleScope.launch {
            if (!isAdded) return@launch
            customProgressDialog.setText(getString(R.string.please_wait))
            customProgressDialog.show()
            try {
                val messages = sharedViewModel.parseAndBuildInitialPage(newsConversations)
                mAdapter.submitList(messages) {
                    binding.recyclerGchat.post {
                        binding.recyclerGchat.scrollToPosition(mAdapter.itemCount - 1)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                customProgressDialog.dismiss()
            }
        }
    }

    private fun observeAiProviders() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    sharedViewModel.aiProviders.collect { providers ->
                        if (providers != null) {
                            if (providers.values.all { !it }) {
                                onFailError()
                            } else {
                                updateAIButtons(providers)
                            }
                        }
                    }
                }
                launch {
                    sharedViewModel.aiProvidersLoading.collect { isLoading ->
                        if (isLoading) {
                            customProgressDialog.setText("${context?.getString(R.string.fetching_ai_providers)}")
                            customProgressDialog.show()
                        } else {
                            customProgressDialog.dismiss()
                        }
                    }
                }
                launch {
                    sharedViewModel.aiProvidersError.collect { hasError ->
                        if (hasError && sharedViewModel.aiProviders.value == null) {
                            val cachedProviders = getCachedProviderAvailability()
                            if (cachedProviders != null) {
                                updateAIButtons(cachedProviders)
                            } else {
                                onFailError()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun observeViewModelData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    sharedViewModel.selectedChatHistory.collect { conversations ->
                        mAdapter.clearData()
                        sharedViewModel.clearPaginationState()
                        binding.editGchatMessage.text.clear()
                        binding.textGchatIndicator.visibility = View.GONE
                        if (!conversations.isNullOrEmpty()) {
                            val messages = sharedViewModel.processChatHistory(conversations)
                            mAdapter.submitList(messages) {
                                binding.recyclerGchat.post {
                                    binding.recyclerGchat.scrollToPosition(mAdapter.itemCount - 1)
                                }
                            }
                        }
                    }
                }
                launch {
                    sharedViewModel.selectedAiProvider.collect { selectedAiProvider ->
                        aiName = selectedAiProvider ?: aiName
                        if (binding.aiTableRow.isNotEmpty()) {
                            for (i in 0 until binding.aiTableRow.childCount) {
                                val view = binding.aiTableRow.getChildAt(i)
                                if (view is Button && view.text.toString().equals(selectedAiProvider, ignoreCase = true)) {
                                    val modelName = getModelsMap()[selectedAiProvider?.lowercase()] ?: "default-model"
                                    selectAI(view, "$selectedAiProvider", modelName)
                                    break
                                }
                            }
                        }
                    }
                }
                launch {
                    sharedViewModel.selectedId.collect { selectedId ->
                        _id = selectedId
                    }
                }
                launch {
                    sharedViewModel.selectedRev.collect { selectedRev ->
                        _rev = selectedRev
                    }
                }

            }
        }
    }

    fun checkAiProviders() {
        if (!sharedViewModel.shouldFetchAiProviders()) {
            return
        }

        sharedViewModel.setAiProvidersLoading(true)
        sharedViewModel.setAiProvidersError(false)

        viewLifecycleOwner.lifecycleScope.launch {
            val providers = chatRepository.fetchAiProviders(serverUrl)
            sharedViewModel.setAiProvidersLoading(false)
            if (providers == null || providers.values.all { !it }) {
                val cachedProviders = getCachedProviderAvailability()
                if (cachedProviders != null) {
                    sharedViewModel.setAiProvidersError(false)
                    sharedViewModel.setAiProviders(cachedProviders)
                } else {
                    sharedViewModel.setAiProvidersError(true)
                    sharedViewModel.setAiProviders(null)
                }
            } else {
                sharedViewModel.setAiProviders(providers)
            }
        }
    }

    private fun updateAIButtons(aiProvidersResponse: Map<String, Boolean>) {
        if (!isAdded || context == null) return

        val aiTableRow = binding.aiTableRow
        aiTableRow.removeAllViews()

        val currentContext = requireContext()
        val modelsMap = getModelsMap()

        val providersMap = aiProvidersResponse.filter { it.value }

        if (providersMap.isEmpty()) return

        providersMap.keys.forEach { providerName ->
            val modelName = modelsMap[providerName.lowercase()] ?: "default-model"

            aiTableRow.addView(createProviderButton(currentContext, providerName, modelName))

        }
        aiTableRow.getChildAt(0)?.performClick()
        isAiUnavailable = false
        refreshInputState()
    }

    private fun createProviderButton(context: Context, providerName: String, modelName: String): Button =
        Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(6, 0, 6, 0)
            }
            text = providerName.lowercase(Locale.getDefault())
            setTextColor(ContextCompat.getColor(context, R.color.md_black_1000))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(16, 8, 16, 8)
            isAllCaps = false
            setBackgroundColor(ContextCompat.getColor(context, R.color.disable_color))
            setOnClickListener { selectAI(this, providerName, modelName) }
        }

    private fun selectAI(selectedButton: Button, providerName: String, modelName: String) {
        val aiTableRow = binding.aiTableRow
        val context = requireContext()

        if (aiName != providerName && aiName.isNotEmpty()) {
            clearConversation()
        }

        currentID = ""
        mAdapter.lastAnimatedPosition = -1
        mAdapter.animatedMessages.clear()

        updateButtonStyles(selectedButton, aiTableRow, context)

        aiName = providerName
        aiModel = modelName

        binding.textGchatIndicator.visibility = View.GONE
    }

    private fun updateButtonStyles(selectedButton: Button, aiTableRow: LinearLayout, context: Context) {
        for (i in 0 until aiTableRow.childCount) {
            val view = aiTableRow.getChildAt(i)
            if (view is Button) {
                if (view == selectedButton) {
                    view.setBackgroundColor(ContextCompat.getColor(context, R.color.mainColor))
                    view.setTextColor(ContextCompat.getColor(context, R.color.textColorPrimary))
                } else {
                    view.setBackgroundColor(ContextCompat.getColor(context, R.color.disable_color))
                    view.setTextColor(ContextCompat.getColor(context, R.color.md_black_1000))
                }
            }
        }
    }

    private fun clearConversation() {
        mAdapter.clearData()
        sharedViewModel.clearPaginationState()
        _id = ""
        _rev = ""
        currentID = ""
        binding.editGchatMessage.text.clear()
        binding.textGchatIndicator.visibility = View.GONE
    }

    private fun onFailError() {
        isAiUnavailable = true
        binding.textGchatIndicator.visibility = View.VISIBLE
        binding.textGchatIndicator.text = context?.getString(R.string.virtual_assistant_currently_not_available)
        refreshInputState()
    }

    private fun launchNewChatRequest(query: String, userName: String?, aiProvider: AiProvider) {
        disableUI()
        val mapping = processServerUrl()
        val contextualQuery = if (hasCourseContext && !contextIncluded) {
            contextIncluded = true
            buildContextPrefix() + query
        } else {
            query
        }
        viewLifecycleOwner.lifecycleScope.launch {
            updateServerIfNecessary(mapping)
            sendNewChatRequest(contextualQuery, userName, aiProvider)
        }
    }

    private fun launchContinueChatRequest(query: String, userName: String?, aiProvider: AiProvider, id: String, rev: String) {
        disableUI()
        val mapping = processServerUrl()
        viewLifecycleOwner.lifecycleScope.launch {
            updateServerIfNecessary(mapping)
            sendContinueChatRequest(query, userName, aiProvider, id, rev)
        }
    }

    private fun disableUI() {
        _binding?.let { binding ->
            binding.buttonGchatSend.isEnabled = false
            binding.editGchatMessage.isEnabled = false
            binding.imageGchatLoading.visibility = View.VISIBLE
        } ?: return
    }

    private fun enableUI() {
        _binding?.let { binding ->
            binding.imageGchatLoading.visibility = View.INVISIBLE
            refreshInputState()
        } ?: return
    }

    private fun refreshInputState() {
        _binding?.let { binding ->
            val enableInput = isUserLoaded && !isAiUnavailable
            binding.buttonGchatSend.isEnabled = enableInput
            binding.editGchatMessage.isEnabled = enableInput
        }
    }

    private fun processServerUrl(): ServerUrlMapper.UrlMapping =
        serverUrlMapper.processUrl(serverUrl)

    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, sharedPrefManager.rawPreferences) { url ->
            isServerReachable(url)
        }
    }

    private fun getModelsMap(): Map<String, String> {
        val modelsString = sharedPrefManager.getRawString("ai_models").takeIf { it.isNotEmpty() }
        return if (modelsString != null) {
            JsonUtils.gson.fromJson(modelsString, object : TypeToken<Map<String, String>>() {}.type)
        } else {
            emptyMap()
        }
    }

    private fun getCachedProviderAvailability(): Map<String, Boolean>? {
        val modelsMap = getModelsMap()
        if (modelsMap.isEmpty()) return null
        return modelsMap.keys
            .mapNotNull { key -> key.takeIf { it.isNotBlank() } }
            .distinct()
            .associateWith { true }
            .takeIf { it.isNotEmpty() }
    }

    private suspend fun getLatestRev(id: String): String? {
        return try {
            chatRepository.getLatestRev(id)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun sendNewChatRequest(query: String, userName: String?, aiProvider: AiProvider) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = chatRepository.sendNewChatRequest(query, userName, aiProvider)
            handleChatResult(result)
        }
    }

    private fun sendContinueChatRequest(query: String, userName: String?, aiProvider: AiProvider, id: String, rev: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = chatRepository.sendContinueChatRequest(query, userName, aiProvider, id, rev)
            handleChatResult(result)
        }
    }

    private fun handleChatResult(result: ChatResult) {
        when (result) {
            is ChatResult.Success -> {
                mAdapter.addResponse(result.response, ChatMessage.RESPONSE_SOURCE_NETWORK)
                _rev = result.rev
                currentID = result.id
                if (isAdded && activity is DashboardActivity) {
                    (activity as DashboardActivity).refreshChatHistory()
                }
            }
            is ChatResult.Error -> {
                showError(result.message)
            }
        }
        enableUI()
    }

    private fun showError(message: String?) {
        _binding?.let { binding ->
            binding.textGchatIndicator.visibility = View.VISIBLE
            binding.textGchatIndicator.text = context?.getString(R.string.message_placeholder, message)
        }
    }

    private fun loadMoreConversations() {
        val (newMessages, hasLoadMoreAbove) = sharedViewModel.loadMoreConversations()
        mAdapter.prependMessages(newMessages, hasLoadMoreAbove = hasLoadMoreAbove)
    }

    private fun clearChatDetail() {
        if (newsId == null && sharedViewModel.selectedChatHistory.value.isNullOrEmpty()) {
            if (::mAdapter.isInitialized) {
                mAdapter.clearData()
                _id = ""
                _rev = ""
            }
        }
    }

    override fun onDestroyView() {
        if (this::messageTextWatcher.isInitialized) {
            binding.editGchatMessage.removeTextChangedListener(messageTextWatcher)
        }
        if (sharedPrefManager.isAlternativeUrl()) {
            sharedPrefManager.setAlternativeUrl("")
            sharedPrefManager.setProcessedAlternativeUrl("")
            sharedPrefManager.setIsAlternativeUrl(false)
        }
        loadingJob?.cancel()
        speechRecognizer?.destroy()
        _binding = null
        super.onDestroyView()
    }

    private fun buildContextPrefix(): String {
        val sb = StringBuilder("[Context: ")
        if (!courseTitle.isNullOrBlank()) sb.append("Course \"$courseTitle\"")
        if (stepNumber > 0) sb.append(", Step $stepNumber")
        if (!stepTitle.isNullOrBlank()) sb.append(": \"$stepTitle\"")
        val text = selectedText
        val desc = stepDescription
        if (!text.isNullOrBlank()) {
            val passage = text.take(300)
            sb.append(". Highlighted passage: \"$passage${if (text.length > 300) "..." else ""}\"")
        } else if (!desc.isNullOrBlank()) {
            val truncated = desc.take(400)
            sb.append(". Content: $truncated${if (desc.length > 400) "..." else ""}")
        }
        sb.append("]\n\n")
        return sb.toString()
    }

    private fun buildBannerText(): String {
        val title = stepTitle
        val label = when {
            stepNumber > 0 && !title.isNullOrBlank() -> "Step $stepNumber: $title"
            !title.isNullOrBlank() -> title
            else -> courseTitle ?: ""
        }
        return getString(R.string.course_context_banner, label)
    }

    companion object {
        const val ARG_COURSE_TITLE = "course_title"
        const val ARG_STEP_TITLE = "step_title"
        const val ARG_STEP_DESCRIPTION = "step_description"
        const val ARG_STEP_NUMBER = "step_number"
        const val ARG_SELECTED_TEXT = "selected_text"
    }
}
