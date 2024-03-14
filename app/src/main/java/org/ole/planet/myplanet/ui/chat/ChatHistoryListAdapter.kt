package org.ole.planet.myplanet.ui.chat

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.realm.RealmList
import org.ole.planet.myplanet.databinding.RowChatHistoryBinding
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.RealmChatHistory

class ChatHistoryListAdapter(var context: Context, private var chatHistory: List<RealmChatHistory>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private lateinit var rowChatHistoryBinding: RowChatHistoryBinding
    private var chatHistoryItemClickListener: ChatHistoryItemClickListener? = null
    private var filteredChatHistory: List<RealmChatHistory> = chatHistory

    interface ChatHistoryItemClickListener {
        fun onChatHistoryItemClicked(conversations: RealmList<Conversation>?, _id: String, _rev: String?)
    }

    fun setChatHistoryItemClickListener(listener: ChatHistoryItemClickListener) {
        chatHistoryItemClickListener = listener
    }

    fun filter(query: String) {
        filteredChatHistory = chatHistory.filter { chat ->
            if (chat.conversations != null && chat.conversations?.isNotEmpty() == true) {
                chat.conversations!![0]?.query?.contains(query, ignoreCase = true) == true
            } else {
                chat.title?.contains(query, ignoreCase = true) ==true
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        rowChatHistoryBinding = RowChatHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderChat(rowChatHistoryBinding)
    }

    override fun getItemCount(): Int {
        return filteredChatHistory.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val viewHolderChat = holder as ViewHolderChat
        if (filteredChatHistory[position].conversations != null && filteredChatHistory[position].conversations?.isNotEmpty() == true) {
            viewHolderChat.rowChatHistoryBinding.chatTitle.text = filteredChatHistory[position].conversations?.get(0)!!.query
            viewHolderChat.rowChatHistoryBinding.chatCardView.contentDescription = filteredChatHistory[position].conversations?.get(0)!!.query
        } else {
            viewHolderChat.rowChatHistoryBinding.chatTitle.text = filteredChatHistory[position].title
            viewHolderChat.rowChatHistoryBinding.chatCardView.contentDescription = filteredChatHistory[position].title
        }

        viewHolderChat.rowChatHistoryBinding.root.setOnClickListener {
            chatHistoryItemClickListener?.onChatHistoryItemClicked(
                filteredChatHistory[position].conversations,
                "${filteredChatHistory[position]._id}",
                filteredChatHistory[position]._rev
            )
        }
    }

    class ViewHolderChat(val rowChatHistoryBinding: RowChatHistoryBinding) : RecyclerView.ViewHolder(rowChatHistoryBinding.root)
}