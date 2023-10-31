package org.ole.planet.myplanet.ui.chat

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.RowChatHistoryBinding
import org.ole.planet.myplanet.model.RealmChatHistory

class ChatHistoryListAdapter(var context: Context, var chatHistory: List<RealmChatHistory>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private lateinit var rowChatHistoryBinding: RowChatHistoryBinding
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        rowChatHistoryBinding = RowChatHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderChat(rowChatHistoryBinding)
    }

    override fun getItemCount(): Int {
        return chatHistory.size
    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        rowChatHistoryBinding.chatTitle.text = chatHistory[position].id
        rowChatHistoryBinding.root.setOnClickListener {
            Log.d("logged", chatHistory[position].id)
        }
    }

    class ViewHolderChat(rowChatHistoryBinding: RowChatHistoryBinding) : RecyclerView.ViewHolder(rowChatHistoryBinding.root)
}