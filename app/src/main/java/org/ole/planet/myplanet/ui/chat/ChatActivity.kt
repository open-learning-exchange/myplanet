package org.ole.planet.myplanet.ui.chat

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.MediaType
import okhttp3.RequestBody
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.ApiClient
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.utilities.Utilities
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ChatActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var sendMessage: ImageView
    private lateinit var chatMessage: EditText
    private lateinit var imageLoading: ImageView
    private lateinit var mAdapter: ChatAdapter
    private lateinit var errorIndicator: TextView
    private lateinit var back: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        sendMessage = findViewById(R.id.button_gchat_send)
        chatMessage = findViewById(R.id.edit_gchat_message)
        recyclerView = findViewById(R.id.recycler_gchat)
        imageLoading = findViewById(R.id.image_gchat_loading)
        errorIndicator = findViewById(R.id.text_gchat_indicator)
        back = findViewById(R.id.back)

        mAdapter = ChatAdapter(ArrayList(), this)
        recyclerView.adapter = mAdapter
        val layoutManager: RecyclerView.LayoutManager = object : LinearLayoutManager(this) {
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
                val message = "${chatMessage.text}".replace("\n", " ")
                mAdapter.addQuery(message)
                val jsonContent = "{\"content\": \"$message\"}"
                val requestBody = RequestBody.create(MediaType.parse("application/json"), jsonContent)
                makePostRequest(requestBody)
                chatMessage.text.clear()
                errorIndicator.visibility = View.GONE
            }
        }

        chatMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                errorIndicator.visibility = View.GONE
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        back.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish()
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBackPressed()
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    fun makePostRequest(content: RequestBody) {
        sendMessage.isEnabled = false
        chatMessage.isEnabled = false
        imageLoading.visibility = View.VISIBLE

        val apiInterface = ApiClient.getClient().create(ApiInterface::class.java)
        val call = apiInterface.chatGpt(Utilities.getHostUrl(), content)

        Log.d("content", "$content")
        call.enqueue(object : Callback<ChatModel> {
            override fun onResponse(call: Call<ChatModel>, response: Response<ChatModel>) {
                Log.d("response", "${response.body()}")
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}