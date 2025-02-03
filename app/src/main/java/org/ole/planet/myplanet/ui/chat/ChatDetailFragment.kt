package org.ole.planet.myplanet.ui.chat

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.*
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.TableRow
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.*
import com.google.gson.*
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
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.Utilities
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Date

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedViewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentChatDetailBinding = FragmentChatDetailBinding.inflate(inflater, container, false)
        settings = requireActivity().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
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
                fragmentChatDetailBinding.textGchatIndicator.text =
                    getString(R.string.kindly_enter_message)
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
        val updateUrl = "${settings.getString("serverURL", "")}"
        Log.d("ServerCheck", "Initial API Base URL: $updateUrl")

        val serverUrlMapper = ServerUrlMapper(requireContext())
        val mapping = serverUrlMapper.processUrl(updateUrl)

        CoroutineScope(Dispatchers.IO).launch {
            val primaryAvailable = isServerReachable(mapping.primaryUrl)
            val alternativeAvailable = mapping.alternativeUrl?.let { isServerReachable(it) } == true

            Log.d("ServerCheck", "Primary API URL: ${mapping.primaryUrl}, Reachable: $primaryAvailable")
            Log.d("ServerCheck", "Alternative API URL: ${mapping.alternativeUrl}, Reachable: $alternativeAvailable")

            if (!primaryAvailable && alternativeAvailable) {
                mapping.alternativeUrl.let { alternativeUrl ->
                    val uri = Uri.parse(updateUrl)
                    val editor = settings.edit()

                    serverUrlMapper.updateUrlPreferences(editor, uri, alternativeUrl, mapping.primaryUrl, settings)

                    Log.d("ServerCheck", "Switched to Alternative API URL: ${Utilities.hostUrl}/checkproviders\"")
                }
            }

            withContext(Dispatchers.Main) {
                val apiInterface = ApiClient.client?.create(ApiInterface::class.java)
                Log.d("ServerCheck", "Final API Base URL: ${Utilities.hostUrl}/checkproviders")
                apiInterface?.checkAiProviders("${Utilities.hostUrl}/checkproviders")?.enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                        if (response.isSuccessful) {
                            response.body()?.let { responseBody ->
                                try {
                                    val responseString = responseBody.string()
                                    Log.d("ServerCheck", "Response: $responseString")
                                    Log.d("servercheck", "response1: $responseString")

                                    val gson = Gson()
                                    val aiProvidersResponse = gson.fromJson(responseString, AiProvidersResponse::class.java)
                                    updateAIButtons(aiProvidersResponse)

                                } catch (e: JsonSyntaxException) {
                                    onFailError()
                                }
                            }
                        }
                    }
//                    override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
//                        Log.d("ServerCheck", "Response: ${response.body()?.string()}")
//                        if (response.isSuccessful) {
//                            response.body()?.let { responseBody ->
//                                try {
//                                    Log.d("servercheck", "response1: ${responseBody.string()}")
//                                    val gson = Gson()
//                                    val aiProvidersResponse = gson.fromJson(responseBody.string(), AiProvidersResponse::class.java)
//                                    updateAIButtons(aiProvidersResponse)
//                                } catch (e: JsonSyntaxException) {
//                                    onFailError()
//                                }
//                            }
//                        }
//                    }

                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                        onFailError()
                    }
                })
            }
        }
    }

    private fun updateAIButtons(aiProvidersResponse: AiProvidersResponse) {
        val aiTableRow = fragmentChatDetailBinding.aiTableRow
        aiTableRow.removeAllViews()  // Clear existing buttons

        val context = requireContext()

        // Convert aiProvidersResponse to a Map for dynamic handling
        val providersMap = aiProvidersResponse::class.java.declaredFields
            .associate { field ->
                field.isAccessible = true
                field.name to (field.get(aiProvidersResponse) as? Boolean ?: false)
            }
            .filter { it.value } // Keep only enabled AI providers

        if (providersMap.isEmpty()) return // No providers available, nothing to display

        providersMap.keys.forEachIndexed { index, providerName ->
            // Create Button dynamically
            val button = Button(context).apply {
                text = providerName.capitalize()
                setTextColor(ContextCompat.getColor(context, R.color.md_black_1000))
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setPadding(16, 8, 16, 8)
                setBackgroundColor(ContextCompat.getColor(context, R.color.disable_color))
                setOnClickListener { selectAI(this, providerName) }
            }

            // Add Button to TableRow
            aiTableRow.addView(button)

            // Add Divider if it's not the last button
            if (index < providersMap.size - 1) {
                val divider = View(context).apply {
                    layoutParams = TableRow.LayoutParams(1, TableRow.LayoutParams.MATCH_PARENT).apply {
                        setMargins(8, 0, 8, 0)
                    }
                    setBackgroundColor(ContextCompat.getColor(context, R.color.hint_color))
                }
                aiTableRow.addView(divider)
            }
        }

        // Auto-select the first available provider
        aiTableRow.getChildAt(0)?.performClick()
    }

    private fun selectAI(selectedButton: Button, providerName: String) {
        val aiTableRow = fragmentChatDetailBinding.aiTableRow
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

        // Update AI Model dynamically
        aiName = providerName
        aiModel = when (providerName.lowercase()) {
            "openai" -> "gpt-3.5-turbo"
            "perplexity" -> "pplx-7b-online"
            "gemini" -> "gemini-pro"
            else -> "default-model"
        }

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
        fragmentChatDetailBinding.buttonGchatSend.isEnabled = false
        fragmentChatDetailBinding.editGchatMessage.isEnabled = false
        fragmentChatDetailBinding.imageGchatLoading.visibility = View.VISIBLE

        val apiInterface = ApiClient.client?.create(ApiInterface::class.java)
        apiInterface?.chatGpt(Utilities.hostUrl, content)?.enqueue(object : Callback<ChatModel> {
            override fun onResponse(call: Call<ChatModel>, response: Response<ChatModel>) {
                val responseBody = response.body()
                if (response.isSuccessful && responseBody != null) {
                    val jsonObject = JsonObject()
                    if (responseBody.status == "Success") {
                        val chatResponse = response.body()?.chat
                        if (chatResponse != null) {
                            mAdapter.responseSource = ChatAdapter.RESPONSE_SOURCE_NETWORK
                            mAdapter.addResponse(chatResponse)
                            _id = "${response.body()?.couchDBResponse?.id}"
                            _rev = "${response.body()?.couchDBResponse?.rev}"
                            jsonObject.addProperty("_rev", "${response.body()?.couchDBResponse?.rev}")
                            jsonObject.addProperty("_id", "${response.body()?.couchDBResponse?.id}")
                            jsonObject.addProperty("aiProvider", aiName)
                            jsonObject.addProperty("user", user?.name)
                            jsonObject.addProperty("title", query)
                            jsonObject.addProperty("createdTime", Date().time)
                            jsonObject.addProperty("updatedDate", "")

                            val conversationsArray = JsonArray()
                            val conversationObject = JsonObject()
                            conversationObject.addProperty("query", query)
                            conversationObject.addProperty("response", chatResponse)
                            conversationsArray.add(conversationObject)

                            jsonObject.add("conversations", conversationsArray)

                            requireActivity().runOnUiThread {
                                RealmChatHistory.insert(mRealm, jsonObject)
                            }
                            (requireActivity() as? DashboardActivity)?.refreshChatHistoryList()
                        }
                    } else {
                        fragmentChatDetailBinding.textGchatIndicator.visibility = View.VISIBLE
                        fragmentChatDetailBinding.textGchatIndicator.text = context?.getString(R.string.message_placeholder, responseBody.message)
                        jsonObject.addProperty("_rev", "")
                        jsonObject.addProperty("_id", "")
                        jsonObject.addProperty("aiProvider", aiName)
                        jsonObject.addProperty("user", user?.name)
                        jsonObject.addProperty("title", query)
                        jsonObject.addProperty("createdTime", Date().time)
                        jsonObject.addProperty("updatedDate", "")

                        val conversationsArray = JsonArray()
                        val conversationObject = JsonObject()
                        conversationObject.addProperty("query", query)
                        conversationObject.addProperty("response", "")
                        conversationsArray.add(conversationObject)

                        jsonObject.add("conversations", conversationsArray)
                        requireActivity().runOnUiThread {
                            RealmChatHistory.insert(mRealm, jsonObject)
                        }
                        (requireActivity() as? DashboardActivity)?.refreshChatHistoryList()
                    }
                } else {
                    fragmentChatDetailBinding.textGchatIndicator.visibility = View.VISIBLE
                    fragmentChatDetailBinding.textGchatIndicator.text = if (response.message() == "null"){
                        getString(R.string.request_failed_please_retry)
                    } else {
                        response.message()
                    }
                }

                fragmentChatDetailBinding.buttonGchatSend.isEnabled = true
                fragmentChatDetailBinding.editGchatMessage.isEnabled = true
                fragmentChatDetailBinding.imageGchatLoading.visibility = View.INVISIBLE
            }

            override fun onFailure(call: Call<ChatModel>, t: Throwable) {
                fragmentChatDetailBinding.textGchatIndicator.visibility = View.VISIBLE
                fragmentChatDetailBinding.textGchatIndicator.text = context?.getString(R.string.message_placeholder, t.message)
                fragmentChatDetailBinding.buttonGchatSend.isEnabled = true
                fragmentChatDetailBinding.editGchatMessage.isEnabled = true
                fragmentChatDetailBinding.imageGchatLoading.visibility = View.INVISIBLE
            }
        })
    }

    private fun continueChatRequest(content: RequestBody, id: String, query: String) {
        fragmentChatDetailBinding.buttonGchatSend.isEnabled = false
        fragmentChatDetailBinding.editGchatMessage.isEnabled = false
        fragmentChatDetailBinding.imageGchatLoading.visibility = View.VISIBLE

        val apiInterface = ApiClient.client?.create(ApiInterface::class.java)
        apiInterface?.chatGpt(Utilities.hostUrl, content)?.enqueue(object : Callback<ChatModel> {
            override fun onResponse(call: Call<ChatModel>, response: Response<ChatModel>) {
                val responseBody = response.body()
                if (response.isSuccessful && responseBody != null) {
                    if (responseBody.status == "Success") {
                        val chatResponse = response.body()?.chat
                        if (chatResponse != null) {
                            mAdapter.responseSource = ChatAdapter.RESPONSE_SOURCE_NETWORK
                            mAdapter.addResponse(chatResponse)
                            _rev = "${response.body()?.couchDBResponse?.rev}"
                            continueConversationRealm(id, query, chatResponse)
                        }
                    } else {
                        fragmentChatDetailBinding.textGchatIndicator.visibility = View.VISIBLE
                        fragmentChatDetailBinding.textGchatIndicator.text = context?.getString(R.string.message_placeholder, responseBody.message)
                    }
                } else {
                    fragmentChatDetailBinding.textGchatIndicator.visibility = View.VISIBLE
                    fragmentChatDetailBinding.textGchatIndicator.text = getString(R.string.request_failed_please_retry)
                    continueConversationRealm(id, query, "")
                }

                fragmentChatDetailBinding.buttonGchatSend.isEnabled = true
                fragmentChatDetailBinding.editGchatMessage.isEnabled = true
                fragmentChatDetailBinding.imageGchatLoading.visibility = View.INVISIBLE
            }

            override fun onFailure(call: Call<ChatModel>, t: Throwable) {
                continueConversationRealm(id, query, "")
                fragmentChatDetailBinding.textGchatIndicator.visibility = View.VISIBLE
                fragmentChatDetailBinding.textGchatIndicator.text = context?.getString(R.string.message_placeholder, t.message)
                fragmentChatDetailBinding.buttonGchatSend.isEnabled = true
                fragmentChatDetailBinding.editGchatMessage.isEnabled = true
                fragmentChatDetailBinding.imageGchatLoading.visibility = View.INVISIBLE
            }
        })
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
}
