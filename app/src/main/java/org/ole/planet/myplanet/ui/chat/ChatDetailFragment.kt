package org.ole.planet.myplanet.ui.chat

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import io.realm.Realm
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentChatDetailBinding
import org.ole.planet.myplanet.datamanager.ApiClient
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmChatHistory.addConversationToChatHistory
import org.ole.planet.myplanet.utilities.Utilities
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ChatDetailFragment : Fragment() {
    lateinit var fragmentChatDetailBinding: FragmentChatDetailBinding
    private lateinit var mAdapter: ChatAdapter
    private lateinit var sharedViewModel: ChatViewModel
    private var _id: String = ""
    private var _rev: String = ""
    private lateinit var mRealm: Realm
    private lateinit var webSocket: WebSocket

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedViewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        fragmentChatDetailBinding = FragmentChatDetailBinding.inflate(inflater, container, false)
        return fragmentChatDetailBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mRealm = DatabaseService(activity).realmInstance
        mAdapter = ChatAdapter(ArrayList(), requireContext())
        val webSocketUrl = replaceProtocolWithWs(Utilities.getHostUrl())

        webSocket = ApiClient.createWebSocket(webSocketUrl, MyWebSocketListener())
        Log.d("webSocket", webSocketUrl)
        fragmentChatDetailBinding.recyclerGchat.adapter = mAdapter
        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(requireContext())
        fragmentChatDetailBinding.recyclerGchat.layoutManager = layoutManager
        fragmentChatDetailBinding.recyclerGchat.isNestedScrollingEnabled = true
        fragmentChatDetailBinding.recyclerGchat.setHasFixedSize(true)

        if (mAdapter.itemCount > 0) {
            fragmentChatDetailBinding.recyclerGchat.scrollToPosition(mAdapter.itemCount - 1)
            fragmentChatDetailBinding.recyclerGchat.smoothScrollToPosition(mAdapter.itemCount - 1)
        }

        fragmentChatDetailBinding.buttonGchatSend.setOnClickListener {
            fragmentChatDetailBinding.textGchatIndicator.visibility = View.GONE
            if (TextUtils.isEmpty(fragmentChatDetailBinding.editGchatMessage.text.toString().trim())) {
                fragmentChatDetailBinding.textGchatIndicator.visibility = View.VISIBLE
                fragmentChatDetailBinding.textGchatIndicator.text = "Kindly enter message"
            } else {
                val message = "${fragmentChatDetailBinding.editGchatMessage.text}".replace("\n", " ")
                mAdapter.addQuery(message)
                if (_id != "") {
                    val continueChatData = ContinueChatModel(data = Data(message, _id, _rev), save = true)
                    val jsonContent = Gson().toJson(continueChatData)
                    val requestBody = RequestBody.create(MediaType.parse("application/json"), jsonContent)
                    continueChatRequest(requestBody, _id, message)
                } else {
                    val chatData = ChatRequestModel(data = ContentData(message), save = true)
                    val jsonContent = Gson().toJson(chatData)

                    fragmentChatDetailBinding.buttonGchatSend.isEnabled = false
                    fragmentChatDetailBinding.editGchatMessage.isEnabled = false
                    fragmentChatDetailBinding.imageGchatLoading.visibility = View.VISIBLE
                    webSocket.send(jsonContent)
//                    val chatData = ChatRequestModel(data = ContentData(message), save = true)
//                    val jsonContent = Gson().toJson(chatData)
//                    val requestBody = RequestBody.create(MediaType.parse("application/json"), jsonContent)
//                    makePostRequest(requestBody, message)
                }
                fragmentChatDetailBinding.editGchatMessage.text.clear()
                fragmentChatDetailBinding.textGchatIndicator.visibility = View.GONE
            }
        }

        fragmentChatDetailBinding.editGchatMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                fragmentChatDetailBinding.textGchatIndicator.visibility = View.GONE
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        sharedViewModel.getSelectedChatHistory().observe(viewLifecycleOwner) { conversations ->
            mAdapter.clearData()
            fragmentChatDetailBinding.editGchatMessage.text.clear()
            fragmentChatDetailBinding.textGchatIndicator.visibility = View.GONE
            if (conversations.isValid) {
                for (conversation in conversations) {
                    val query = conversation.query
                    val response = conversation.response
                    mAdapter.addQuery(query)
                    mAdapter.responseSource = ChatAdapter.RESPONSE_SOURCE_SHARED_VIEW_MODEL
                    mAdapter.addResponse(response)
                }
            }
        }

        sharedViewModel.getSelected_id().observe(viewLifecycleOwner) { selected_id ->
            _id = selected_id
        }

        sharedViewModel.getSelected_rev().observe(viewLifecycleOwner) { selected_rev ->
            _rev = selected_rev
        }
        view.post {
            clearChatDetail()
        }
    }

    private fun makePostRequest(content: RequestBody, query: String) {
        fragmentChatDetailBinding.buttonGchatSend.isEnabled = false
        fragmentChatDetailBinding.editGchatMessage.isEnabled = false
        fragmentChatDetailBinding.imageGchatLoading.visibility = View.VISIBLE

        val apiInterface = ApiClient.getClient().create(ApiInterface::class.java)
        val call = apiInterface.chatGpt(Utilities.getHostUrl(), content)

        call.enqueue(object : Callback<ChatModel> {
            override fun onResponse(call: Call<ChatModel>, response: Response<ChatModel>) {
                val responseBody = response.body()
                if (response.isSuccessful && responseBody != null) {
                    if (responseBody.status == "Success") {
                        val chatResponse = response.body()?.chat
                        if (chatResponse != null) {
                            mAdapter.responseSource = ChatAdapter.RESPONSE_SOURCE_NETWORK
                            mAdapter.addResponse(chatResponse)
                            _id = response.body()!!.couchDBResponse!!.id.toString()
                            _rev = response.body()!!.couchDBResponse!!.rev.toString()
                            val jsonObject = JsonObject()
                            jsonObject.addProperty("_rev", response.body()!!.couchDBResponse!!.rev.toString())
                            jsonObject.addProperty("_id", response.body()!!.couchDBResponse!!.id.toString())
                            jsonObject.addProperty("time", "")
                            jsonObject.addProperty("title", "")
                            jsonObject.addProperty("updatedTime", "")

                            val conversationsArray = JsonArray()
                            val conversationObject = JsonObject()
                            conversationObject.addProperty("query", query)
                            conversationObject.addProperty("response", chatResponse)
                            conversationsArray.add(conversationObject)

                            jsonObject.add("conversations", conversationsArray)
                        }
                    } else {
                        fragmentChatDetailBinding.textGchatIndicator.visibility = View.VISIBLE
                        fragmentChatDetailBinding.textGchatIndicator.text = "${responseBody.message}"
                        val jsonObject = JsonObject()
                        jsonObject.addProperty("_rev", "")
                        jsonObject.addProperty("_id", "")
                        jsonObject.addProperty("time", "")
                        jsonObject.addProperty("title", "")
                        jsonObject.addProperty("updatedTime", "")

                        val conversationsArray = JsonArray()
                        val conversationObject = JsonObject()
                        conversationObject.addProperty("query", query)
                        conversationObject.addProperty("response", "")
                        conversationsArray.add(conversationObject)

                        jsonObject.add("conversations", conversationsArray)
                        requireActivity().runOnUiThread {
                            RealmChatHistory.insert(mRealm, jsonObject)
                        }
                    }
                } else {
                    fragmentChatDetailBinding.textGchatIndicator.visibility = View.VISIBLE
                    fragmentChatDetailBinding.textGchatIndicator.text = getString(R.string.request_failed_please_retry)
                    val jsonObject = JsonObject()
                    jsonObject.addProperty("_rev", "")
                    jsonObject.addProperty("_id", "")
                    jsonObject.addProperty("time", "")
                    jsonObject.addProperty("title", "")
                    jsonObject.addProperty("updatedTime", "")

                    val conversationsArray = JsonArray()
                    val conversationObject = JsonObject()
                    conversationObject.addProperty("query", query)
                    conversationObject.addProperty("response", "")
                    conversationsArray.add(conversationObject)

                    jsonObject.add("conversations", conversationsArray)
                    requireActivity().runOnUiThread {
                        RealmChatHistory.insert(mRealm, jsonObject)
                    }
                }

                fragmentChatDetailBinding.buttonGchatSend.isEnabled = true
                fragmentChatDetailBinding.editGchatMessage.isEnabled = true
                fragmentChatDetailBinding.imageGchatLoading.visibility = View.INVISIBLE
            }

            override fun onFailure(call: Call<ChatModel>, t: Throwable) {
                val jsonObject = JsonObject()
                jsonObject.addProperty("_rev", "")
                jsonObject.addProperty("_id", "")
                jsonObject.addProperty("time", "")
                jsonObject.addProperty("title", "")
                jsonObject.addProperty("updatedTime", "")

                val conversationsArray = JsonArray()
                val conversationObject = JsonObject()
                conversationObject.addProperty("query", query)
                conversationObject.addProperty("response", "")
                conversationsArray.add(conversationObject)

                jsonObject.add("conversations", conversationsArray)
                requireActivity().runOnUiThread {
                    RealmChatHistory.insert(mRealm, jsonObject)
                }
                fragmentChatDetailBinding.textGchatIndicator.visibility = View.VISIBLE
                fragmentChatDetailBinding.textGchatIndicator.text = "${t.message}"
                fragmentChatDetailBinding.buttonGchatSend.isEnabled = true
                fragmentChatDetailBinding.editGchatMessage.isEnabled = true
                fragmentChatDetailBinding.imageGchatLoading.visibility = View.INVISIBLE
            }
        })
    }

    private fun continueChatRequest(content: RequestBody, _id: String, query: String) {
        fragmentChatDetailBinding.buttonGchatSend.isEnabled = false
        fragmentChatDetailBinding.editGchatMessage.isEnabled = false
        fragmentChatDetailBinding.imageGchatLoading.visibility = View.VISIBLE

        val apiInterface = ApiClient.getClient().create(ApiInterface::class.java)
        val call = apiInterface.chatGpt(Utilities.getHostUrl(), content)

        call.enqueue(object : Callback<ChatModel> {
            override fun onResponse(call: Call<ChatModel>, response: Response<ChatModel>) {
                val responseBody = response.body()
                if (response.isSuccessful && responseBody != null) {
                    if (responseBody.status == "Success") {
                        val chatResponse = response.body()?.chat
                        if (chatResponse != null) {
                            mAdapter.responseSource = ChatAdapter.RESPONSE_SOURCE_NETWORK
                            mAdapter.addResponse(chatResponse)
                            _rev = response.body()!!.couchDBResponse!!.rev.toString()
                            continueConversationRealm(_id, query, chatResponse)
                        }
                    } else {
                        fragmentChatDetailBinding.textGchatIndicator.visibility = View.VISIBLE
                        fragmentChatDetailBinding.textGchatIndicator.text = "${responseBody.message}"
                    }
                } else {
                    fragmentChatDetailBinding.textGchatIndicator.visibility = View.VISIBLE
                    fragmentChatDetailBinding.textGchatIndicator.text = getString(R.string.request_failed_please_retry)
                    continueConversationRealm(_id, query, "")
                }

                fragmentChatDetailBinding.buttonGchatSend.isEnabled = true
                fragmentChatDetailBinding.editGchatMessage.isEnabled = true
                fragmentChatDetailBinding.imageGchatLoading.visibility = View.INVISIBLE
            }

            override fun onFailure(call: Call<ChatModel>, t: Throwable) {
                continueConversationRealm(_id, query, "")
                fragmentChatDetailBinding.textGchatIndicator.visibility = View.VISIBLE
                fragmentChatDetailBinding.textGchatIndicator.text = "${t.message}"
                fragmentChatDetailBinding.buttonGchatSend.isEnabled = true
                fragmentChatDetailBinding.editGchatMessage.isEnabled = true
                fragmentChatDetailBinding.imageGchatLoading.visibility = View.INVISIBLE
            }
        })
    }

    private fun continueConversationRealm(_id:String, query:String, chatResponse:String) {
        try {
            mRealm = Realm.getDefaultInstance()
            addConversationToChatHistory(mRealm, _id, query, chatResponse)
        } finally {
            if (mRealm != null && !mRealm.isClosed) {
                mRealm.close()
            }
        }
    }

    private fun clearChatDetail() {
        if (::mAdapter.isInitialized) {
            mAdapter.clearData()
            _id = ""
            _rev = ""
            mAdapter.notifyDataSetChanged()
            fragmentChatDetailBinding.recyclerGchat.invalidate()
        }
    }

    fun replaceProtocolWithWs(url: String): String {
        val uri = Uri.parse(url)
        val updatedUri = uri.buildUpon().scheme("ws").build()
        return updatedUri.toString()
    }

    private inner class MyWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
//            webSocket.send("Hello World!")
        }

//        override fun onMessage(webSocket: WebSocket, text: String) {
//            output("Received : $text")
//            requireActivity().runOnUiThread {
//                try {
//                    val chatModel = Gson().fromJson(text, ChatModel::class.java)
//
//                    if (chatModel != null) {
//                        // Access properties of ChatModel
//                        val chatResponse = chatModel.chat
//                        val id = chatModel.couchDBResponse?.id
//                        val rev = chatModel.couchDBResponse?.rev
//
//                        // Update your adapter or UI with the received data
//                        mAdapter.responseSource = ChatAdapter.RESPONSE_SOURCE_NETWORK
//                        mAdapter.addResponse(chatResponse ?: "")
//
//
//                        // Handle id and rev as needed
//                        _id = id ?: ""
//                        _rev = rev ?: ""
//
//                        // Further handling if needed (e.g., updating UI, storing data, etc.)
//                    } else {
//                        Log.e("WebSocket", "Received null ChatModel")
//                    }
//                } catch (e: JsonSyntaxException) {
//                    // Handle JSON parsing error
//                    Log.e("WebSocket", "Error parsing JSON: $text")
//                }
//
//
//
////                mAdapter.responseSource = ChatAdapter.RESPONSE_SOURCE_NETWORK
////                mAdapter.addResponse(text)
////            _id = response.body()!!.couchDBResponse!!.id.toString()
////            _rev = response.body()!!.couchDBResponse!!.rev.toString()
////            val jsonObject = JsonObject()
////            jsonObject.addProperty("_rev", response.body()!!.couchDBResponse!!.rev.toString())
////            jsonObject.addProperty("_id", response.body()!!.couchDBResponse!!.id.toString())
////            jsonObject.addProperty("time", "")
////            jsonObject.addProperty("title", "")
////            jsonObject.addProperty("updatedTime", "")
////
////            val conversationsArray = JsonArray()
////            val conversationObject = JsonObject()
////            conversationObject.addProperty("query", query)
////            conversationObject.addProperty("response", text)
////            conversationsArray.add(conversationObject)
////
////            jsonObject.add("conversations", conversationsArray)
//                fragmentChatDetailBinding.buttonGchatSend.isEnabled = true
//                fragmentChatDetailBinding.editGchatMessage.isEnabled = true
//                fragmentChatDetailBinding.imageGchatLoading.visibility = View.INVISIBLE
//            }
//        }

//        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
//            // Received a binary message from the WebSocket
//            // Handle the message as needed
//        }

        private var receivedMessageBuffer = StringBuilder()

        override fun onMessage(webSocket: WebSocket, text: String) {
            // Append the received part to the buffer
            receivedMessageBuffer.append(text)

            // Log the received part
            output("Received part: $text")

            // Check if the complete JSON message is received
            if (text.contains("[DONE]")) {
                // Process the complete JSON message
                val completeMessage = receivedMessageBuffer.toString()
                output("Complete message: $completeMessage")

                // Clear the buffer for the next message
                receivedMessageBuffer = StringBuilder()
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            // WebSocket is about to close
            webSocket.close(NORMAL_CLOSURE_STATUS, null)
            output("Closing : $code / $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            // WebSocket connection closed
//            webSocket.close(NORMAL_CLOSURE_STATUS, null)
            output("Closed : $code / $reason")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
            requireActivity().runOnUiThread {
                output("Error : ${t.message}")
//                val jsonObject = JsonObject()
//                jsonObject.addProperty("_rev", "")
//                jsonObject.addProperty("_id", "")
//                jsonObject.addProperty("time", "")
//                jsonObject.addProperty("title", "")
//                jsonObject.addProperty("updatedTime", "")
//
//                val conversationsArray = JsonArray()
//                val conversationObject = JsonObject()
//                conversationObject.addProperty("query", query)
//                conversationObject.addProperty("response", "")
//                conversationsArray.add(conversationObject)
//
//                jsonObject.add("conversations", conversationsArray)
//                requireActivity().runOnUiThread {
//                    RealmChatHistory.insert(mRealm, jsonObject)
//                }
                fragmentChatDetailBinding.textGchatIndicator.visibility = View.VISIBLE
                fragmentChatDetailBinding.textGchatIndicator.text = "${t.message}"
                fragmentChatDetailBinding.buttonGchatSend.isEnabled = true
                fragmentChatDetailBinding.editGchatMessage.isEnabled = true
                fragmentChatDetailBinding.imageGchatLoading.visibility = View.INVISIBLE
            }
        }

        fun output(text: String?) {
            Log.d("PieSocket", text!!)
        }
    }

    companion object {
        private const val NORMAL_CLOSURE_STATUS = 1000
    }
}