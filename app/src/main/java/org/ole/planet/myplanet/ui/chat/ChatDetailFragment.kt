package org.ole.planet.myplanet.ui.chat

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TableRow
import androidx.core.content.ContextCompat
import androidx.core.view.isNotEmpty
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.AndroidEntryPoint
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentChatDetailBinding
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.AiProvider
import org.ole.planet.myplanet.model.ChatMessage
import org.ole.planet.myplanet.model.ChatModel
import org.ole.planet.myplanet.model.ChatRequest
import org.ole.planet.myplanet.model.ContentData
import org.ole.planet.myplanet.model.ContinueChatRequest
import org.ole.planet.myplanet.model.Data
import org.ole.planet.myplanet.model.RealmConversation
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.service.sync.ServerUrlMapper
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@AndroidEntryPoint
class ChatDetailFragment : Fragment() {
    private var _binding: FragmentChatDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var mAdapter: ChatAdapter
    private lateinit var sharedViewModel: ChatViewModel
    private lateinit var messageTextWatcher: TextWatcher
    private var _id: String = ""
    private var _rev: String = ""
    private var currentID: String = ""
    private var aiName: String = ""
    private var aiModel: String = ""
    var user: RealmUserModel? = null
    private var isUserLoaded = false
    private var isAiUnavailable = false
    private var newsId: String? = null
    private var loadingJob: Job? = null
    @Inject
    @AppPreferences
    lateinit var settings: SharedPreferences
    lateinit var customProgressDialog: DialogUtils.CustomProgressDialog
    @Inject
    lateinit var chatRepository: ChatRepository
    @Inject
    lateinit var chatApiHelper: ChatApiHelper
    @Inject
    lateinit var userRepository: UserRepository
    private val serverUrlMapper = ServerUrlMapper()
    private val jsonMediaType = "application/json".toMediaTypeOrNull()
    private val serverUrl: String
        get() = settings.getString("serverURL", "") ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedViewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatDetailBinding.inflate(inflater, container, false)
        customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initChatComponents()
        val newsRev = arguments?.getString("newsRev")
        val newsConversations = arguments?.getString("conversations")
        observeAiProviders()
        setupSendButton()
        setupMessageInputListeners()
        if (newsId != null) {
            loadNewsConversations(newsId, newsRev, newsConversations)
        } else {
            observeViewModelData()
        }
        view.post { clearChatDetail() }
    }

    private fun initChatComponents() {
        isUserLoaded = false
        isAiUnavailable = false
        refreshInputState()
        viewLifecycleOwner.lifecycleScope.launch {
            val userId = settings.getString("userId", "") ?: ""
            user = withContext(Dispatchers.IO) { userRepository.getUserById(userId) }
            isUserLoaded = true
            refreshInputState()
        }
        mAdapter = ChatAdapter(requireContext(), binding.recyclerGchat, viewLifecycleOwner.lifecycleScope)
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
                            val requestBody = createContinueChatRequest(message, aiProvider, _id, newRev)
                            launchRequest(requestBody, message, _id)
                        }
                    }
                    currentID.isNotEmpty() -> {
                        val requestBody = createContinueChatRequest(message, aiProvider, currentID, _rev)
                        launchRequest(requestBody, message, currentID)
                    }
                    else -> {
                        val requestBody = createChatRequest(message, aiProvider)
                        launchRequest(requestBody, message, null)
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
                val messages = withContext(Dispatchers.Default) {
                    val conversations = JsonUtils.gson.fromJson(newsConversations, Array<RealmConversation>::class.java).toList()
                    val list = mutableListOf<ChatMessage>()
                    val limit = 20
                    val limitedConversations = if (conversations.size > limit) conversations.takeLast(limit) else conversations
                    for (conversation in limitedConversations) {
                        conversation.query?.let { list.add(ChatMessage(it, ChatMessage.QUERY)) }
                        conversation.response?.let { list.add(ChatMessage(it, ChatMessage.RESPONSE, ChatMessage.RESPONSE_SOURCE_SHARED_VIEW_MODEL)) }
                    }
                    list
                }
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
                            onFailError()
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
                        binding.editGchatMessage.text.clear()
                        binding.textGchatIndicator.visibility = View.GONE
                        if (!conversations.isNullOrEmpty()) {
                            val messages = mutableListOf<ChatMessage>()
                            for (conversation in conversations) {
                                conversation.query?.let { messages.add(ChatMessage(it, ChatMessage.QUERY)) }
                                conversation.response?.let { messages.add(ChatMessage(it, ChatMessage.RESPONSE, ChatMessage.RESPONSE_SOURCE_SHARED_VIEW_MODEL)) }
                            }
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

        val mapping = serverUrlMapper.processUrl(serverUrl)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            updateServerIfNecessary(mapping)
            withContext(Dispatchers.Main) {
                chatApiHelper.fetchAiProviders { providers ->
                    sharedViewModel.setAiProvidersLoading(false)
                    if (providers == null || providers.values.all { !it }) {
                        sharedViewModel.setAiProvidersError(true)
                        sharedViewModel.setAiProviders(null)
                    } else {
                        sharedViewModel.setAiProviders(providers)
                    }
                }
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

        providersMap.keys.forEachIndexed { index, providerName ->
            val modelName = modelsMap[providerName.lowercase()] ?: "default-model"

            aiTableRow.addView(createProviderButton(currentContext, providerName, modelName))

            if (index < providersMap.size - 1) {
                aiTableRow.addView(createDivider(currentContext))
            }
        }
        aiTableRow.getChildAt(0)?.performClick()
        isAiUnavailable = false
        refreshInputState()
    }

    private fun createProviderButton(context: Context, providerName: String, modelName: String): Button =
        Button(context).apply {
            text = providerName.lowercase(Locale.getDefault())
            setTextColor(ContextCompat.getColor(context, R.color.md_black_1000))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(16, 8, 16, 8)
            isAllCaps = false
            setBackgroundColor(ContextCompat.getColor(context, R.color.disable_color))
            setOnClickListener { selectAI(this, providerName, modelName) }
        }

    private fun createDivider(context: Context): View =
        View(context).apply {
            layoutParams = TableRow.LayoutParams(1, TableRow.LayoutParams.MATCH_PARENT).apply {
                setMargins(8, 0, 8, 0)
            }
            setBackgroundColor(ContextCompat.getColor(context, R.color.hint_color))
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

    private fun updateButtonStyles(selectedButton: Button, aiTableRow: TableRow, context: Context) {
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

    private fun launchRequest(content: RequestBody, query: String, id: String?) {
        disableUI()
        val mapping = processServerUrl()
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) { updateServerIfNecessary(mapping) }
            sendChatRequest(content, query, id, id == null)
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
        serverUrlMapper.updateServerIfNecessary(mapping, settings) { url ->
            isServerReachable(url)
        }
    }

    private fun getModelsMap(): Map<String, String> {
        val modelsString = settings.getString("ai_models", null)
        return if (modelsString != null) {
            JsonUtils.gson.fromJson(modelsString, object : TypeToken<Map<String, String>>() {}.type)
        } else {
            emptyMap()
        }
    }

    private fun jsonRequestBody(json: String): RequestBody =
        json.toRequestBody(jsonMediaType)

    private fun createContinueChatRequest(message: String, aiProvider: AiProvider, id: String, rev: String): RequestBody {
        val continueChatData = ContinueChatRequest(data = Data("${user?.name}", message, aiProvider, id, rev), save = true)
        val jsonContent = JsonUtils.gson.toJson(continueChatData)
        return jsonRequestBody(jsonContent)
    }

    private fun createChatRequest(message: String, aiProvider: AiProvider): RequestBody {
        val chatData = ChatRequest(data = ContentData("${user?.name}", message, aiProvider), save = true)
        val jsonContent = JsonUtils.gson.toJson(chatData)
        return jsonRequestBody(jsonContent)
    }

    private suspend fun getLatestRev(id: String): String? {
        return try {
            chatRepository.getLatestRev(id)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun sendChatRequest(content: RequestBody, query: String, id: String?, newChat: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            chatApiHelper.sendChatRequest(content, object : Callback<ChatModel> {
                override fun onResponse(call: Call<ChatModel>, response: Response<ChatModel>) {
                    handleResponse(response, query, id)
                }

                override fun onFailure(call: Call<ChatModel>, t: Throwable) {
                    handleFailure(t.message, query, id)
                }
            })
        }
    }

    private fun handleResponse(response: Response<ChatModel>, query: String, id: String?) {
        val responseBody = response.body()
        if (response.isSuccessful && responseBody != null) {
            if (responseBody.status == "Success") {
                responseBody.chat?.let { chatResponse ->
                    processSuccessfulResponse(chatResponse, responseBody, query, id)
                }
            } else {
                showError(responseBody.message)
            }
        } else {
            showError(response.message() ?: context?.getString(R.string.request_failed_please_retry))
            id?.let { continueConversationRealm(it, query, "") }
        }
        enableUI()
    }

    private fun processSuccessfulResponse(chatResponse: String, responseBody: ChatModel, query: String, id: String?) {
        mAdapter.addResponse(chatResponse, ChatMessage.RESPONSE_SOURCE_NETWORK)
        responseBody.couchDBResponse?.rev?.let { _rev = it }
        id?.let { continueConversationRealm(it, query, chatResponse) } ?: saveNewChat(query, chatResponse, responseBody)
    }

    private fun handleFailure(errorMessage: String?, query: String, id: String?) {
        showError(errorMessage)
        id?.let { continueConversationRealm(it, query, "") }
        enableUI()
    }

    private fun showError(message: String?) {
        _binding?.let { binding ->
            binding.textGchatIndicator.visibility = View.VISIBLE
            binding.textGchatIndicator.text = context?.getString(R.string.message_placeholder, message)
        }
    }

    private fun saveNewChat(query: String, chatResponse: String, responseBody: ChatModel) {
        val jsonObject = buildChatHistoryObject(query, chatResponse, responseBody)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                chatRepository.saveNewChat(jsonObject)
                if (isAdded && activity is DashboardActivity) {
                    (activity as DashboardActivity).refreshChatHistory()
                }
            } catch (e: Exception) {
                if (isAdded) {
                    Snackbar.make(binding.root, getString(R.string.failed_to_save_chat), Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun buildChatHistoryObject(query: String, chatResponse: String, responseBody: ChatModel): JsonObject =
        JsonObject().apply {
            val id = responseBody.couchDBResponse?.id
            val rev = responseBody.couchDBResponse?.rev
            if (id != null) {
                currentID = id
            }
            if (rev != null) {
                _rev = rev
            }
            addProperty("_rev", responseBody.couchDBResponse?.rev ?: "")
            addProperty("_id", responseBody.couchDBResponse?.id ?: "")
            addProperty("aiProvider", aiName)
            addProperty("user", user?.name)
            addProperty("title", query)
            addProperty("createdTime", Date().time)
            addProperty("updatedDate", "")

            val conversationsArray = JsonArray()
            val conversationObject = JsonObject().apply {
                addProperty("query", query)
                addProperty("response", chatResponse)
            }
            conversationsArray.add(conversationObject)
            add("conversations", conversationsArray)
        }

    private fun continueConversationRealm(id: String, query: String, chatResponse: String) {
        val realmChatId = when {
            id.isNotBlank() -> id
            _id.isNotBlank() -> _id
            currentID.isNotBlank() -> currentID
            else -> return
        }

        if (query.isBlank() && chatResponse.isBlank()) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                chatRepository.continueConversation(realmChatId, query, chatResponse, _rev)
                withContext(Dispatchers.Main) {
                    if (isAdded && activity is DashboardActivity) {
                        (activity as DashboardActivity).refreshChatHistory()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Snackbar.make(binding.root, getString(R.string.failed_to_save_chat), Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
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
        val editor = settings.edit()
        if (settings.getBoolean("isAlternativeUrl", false)) {
            editor.putString("alternativeUrl", "")
            editor.putString("processedAlternativeUrl", "")
            editor.putBoolean("isAlternativeUrl", false)
            editor.apply()
        }
        loadingJob?.cancel()
        _binding = null
        super.onDestroyView()
    }
}
