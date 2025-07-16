package org.ole.planet.myplanet.ui.news

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.chat.ChatAdapter
import org.ole.planet.myplanet.databinding.RowNewsBinding

class ChatHandler(
    private val context: Context,
    private val listener: AdapterNews.OnNewsItemClickListener?,
    var sessionUser: RealmUserModel?
) {
    fun handleChat(binding: RowNewsBinding, news: RealmNews) {
        if (news.newsId?.isNotEmpty() == true) {
            val conversations = Gson().fromJson(news.conversations, Array<Conversation>::class.java).toList()
            val chatAdapter = ChatAdapter(ArrayList(), context, binding.recyclerGchat)

            if (sessionUser?.id?.startsWith("guest") == false) {
                chatAdapter.setOnChatItemClickListener(object : ChatAdapter.OnChatItemClickListener {
                    override fun onChatItemClick(position: Int, chatItem: String) {
                        listener?.onNewsItemClick(news)
                    }
                })
            }

            for (conversation in conversations) {
                val query = conversation.query
                val response = conversation.response
                if (query != null) {
                    chatAdapter.addQuery(query)
                }
                chatAdapter.responseSource = ChatAdapter.RESPONSE_SOURCE_SHARED_VIEW_MODEL
                if (response != null) {
                    chatAdapter.addResponse(response)
                }
            }

            binding.recyclerGchat.adapter = chatAdapter
            binding.recyclerGchat.layoutManager = LinearLayoutManager(context)
            binding.recyclerGchat.visibility = View.VISIBLE
            binding.sharedChat.visibility = View.VISIBLE
        } else {
            binding.recyclerGchat.visibility = View.GONE
            binding.sharedChat.visibility = View.GONE
        }
    }
}

