package org.ole.planet.myplanet.ui.chat

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ExpandableListView
import androidx.recyclerview.widget.RecyclerView
import io.realm.RealmList
import org.ole.planet.myplanet.databinding.ChatShareDialogBinding
import org.ole.planet.myplanet.databinding.DialogServerUrlBinding
import org.ole.planet.myplanet.databinding.RowChatHistoryBinding
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.utilities.ExpandableListAdapter

class ChatHistoryListAdapter(var context: Context, private var chatHistory: List<RealmChatHistory>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private lateinit var rowChatHistoryBinding: RowChatHistoryBinding
    private var chatHistoryItemClickListener: ChatHistoryItemClickListener? = null
    private var filteredChatHistory: List<RealmChatHistory> = chatHistory
    private var chatTitle: String? = ""
    private lateinit var expandableListAdapter: ExpandableListAdapter
    private lateinit var expandableListTitle: List<String>
    private lateinit var expandableListDetail: Map<String, List<String>>

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
            viewHolderChat.rowChatHistoryBinding.chatTitle.text = filteredChatHistory[position].conversations?.get(0)?.query
            viewHolderChat.rowChatHistoryBinding.chatTitle.contentDescription = filteredChatHistory[position].conversations?.get(0)?.query
            chatTitle = filteredChatHistory[position].conversations?.get(0)?.query
        } else {
            viewHolderChat.rowChatHistoryBinding.chatTitle.text = filteredChatHistory[position].title
            viewHolderChat.rowChatHistoryBinding.chatTitle.contentDescription = filteredChatHistory[position].title
            chatTitle = filteredChatHistory[position].title
        }

        viewHolderChat.rowChatHistoryBinding.root.setOnClickListener {
            viewHolderChat.rowChatHistoryBinding.chatCardView.contentDescription = chatTitle
            chatHistoryItemClickListener?.onChatHistoryItemClicked(
                filteredChatHistory[position].conversations,
                "${filteredChatHistory[position]._id}",
                filteredChatHistory[position]._rev
            )
        }

        viewHolderChat.rowChatHistoryBinding.shareChat.setOnClickListener {
            expandableListDetail = getData()
            expandableListTitle = expandableListDetail.keys.toList()
            expandableListAdapter = ExpandableListAdapter(context, expandableListTitle, expandableListDetail)

            val chatShareDialogBinding = ChatShareDialogBinding.inflate(LayoutInflater.from(context))
            chatShareDialogBinding.listView.setAdapter(expandableListAdapter)
            val dialogBuilder = AlertDialog.Builder(context)
                .setView(chatShareDialogBinding.root)
                .setPositiveButton("close") { dialog, _ ->
                    dialog.dismiss()
                }

            val dialog = dialogBuilder.create()
            dialog.show()
        }
    }
    private fun getData(): Map<String, List<String>> {
        val expandableListDetail: MutableMap<String, List<String>> = HashMap()

        val community: MutableList<String> = ArrayList()
        community.add("Community")

        val teams: MutableList<String> = ArrayList()
        teams.add("Teams")
        teams.add("Enterprises")

        expandableListDetail["Share with Community"] = community
        expandableListDetail["Share with Team/Enterprises"] = teams

        return expandableListDetail
    }


    class ViewHolderChat(val rowChatHistoryBinding: RowChatHistoryBinding) : RecyclerView.ViewHolder(rowChatHistoryBinding.root)
}