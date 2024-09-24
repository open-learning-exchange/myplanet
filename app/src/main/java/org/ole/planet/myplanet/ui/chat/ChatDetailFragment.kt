package org.ole.planet.myplanet.ui.chat

import android.os.Bundle
import android.text.*
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.*
import com.google.gson.*
import io.realm.Realm
import okhttp3.*
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentChatDetailBinding
import org.ole.planet.myplanet.datamanager.*
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.model.RealmChatHistory.Companion.addConversationToChatHistory
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedViewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentChatDetailBinding = FragmentChatDetailBinding.inflate(inflater, container, false)
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
        val selectedText = arguments?.getString("selectedText")
        if (!selectedText.isNullOrEmpty()) {
            // Paste the selected text into the editGchatMessage field
            fragmentChatDetailBinding.editGchatMessage.setText(selectedText)

            // Simulate a click on the send button
            fragmentChatDetailBinding.buttonGchatSend.performClick()
        }
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
        val apiInterface = ApiClient.client?.create(ApiInterface::class.java)
        apiInterface?.checkAiProviders("${Utilities.hostUrl}/checkproviders")?.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    response.body()?.let { responseBody ->
                        try {
                            val gson = Gson()
                            val aiProvidersResponse = gson.fromJson(responseBody.string(), AiProvidersResponse::class.java)
                            if (aiProvidersResponse.openai) {
                                fragmentChatDetailBinding.tvOpenai.visibility = View.VISIBLE
                                fragmentChatDetailBinding.view1.visibility = View.VISIBLE

                                if (isAdded) {
                                    aiName = getString(R.string.openai)
                                    aiModel = "gpt-3.5-turbo"
                                    fragmentChatDetailBinding.tvOpenai.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.mainColor))
                                    fragmentChatDetailBinding.tvOpenai.setTextColor(ContextCompat.getColor(requireContext(), R.color.textColorPrimary))

                                    fragmentChatDetailBinding.tvOpenai.setOnClickListener {
                                        fragmentChatDetailBinding.tvOpenai.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.mainColor))
                                        fragmentChatDetailBinding.tvOpenai.setTextColor(ContextCompat.getColor(requireContext(), R.color.textColorPrimary))

                                        fragmentChatDetailBinding.tvPerplexity.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.disable_color))
                                        fragmentChatDetailBinding.tvPerplexity.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_black_1000))

                                        fragmentChatDetailBinding.tvGemini.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.disable_color))
                                        fragmentChatDetailBinding.tvGemini.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_black_1000))
                                        clearChatDetail()
                                        fragmentChatDetailBinding.textGchatIndicator.visibility = View.GONE
                                        aiName = getString(R.string.openai)
                                        aiModel = "gpt-3.5-turbo"
                                    }
                                }
                            } else {
                                fragmentChatDetailBinding.tvOpenai.visibility = View.GONE
                                fragmentChatDetailBinding.view1.visibility = View.GONE
                            }

                            if (aiProvidersResponse.perplexity) {
                                fragmentChatDetailBinding.tvPerplexity.visibility = View.VISIBLE
                                fragmentChatDetailBinding.view2.visibility = View.VISIBLE

                                if (isAdded) {
                                    if (!aiProvidersResponse.openai) {
                                        aiName = getString(R.string.perplexity)
                                        aiModel = "pplx-7b-online"
                                        fragmentChatDetailBinding.tvPerplexity.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.mainColor))
                                        fragmentChatDetailBinding.tvPerplexity.setTextColor(ContextCompat.getColor(requireContext(), R.color.textColorPrimary))
                                    }

                                    fragmentChatDetailBinding.tvPerplexity.setOnClickListener {
                                        fragmentChatDetailBinding.tvPerplexity.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.mainColor))
                                        fragmentChatDetailBinding.tvPerplexity.setTextColor(ContextCompat.getColor(requireContext(), R.color.textColorPrimary))

                                        fragmentChatDetailBinding.tvOpenai.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.disable_color))
                                        fragmentChatDetailBinding.tvOpenai.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_black_1000))

                                        fragmentChatDetailBinding.tvGemini.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.disable_color))
                                        fragmentChatDetailBinding.tvGemini.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_black_1000))

                                        clearChatDetail()
                                        fragmentChatDetailBinding.textGchatIndicator.visibility = View.GONE
                                        aiName = getString(R.string.perplexity)
                                        aiModel = "pplx-7b-online"
                                    }
                                }
                            } else {
                                fragmentChatDetailBinding.tvPerplexity.visibility = View.GONE
                                fragmentChatDetailBinding.view2.visibility = View.GONE
                            }

                            if (aiProvidersResponse.gemini) {
                                if (!aiProvidersResponse.openai && !aiProvidersResponse.perplexity) {
                                    if (isAdded) {
                                        aiName = getString(R.string.gemini)
                                        aiModel = "gemini-pro"
                                        fragmentChatDetailBinding.tvGemini.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.mainColor))
                                        fragmentChatDetailBinding.tvGemini.setTextColor(ContextCompat.getColor(requireContext(), R.color.textColorPrimary))
                                    }
                                }

                                fragmentChatDetailBinding.tvGemini.visibility = View.VISIBLE

                                fragmentChatDetailBinding.tvGemini.setOnClickListener {
                                    fragmentChatDetailBinding.tvGemini.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.mainColor))
                                    fragmentChatDetailBinding.tvGemini.setTextColor(ContextCompat.getColor(requireContext(), R.color.textColorPrimary))

                                    fragmentChatDetailBinding.tvPerplexity.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.disable_color))
                                    fragmentChatDetailBinding.tvPerplexity.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_black_1000))

                                    fragmentChatDetailBinding.tvOpenai.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.disable_color))
                                    fragmentChatDetailBinding.tvOpenai.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_black_1000))

                                    clearChatDetail()
                                    fragmentChatDetailBinding.textGchatIndicator.visibility = View.GONE
                                    aiName = getString(R.string.gemini)
                                    aiModel = "gemini-pro"
                                }
                            } else {
                                fragmentChatDetailBinding.tvGemini.visibility = View.GONE
                            }
                        } catch (e: JsonSyntaxException) {
                            onFailError()
                        }
                    }
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                onFailError()
            }
        })
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
