package org.ole.planet.myplanet.ui.chat

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.*
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TableRow
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.*
import com.google.android.material.button.MaterialButton
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import io.realm.Realm
import kotlinx.coroutines.CoroutineScope
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

class ChatDetailFragment : Fragment() {
    lateinit var fragmentChatDetailBinding: FragmentChatDetailBinding
    private lateinit var mAdapter: ChatAdapter
    private lateinit var sharedViewModel: ChatViewModel
    private var _id: String = ""
    private var _rev: String = ""
    private var aiName: String = ""
    private var aiModel: String = ""
    private lateinit var mRealm: Realm
    var user: RealmUserModel? = null
    private var newsId: String? = null
    lateinit var settings: SharedPreferences
    lateinit var customProgressDialog: DialogUtils.CustomProgressDialog

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
            val aiProvider = AiProvider(name = aiName.lowercase(), model = aiModel)
            fragmentChatDetailBinding.textGchatIndicator.visibility = View.GONE
            if (TextUtils.isEmpty("${fragmentChatDetailBinding.editGchatMessage.text}".trim())) {
                fragmentChatDetailBinding.textGchatIndicator.visibility = View.VISIBLE
                fragmentChatDetailBinding.textGchatIndicator.text = getString(R.string.kindly_enter_message)
            } else {
                val message = "${fragmentChatDetailBinding.editGchatMessage.text}".replace("\n", " ")
                mAdapter.addQuery(message)
                if (_id != "") {
                    val continueChatData = ContinueChatModel(data = Data("${user?.name}", message, aiProvider, _id, _rev), save = true)
                    val jsonContent = Gson().toJson(continueChatData)
                    val requestBody = RequestBody.create(MediaType.parse("application/json"), jsonContent)
                    continueChatRequest(requestBody, _id, message)
                } else {
                    val chatData = ChatRequestModel(data = ContentData("${user?.name}", message, aiProvider), save = true)
                    val jsonContent = Gson().toJson(chatData)
                    val requestBody = RequestBody.create(MediaType.parse("application/json"), jsonContent)
                    makePostRequest(requestBody, message)
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
            val conversations = Gson().fromJson(newsConversations, Array<Conversation>::class.java).toList()
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
                if (conversations.isValid) {
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
        val updateUrl = settings.getString("serverURL", "") ?: ""
        val serverUrlMapper = ServerUrlMapper(requireContext())
        val mapping = serverUrlMapper.processUrl(updateUrl)

        CoroutineScope(Dispatchers.IO).launch {
            val primaryAvailable = isServerReachable(mapping.primaryUrl)
            val alternativeAvailable = mapping.alternativeUrl?.let { isServerReachable(it) } == true

            if (!primaryAvailable && alternativeAvailable) {
                mapping.alternativeUrl.let { alternativeUrl ->
                    val uri = Uri.parse(updateUrl)
                    val editor = settings.edit()
                    serverUrlMapper.updateUrlPreferences(editor, uri, alternativeUrl, mapping.primaryUrl, settings)
                }
            }

            withContext(Dispatchers.Main) {
                customProgressDialog.setText(getString(R.string.fetching_ai_providers))
                customProgressDialog.show()
                val apiInterface = ApiClient.client?.create(ApiInterface::class.java)
                apiInterface?.checkAiProviders("${Utilities.hostUrl}checkProviders/")?.enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                        if (response.isSuccessful) {
                            response.body()?.let { responseBody ->
                                try {
                                    val responseString = responseBody.string()
                                    val aiProvidersResponse: Map<String, Boolean> = Gson().fromJson(
                                        responseString, object : TypeToken<Map<String, Boolean>>() {}.type
                                    )

                                    val primaryModel = if (aiProvidersResponse.containsKey("openai")) {
                                        "openai"
                                    } else {
                                        aiProvidersResponse.keys.firstOrNull() ?: "openai"
                                    }

                                    updateAIButtons(aiProvidersResponse, primaryModel)
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

    private fun updateAIButtons(aiProvidersResponse: Map<String, Boolean>, primaryModel: String) {
        if (!isAdded || context == null) return

        val aiContainer = fragmentChatDetailBinding.aiContainer
        aiContainer.removeAllViews()

        val currentContext = requireContext()
        val modelsString = settings.getString("ai_models", null)

        val modelsMap: Map<String, String> = if (modelsString != null) {
            Gson().fromJson(modelsString, object : TypeToken<Map<String, String>>() {}.type)
        } else {
            emptyMap()
        }

        val providersMap = aiProvidersResponse.filter { it.value }
        if (providersMap.isEmpty()) return

        val displayedProviders = listOf(primaryModel)
        val hiddenProviders = providersMap.keys.filter { it != primaryModel }

        displayedProviders.forEach { providerName ->
            val modelName = modelsMap[providerName.lowercase()] ?: ""
            val button = createStyledAIButton(providerName.lowercase(), modelName.lowercase(), currentContext)
            aiContainer.addView(button)
        }

        if (hiddenProviders.isNotEmpty()) {
            val moreButton = createDropdownMenu(hiddenProviders, modelsMap, currentContext, aiContainer)
            aiContainer.addView(moreButton)
        }

        aiContainer.getChildAt(0)?.performClick()
    }

    private fun createStyledAIButton(providerName: String, modelName: String, context: Context): MaterialButton {
        return MaterialButton(context, null, R.attr.materialButtonOutlinedStyle).apply {
            text = providerName.capitalize(Locale.getDefault())
            setTextColor(ContextCompat.getColor(context, R.color.md_black_1000))
            textSize = 18f
            cornerRadius = 20
            strokeWidth = 2
            setPadding(25, 21, 25, 21)
            setBackgroundColor(ContextCompat.getColor(context, R.color.disable_color))

            layoutParams = TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(14, 6, 14, 6)
            }
            setOnClickListener {
                selectAI(this, providerName, modelName)
            }

            setOnLongClickListener {
                (parent as? TableRow)?.removeView(this)
                true
            }
        }
    }

    private fun createDropdownMenu(hiddenProviders: List<String>, modelsMap: Map<String, String>, context: Context, aiContainer: LinearLayout): MaterialButton {
        return MaterialButton(context, null, R.attr.materialButtonOutlinedStyle).apply {
            text = context.getString(R.string.more)
            textSize = 18f
            cornerRadius = 20
            strokeWidth = 2
            setPadding(25, 21, 25, 21)
            setBackgroundColor(ContextCompat.getColor(context, R.color.disable_color))

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(12, 4, 12, 4)
            }

            setOnClickListener { view ->
                val popupMenu = PopupMenu(context, view)
                hiddenProviders.forEach { providerName ->
                    popupMenu.menu.add(providerName.capitalize(Locale.getDefault()))
                }

                popupMenu.setOnMenuItemClickListener { menuItem ->
                    val selectedProvider = menuItem.title.toString().lowercase()
                    val selectedModel = modelsMap[selectedProvider] ?: ""

                    if (!isButtonAlreadyAdded(aiContainer, selectedProvider)) {
                        val button = createStyledAIButton(selectedProvider, selectedModel.lowercase(), context)
                        aiContainer.addView(button, aiContainer.childCount - 1)
                    }

                    true
                }

                popupMenu.show()
            }
        }
    }

    private fun isButtonAlreadyAdded(aiContainer: LinearLayout, providerName: String): Boolean {
        for (i in 0 until aiContainer.childCount) {
            val view = aiContainer.getChildAt(i)
            if (view is MaterialButton && view.text.toString().equals(providerName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun selectAI(selectedButton: Button, providerName: String, modelName: String) {
        val aiTableRow = fragmentChatDetailBinding.aiContainer
        val context = requireContext()

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

        aiName = providerName.lowercase()
        aiModel = modelName

        clearChatDetail()
        fragmentChatDetailBinding.textGchatIndicator.visibility = View.GONE
    }

    private fun onFailError() {
        fragmentChatDetailBinding.textGchatIndicator.visibility = View.VISIBLE
        fragmentChatDetailBinding.textGchatIndicator.text = requireContext().getString(R.string.virtual_assistant_currently_not_available)
        fragmentChatDetailBinding.editGchatMessage.isEnabled = false
        fragmentChatDetailBinding.buttonGchatSend.isEnabled = false
    }

    private fun makePostRequest(content: RequestBody, query: String) {
        disableUI()
        val mapping = processServerUrl()
        handleServerReachability(mapping)
        sendChatRequest(content, query, null)
    }

    private fun continueChatRequest(content: RequestBody, id: String, query: String) {
        disableUI()
        val mapping = processServerUrl()
        handleServerReachability(mapping)
        sendChatRequest(content, query, id)
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

    private fun processServerUrl(): ServerUrlMapper.UrlMapping {
        val updateUrl = settings.getString("serverURL", "") ?: ""
        val serverUrlMapper = ServerUrlMapper(requireContext())
        return serverUrlMapper.processUrl(updateUrl)
    }

    private fun handleServerReachability(mapping: ServerUrlMapper.UrlMapping) {
        CoroutineScope(Dispatchers.IO).launch {
            val primaryAvailable = isServerReachable(mapping.primaryUrl)
            val alternativeAvailable = mapping.alternativeUrl?.let { isServerReachable(it) } == true

            if (!primaryAvailable && alternativeAvailable) {
                mapping.alternativeUrl.let { alternativeUrl ->
                    val uri = Uri.parse(settings.getString("serverURL", ""))
                    val editor = settings.edit()
                    ServerUrlMapper(requireContext()).updateUrlPreferences(editor, uri, alternativeUrl, mapping.primaryUrl, settings)
                }
            }
        }
    }

    private fun sendChatRequest(content: RequestBody, query: String, id: String?) {
        CoroutineScope(Dispatchers.Main).launch {
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
                    id?.let { continueConversationRealm(it, query, chatResponse) } ?: saveNewChat(query, chatResponse, responseBody)
                }
            } else {
                showError(responseBody.message)
            }
        } else {
            showError(response.message() ?: getString(R.string.request_failed_please_retry))
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

        requireActivity().runOnUiThread {
            RealmChatHistory.insert(mRealm, jsonObject)
        }
        (requireActivity() as? DashboardActivity)?.refreshChatHistoryList()
    }

    private fun continueConversationRealm(id:String, query:String, chatResponse:String) {
        try {
            addConversationToChatHistory(mRealm, id, query, chatResponse)
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
