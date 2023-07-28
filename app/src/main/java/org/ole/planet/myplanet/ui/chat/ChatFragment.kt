package org.ole.planet.myplanet.ui.chat

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.MediaType
import okhttp3.RequestBody
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.ApiClient
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.utilities.Utilities
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ChatFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var sendMessage: ImageView
    private lateinit var chatMessage: EditText
    private lateinit var imageLoading: ImageView
    private lateinit var mAdapter: ChatAdapter
    private lateinit var errorIndicator: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_chat, container, false)
        sendMessage = rootView.findViewById(R.id.button_gchat_send)
        chatMessage = rootView.findViewById(R.id.edit_gchat_message)
        recyclerView = rootView.findViewById(R.id.recycler_gchat)
        imageLoading = rootView.findViewById(R.id.image_gchat_loading)
        errorIndicator = rootView.findViewById(R.id.text_gchat_indicator)

        mAdapter = ChatAdapter(ArrayList(), requireContext())
        recyclerView.adapter = mAdapter
        val layoutManager: RecyclerView.LayoutManager = object : LinearLayoutManager(requireContext()) {
            override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
                return RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        }
        recyclerView.layoutManager = layoutManager
        recyclerView.isNestedScrollingEnabled = true
        recyclerView.setHasFixedSize(true)


        if (mAdapter.itemCount > 0) {
            recyclerView.scrollToPosition(mAdapter.itemCount - 1)
            recyclerView.smoothScrollToPosition(mAdapter.itemCount - 1)
        }

        sendMessage.setOnClickListener {
            errorIndicator.visibility = View.GONE
            if (TextUtils.isEmpty(chatMessage.text.toString().trim())) {
                errorIndicator.visibility = View.VISIBLE
                errorIndicator.text = "Kindly enter message"
            } else {
                val message = chatMessage.text.toString()
                mAdapter.addQuery(message)
                val jsonContent = "{\"content\": \"$message\"}"
                val requestBody = RequestBody.create(MediaType.parse("application/json"), jsonContent)
                makePostRequest(requestBody)
                chatMessage.text.clear()
                errorIndicator.visibility = View.GONE
            }
        }
        return rootView
    }

    fun makePostRequest(content: RequestBody) {
        sendMessage.isEnabled = false
        chatMessage.isEnabled = false
        imageLoading.visibility = View.VISIBLE

        val apiInterface = ApiClient.getClient().create(ApiInterface::class.java)
        val call = apiInterface.chatGpt("${Utilities.getHostUrl()}:5000", content)

        call.enqueue(object : Callback<ChatModel> {
            override fun onResponse(call: Call<ChatModel>, response: Response<ChatModel>) {
                if (response.body()!!.status == "Success") {
                    val chatModel = response.body()

                    val history: ArrayList<History> = chatModel?.history ?: ArrayList()

                    val lastItem = history.lastOrNull()
                    if (lastItem != null) {
                        if (lastItem.response != null) {
                            val responseBody = lastItem.response
                            val chatResponse = response.body()?.chat
                            if (chatResponse != null) {
                                mAdapter.addResponse(chatResponse)
                            }
                        }
                    }
                } else {
                    errorIndicator.visibility = View.VISIBLE
                    errorIndicator.text = "${response.body()!!.message}"
                    Log.d("failed chat message", "${response.body()!!.message}")
                }

                sendMessage.isEnabled = true
                chatMessage.isEnabled = true
                imageLoading.visibility = View.INVISIBLE
            }

            override fun onFailure(call: Call<ChatModel>, t: Throwable) {
                Log.d("onFailure chat message", "${t.message}")
                errorIndicator.visibility = View.VISIBLE
                errorIndicator.text = "${t.message}"
                sendMessage.isEnabled = true
                chatMessage.isEnabled = true
                imageLoading.visibility = View.INVISIBLE
            }
        })
    }
}