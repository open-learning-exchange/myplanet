package org.ole.planet.myplanet.ui.chat

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.*
import android.view.*
import android.widget.Button
import android.widget.TableRow
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentChatDetailBinding
import org.ole.planet.myplanet.datamanager.*
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.model.RealmChatHistory.Companion.addConversationToChatHistory
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.Utilities
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Date
import java.util.Locale
import androidx.core.net.toUri
import androidx.core.view.isNotEmpty
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class ChatDetailFragment : Fragment() {
    lateinit var fragmentChatDetailBinding: FragmentChatDetailBinding
    private lateinit var mAdapter: ChatAdapter
    private lateinit var sharedViewModel: ChatViewModel
    private var _id: String = ""
    private var _rev: String = ""
    private var currentID: String = ""
    private var aiName: String = ""
    private var aiModel: String = ""
    private lateinit var mRealm: Realm
    var user: RealmUserModel? = null
    private var newsId: String? = null
    lateinit var settings: SharedPreferences
    lateinit var customProgressDialog: DialogUtils.CustomProgressDialog
    private val gson = Gson()
    private val serverUrlMapper = ServerUrlMapper()
    private val jsonMediaType = "application/json".toMediaTypeOrNull()
    private val serverUrl: String
        get() = settings.getString("serverURL", "") ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedViewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentChatDetailBinding = FragmentChatDetailBinding.inflate(inflater, container, false)
        settings = requireActivity().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
        return fragmentChatDetailBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mRealm = DatabaseService(requireContext()).realmInstance
        user = UserProfileDbHandler(requireContext()).userModel
        mAdapter = ChatAdapter(ArrayList(), requireContext(), fragmentChatDetailBinding.recyclerGchat)
        fragmentChatDetailBinding.recyclerGchat.adapter = mAdapter
        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(requireContext())
        fragmentChatDetailBinding.recyclerGchat.layoutManager = layoutManager
        fragmentChatDetailBinding.recyclerGchat.isNestedScrollingEnabled = true
        fragmentChatDetailBinding.recyclerGchat.setHasFixedSize(true)
        newsId = arguments?.getString("newsId")
        val newsRev = arguments?.getString("newsRev")
        val newsConversations = arguments?.getString("conversations")
        checkAiProviders()
        if (mAdapter.itemCount > 0) {
            fragmentChatDetailBinding.recyclerGchat.scrollToPosition(mAdapter.itemCount - 1)
            fragmentChatDetailBinding.recyclerGchat.smoothScrollToPosition(mAdapter.itemCount - 1)
        }

        fragmentChatDetailBinding.buttonGchatSend.setOnClickListener {
            val aiProvider = AiProvider(name = aiName, model = aiModel)
            fragmentChatDetailBinding.textGchatIndicator.visibility = View.GONE
            if (TextUtils.isEmpty("${fragmentChatDetailBinding.editGchatMessage.text}".trim())) {
                fragmentChatDetailBinding.textGchatIndicator.visibility = View.VISIBLE
                fragmentChatDetailBinding.textGchatIndicator.text = context?.getString(R.string.kindly_enter_message)
            } else {
                val message = "${fragmentChatDetailBinding.editGchatMessage.text}".replace("\n", " ")
                mAdapter.addQuery(message)
                if (_id != "") {
                    val newRev = getLatestRev(_id) ?: _rev
                    val requestBody = createContinueChatRequest(message, aiProvider, _id, newRev)
                    launchRequest(requestBody, message, _id)
                } else if (currentID != "") {
                    val requestBody = createContinueChatRequest(message, aiProvider, currentID, _rev)
                    launchRequest(requestBody, message, currentID)
                } else {
                    val requestBody = createChatRequest(message, aiProvider)
                    launchRequest(requestBody, message, null)
                }
                fragmentChatDetailBinding.editGchatMessage.text.clear()
                fragmentChatDetailBinding.textGchatIndicator.visibility = View.GONE
            }
        }
        fragmentChatDetailBinding.editGchatMessage.setOnKeyListener { _, _, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.keyCode == KeyEvent.KEYCODE_ENTER && event.isShiftPressed) {
                    fragmentChatDetailBinding.editGchatMessage.append("\n")
                    return@setOnKeyListener true
                } else if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
                    fragmentChatDetailBinding.buttonGchatSend.performClick()
                    return@setOnKeyListener true
                }
            }
            false
        }
        fragmentChatDetailBinding.editGchatMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                fragmentChatDetailBinding.textGchatIndicator.visibility = View.GONE
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        if (newsId != null) {
            _id = "$newsId"
            _rev = newsRev ?: ""
            val conversations = gson.fromJson(newsConversations, Array<Conversation>::class.java).toList()
            for (conversation in conversations) {
                val query = conversation.query
                val response = conversation.response
                if (query != null) {
                    mAdapter.addQuery(query)
                }
                mAdapter.responseSource = ChatAdapter.RESPONSE_SOURCE_SHARED_VIEW_MODEL
                if (response != null) {
                    mAdapter.addResponse(response)
                }
            }
        } else {
            sharedViewModel.getSelectedChatHistory().observe(viewLifecycleOwner) { conversations ->
                mAdapter.clearData()
                fragmentChatDetailBinding.editGchatMessage.text.clear()
                fragmentChatDetailBinding.textGchatIndicator.visibility = View.GONE
                if (conversations != null && conversations.isValid && conversations.isNotEmpty()) {
                    for (conversation in conversations) {
                        val query = conversation.query
                        val response = conversation.response
                        if (query != null) {
                            mAdapter.addQuery(query)
                        }

                        mAdapter.responseSource = ChatAdapter.RESPONSE_SOURCE_SHARED_VIEW_MODEL

                        if (response != null) {
                            mAdapter.addResponse(response)
                        }
                    }
                    fragmentChatDetailBinding.recyclerGchat.post {
                        fragmentChatDetailBinding.recyclerGchat.scrollToPosition(mAdapter.itemCount - 1)
                    }
                }
            }
            sharedViewModel.getSelectedAiProvider().observe(viewLifecycleOwner) { selectedAiProvider ->
                aiName = selectedAiProvider ?: aiName
                if (fragmentChatDetailBinding.aiTableRow.isNotEmpty()) {
                    for (i in 0 until fragmentChatDetailBinding.aiTableRow.childCount) {
                        val view = fragmentChatDetailBinding.aiTableRow.getChildAt(i)
                        if (view is Button && view.text.toString().equals(selectedAiProvider, ignoreCase = true)) {
                        val modelName = getModelsMap()[selectedAiProvider?.lowercase()] ?: "default-model"
                        selectAI(view, "$selectedAiProvider", modelName)
                        break
                        }
                    }
                }
            }
        }

        sharedViewModel.getSelectedId().observe(viewLifecycleOwner) { selectedId ->
            _id = selectedId
        }

        sharedViewModel.getSelectedRev().observe(viewLifecycleOwner) { selectedRev ->
            _rev = selectedRev
        }
        view.post {
            clearChatDetail()
        }
    }

    private fun checkAiProviders() {
        val mapping = serverUrlMapper.processUrl(serverUrl)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            updateServerIfNecessary(mapping)

            withContext(Dispatchers.Main) {
                customProgressDialog.setText("${context?.getString(R.string.fetching_ai_providers)}")
                customProgressDialog.show()
                val apiInterface = ApiClient.client?.create(ApiInterface::class.java)
                apiInterface?.checkAiProviders("${Utilities.hostUrl}checkProviders/")?.enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                        if (response.isSuccessful) {
                            response.body()?.let { responseBody ->
                                try {
                                    val responseString = responseBody.string()
                                    val aiProvidersResponse: Map<String, Boolean> = gson.fromJson(
                                        responseString,
                                        object : TypeToken<Map<String, Boolean>>() {}.type
                                    )
                                    updateAIButtons(aiProvidersResponse)
                                } catch (e: JsonSyntaxException) {
                                    e.printStackTrace()
                                    onFailError()
                                } finally {
                                    customProgressDialog.dismiss()
                                }
                            }
                        } else {
                            onFailError()
                            customProgressDialog.dismiss()
                        }
                    }

                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                        onFailError()
                        customProgressDialog.dismiss()
                    }
                })
            }
        }
    }

    private fun updateAIButtons(aiProvidersResponse: Map<String, Boolean>) {
        if (!isAdded || context == null) return

        val aiTableRow = fragmentChatDetailBinding.aiTableRow
        aiTableRow.removeAllViews()

        val currentContext = requireContext()
        val modelsMap = getModelsMap()

        val providersMap = aiProvidersResponse.filter { it.value }

        if (providersMap.isEmpty()) return

        providersMap.keys.forEachIndexed { index, providerName ->
            val modelName = modelsMap[providerName.lowercase()] ?: "default-model"

            val button = Button(currentContext).apply {
                text = providerName.lowercase(Locale.getDefault())
                setTextColor(ContextCompat.getColor(currentContext, R.color.md_black_1000))
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setPadding(16, 8, 16, 8)
                isAllCaps = false
                setBackgroundColor(ContextCompat.getColor(currentContext, R.color.disable_color))
                setOnClickListener { selectAI(this, providerName, modelName) }
            }

            aiTableRow.addView(button)

            if (index < providersMap.size - 1) {
                val divider = View(currentContext).apply {
                    layoutParams = TableRow.LayoutParams(1, TableRow.LayoutParams.MATCH_PARENT).apply {
                        setMargins(8, 0, 8, 0)
                    }
                    setBackgroundColor(ContextCompat.getColor(currentContext, R.color.hint_color))
                }
                aiTableRow.addView(divider)
            }
        }
        aiTableRow.getChildAt(0)?.performClick()
    }

    private fun selectAI(selectedButton: Button, providerName: String, modelName: String) {
        val aiTableRow = fragmentChatDetailBinding.aiTableRow
        val context = requireContext()

        if (aiName != providerName && aiName.isNotEmpty()) {
            clearConversation()
        }

        currentID = ""
        mAdapter.lastAnimatedPosition = -1
        mAdapter.animatedMessages.clear()

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

        aiName = providerName
        aiModel = modelName

        fragmentChatDetailBinding.textGchatIndicator.visibility = View.GONE
    }

    private fun clearConversation() {
        mAdapter.clearData()
        _id = ""
        _rev = ""
        currentID = ""
        fragmentChatDetailBinding.editGchatMessage.text.clear()
        fragmentChatDetailBinding.textGchatIndicator.visibility = View.GONE
    }

    private fun onFailError() {
        fragmentChatDetailBinding.textGchatIndicator.visibility = View.VISIBLE
        fragmentChatDetailBinding.textGchatIndicator.text = context?.getString(R.string.virtual_assistant_currently_not_available)
        fragmentChatDetailBinding.editGchatMessage.isEnabled = false
        fragmentChatDetailBinding.buttonGchatSend.isEnabled = false
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
        fragmentChatDetailBinding.buttonGchatSend.isEnabled = false
        fragmentChatDetailBinding.editGchatMessage.isEnabled = false
        fragmentChatDetailBinding.imageGchatLoading.visibility = View.VISIBLE
    }

    private fun enableUI() {
        fragmentChatDetailBinding.buttonGchatSend.isEnabled = true
        fragmentChatDetailBinding.editGchatMessage.isEnabled = true
        fragmentChatDetailBinding.imageGchatLoading.visibility = View.INVISIBLE
    }

    private fun processServerUrl(): ServerUrlMapper.UrlMapping =
        serverUrlMapper.processUrl(serverUrl)

    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        val primaryAvailable = isServerReachable(mapping.primaryUrl)
        val alternativeAvailable = mapping.alternativeUrl?.let { isServerReachable(it) } == true

        if (!primaryAvailable && alternativeAvailable) {
            mapping.alternativeUrl?.let { alternativeUrl ->
                val editor = settings.edit()
                serverUrlMapper.updateUrlPreferences(editor, serverUrl.toUri(), alternativeUrl, mapping.primaryUrl, settings)
            }
        }
    }

    private fun getModelsMap(): Map<String, String> {
        val modelsString = settings.getString("ai_models", null)
        return if (modelsString != null) {
            gson.fromJson(modelsString, object : TypeToken<Map<String, String>>() {}.type)
        } else {
            emptyMap()
        }
    }

    private fun jsonRequestBody(json: String): RequestBody =
        RequestBody.create(jsonMediaType, json)

    private fun createContinueChatRequest(message: String, aiProvider: AiProvider, id: String, rev: String): RequestBody {
        val continueChatData = ContinueChatModel(data = Data("${user?.name}", message, aiProvider, id, rev), save = true)
        val jsonContent = gson.toJson(continueChatData)
        return jsonRequestBody(jsonContent)
    }

    private fun createChatRequest(message: String, aiProvider: AiProvider): RequestBody {
        val chatData = ChatRequestModel(data = ContentData("${user?.name}", message, aiProvider), save = true)
        val jsonContent = gson.toJson(chatData)
        return jsonRequestBody(jsonContent)
    }

    private fun getLatestRev(id: String): String? {
        return try {
            mRealm.refresh()
            val realmChatHistory = mRealm.where(RealmChatHistory::class.java)
                .equalTo("_id", id)
                .findAll()
                .maxByOrNull { rev -> rev._rev!!.split("-")[0].toIntOrNull() ?: 0 }

            val rev = realmChatHistory?._rev
            rev
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun sendChatRequest(content: RequestBody, query: String, id: String?, newChat: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val apiInterface = ApiClient.client?.create(ApiInterface::class.java)
            apiInterface?.chatGpt(Utilities.hostUrl, content)?.enqueue(object : Callback<ChatModel> {
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
                    mAdapter.responseSource = ChatAdapter.RESPONSE_SOURCE_NETWORK
                    mAdapter.addResponse(chatResponse)
                    val newRev = responseBody.couchDBResponse?.rev
                    if (newRev != null) {
                        _rev = newRev
                    }

                    id?.let { continueConversationRealm(it, query, chatResponse) } ?: saveNewChat(query, chatResponse, responseBody)
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

    private fun handleFailure(errorMessage: String?, query: String, id: String?) {
        showError(errorMessage)
        id?.let { continueConversationRealm(it, query, "") }
        enableUI()
    }

    private fun showError(message: String?) {
        fragmentChatDetailBinding.textGchatIndicator.visibility = View.VISIBLE
        fragmentChatDetailBinding.textGchatIndicator.text = context?.getString(R.string.message_placeholder, message)
    }

    private fun saveNewChat(query: String, chatResponse: String, responseBody: ChatModel) {
        val jsonObject = JsonObject().apply {
            val id = responseBody.couchDBResponse?.id
            val rev = responseBody.couchDBResponse?.rev
            if (id != null) {
                currentID = id
            }
            if (rev != null) {
                _rev = rev
            }
            addProperty("_rev",  responseBody.couchDBResponse?.rev ?: "")
            addProperty("_id",responseBody.couchDBResponse?.id ?: "")
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

        mRealm.executeTransaction { realm ->
            RealmChatHistory.insert(realm, jsonObject)
        }
        (requireActivity() as? DashboardActivity)?.refreshChatHistoryList()
    }

    private fun continueConversationRealm(id:String, query:String, chatResponse:String) {
        try {
            addConversationToChatHistory(mRealm, id, query, chatResponse, _rev)
            mRealm.commitTransaction()
        } catch (e: Exception) {
            e.printStackTrace()
            if (mRealm.isInTransaction) {
                mRealm.cancelTransaction()
            }
        } finally {
            mRealm.close()
        }
    }

    private fun clearChatDetail() {
        if (newsId == null) {
            if (::mAdapter.isInitialized) {
                mAdapter.clearData()
                _id = ""
                _rev = ""
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val editor = settings.edit()
        if (settings.getBoolean("isAlternativeUrl", false)) {
            editor.putString("alternativeUrl", "")
            editor.putString("processedAlternativeUrl", "")
            editor.putBoolean("isAlternativeUrl", false)
            editor.apply()
        }
    }
}
