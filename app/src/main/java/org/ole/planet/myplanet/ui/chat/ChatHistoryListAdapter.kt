package org.ole.planet.myplanet.ui.chat

import android.app.AlertDialog
import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import io.realm.RealmList
import org.ole.planet.myplanet.databinding.AddNoteDialogBinding
import org.ole.planet.myplanet.databinding.ChatShareDialogBinding
import org.ole.planet.myplanet.databinding.GrandChildRecyclerviewDialogBinding
import org.ole.planet.myplanet.databinding.RowChatHistoryBinding
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.ui.news.ExpandableListAdapter
import org.ole.planet.myplanet.ui.news.GrandChildAdapter


class ChatHistoryListAdapter(var context: Context, private var chatHistory: List<RealmChatHistory>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private lateinit var rowChatHistoryBinding: RowChatHistoryBinding
    private var chatHistoryItemClickListener: ChatHistoryItemClickListener? = null
    private var filteredChatHistory: List<RealmChatHistory> = chatHistory
    private var chatTitle: String? = ""
    lateinit var expandableListAdapter: ExpandableListAdapter
    lateinit var expandableTitleList: List<String>
    lateinit var expandableDetailList: HashMap<String, List<String>>

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
            val chatShareDialogBinding = ChatShareDialogBinding.inflate(LayoutInflater.from(context))
            expandableDetailList = getData() as HashMap<String, List<String>>
            expandableTitleList = ArrayList<String>(expandableDetailList.keys)
            expandableListAdapter = ExpandableListAdapter(context, expandableTitleList, expandableDetailList)
            chatShareDialogBinding.listView.setAdapter(expandableListAdapter)

            chatShareDialogBinding.listView.setOnGroupExpandListener { groupPosition ->
                Toast.makeText(context, expandableTitleList[groupPosition] + " List Expanded.", Toast.LENGTH_SHORT).show()
            }

            chatShareDialogBinding.listView.setOnGroupCollapseListener { groupPosition ->
                Toast.makeText(context, expandableTitleList[groupPosition] + " List Collapsed.", Toast.LENGTH_SHORT).show()
            }

            chatShareDialogBinding.listView.setOnChildClickListener { parent, v, groupPosition, childPosition, id ->
                if (expandableTitleList[groupPosition] == "share with team/Enterprises") {
                    val grandChildList = if (expandableDetailList[expandableTitleList[groupPosition]]?.get(childPosition) == "Teams") {
                        listOf("Team A", "Team B", "Team C") // Replace with your actual data
                    } else {
                        listOf("Enterprise A", "Enterprise B", "Enterprise C") // Replace with your actual data
                    }
                    Log.d(TAG, "grandChildList: $grandChildList")
                    showGrandChildRecyclerView(grandChildList)
                } else {
                    showEditTextAndShareButton()
                }
                false
            }

            val builder = AlertDialog.Builder(context)
            builder.setView(chatShareDialogBinding.root)
            builder.setPositiveButton("close") { dialog, _ ->
                dialog.dismiss()
            }
            val dialog = builder.create()
            dialog.show()
        }
    }

    private fun showGrandChildRecyclerView(items: List<String>) {
        val grandChildDialogBinding = GrandChildRecyclerviewDialogBinding.inflate(LayoutInflater.from(context))
        val grandChildAdapter = GrandChildAdapter(items) { selectedItem ->
            showEditTextAndShareButton(selectedItem)
        }
        grandChildDialogBinding.recyclerView.adapter = grandChildAdapter

        val builder = AlertDialog.Builder(context)
        builder.setView(grandChildDialogBinding.root)
        builder.setPositiveButton("close") { dialog, _ ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun showEditTextAndShareButton(selectedItem: String? = null) {
        val addNoteDialogBinding = AddNoteDialogBinding.inflate(LayoutInflater.from(context))

        val builder = AlertDialog.Builder(context)
        builder.setView(addNoteDialogBinding.root)
        builder.setPositiveButton("Share") { dialog, _ ->
            val inputText = addNoteDialogBinding.editText.text.toString()
            // Handle the share action here with inputText and selectedItem
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
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