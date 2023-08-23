package org.ole.planet.myplanet.ui.chat

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.MediaType
import okhttp3.RequestBody
import org.ole.planet.myplanet.databinding.ActivityChatBinding
import org.ole.planet.myplanet.datamanager.ApiClient
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.utilities.Utilities
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ChatActivity : AppCompatActivity() {
    private lateinit var activityChatBinding: ActivityChatBinding
    private lateinit var mAdapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityChatBinding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(activityChatBinding.root)

        mAdapter = ChatAdapter(ArrayList(), this)
        activityChatBinding.recyclerGchat.adapter = mAdapter
        val layoutManager: RecyclerView.LayoutManager = object : LinearLayoutManager(this) {
            override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
                return RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        }
        activityChatBinding.recyclerGchat.layoutManager = layoutManager
        activityChatBinding.recyclerGchat.isNestedScrollingEnabled = true
        activityChatBinding.recyclerGchat.setHasFixedSize(true)


        if (mAdapter.itemCount > 0) {
            activityChatBinding.recyclerGchat.scrollToPosition(mAdapter.itemCount - 1)
            activityChatBinding.recyclerGchat.smoothScrollToPosition(mAdapter.itemCount - 1)
        }

        activityChatBinding.buttonGchatSend.setOnClickListener {
            activityChatBinding.textGchatIndicator.visibility = View.GONE
            if (TextUtils.isEmpty(activityChatBinding.editGchatMessage.text.toString().trim())) {
                activityChatBinding.textGchatIndicator.visibility = View.VISIBLE
                activityChatBinding.textGchatIndicator.text = "Kindly enter message"
            } else {
                val message = "${activityChatBinding.editGchatMessage.text}".replace("\n", " ")
                mAdapter.addQuery(message)
                val jsonContent = "{\"content\": \"$message\"}"
                val requestBody = RequestBody.create(MediaType.parse("application/json"), jsonContent)
                makePostRequest(requestBody)
                activityChatBinding.editGchatMessage.text.clear()
                activityChatBinding.textGchatIndicator.visibility = View.GONE
            }
        }

        activityChatBinding.editGchatMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                activityChatBinding.textGchatIndicator.visibility = View.GONE
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        activityChatBinding.back.setOnClickListener {
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
        activityChatBinding.buttonGchatSend.isEnabled = false
        activityChatBinding.editGchatMessage.isEnabled = false
        activityChatBinding.imageGchatLoading.visibility = View.VISIBLE

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
                    activityChatBinding.textGchatIndicator.visibility = View.VISIBLE
                    activityChatBinding.textGchatIndicator.text = "${response.body()!!.message}"
                    Log.d("failed chat message", "${response.body()!!.message}")
                }

                activityChatBinding.buttonGchatSend.isEnabled = true
                activityChatBinding.editGchatMessage.isEnabled = true
                activityChatBinding.imageGchatLoading.visibility = View.INVISIBLE
            }

            override fun onFailure(call: Call<ChatModel>, t: Throwable) {
                Log.d("onFailure chat message", "${t.message}")
                activityChatBinding.textGchatIndicator.visibility = View.VISIBLE
                activityChatBinding.textGchatIndicator.text = "${t.message}"
                activityChatBinding.buttonGchatSend.isEnabled = true
                activityChatBinding.editGchatMessage.isEnabled = true
                activityChatBinding.imageGchatLoading.visibility = View.INVISIBLE
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