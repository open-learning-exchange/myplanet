package org.ole.planet.myplanet.ui.chat

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.util.Date
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnChatHistoryItemClickListener
import org.ole.planet.myplanet.databinding.AddNoteDialogBinding
import org.ole.planet.myplanet.databinding.ChatShareDialogBinding
import org.ole.planet.myplanet.databinding.GrandChildRecyclerviewDialogBinding
import org.ole.planet.myplanet.databinding.RowChatHistoryBinding
import org.ole.planet.myplanet.model.ChatShareTargets
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmConversation
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.TeamSummary
import org.ole.planet.myplanet.ui.teams.TeamsSelectionAdapter
import org.ole.planet.myplanet.utils.ChatHistoryUtils.extractSharedViewInIds
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.JsonUtils

class ChatHistoryAdapter(
    private val context: Context,
    chatHistoryList: List<RealmChatHistory>,
    private var currentUser: RealmUser?,
    private var newsList: List<RealmNews>,
    private var shareTargets: ChatShareTargets,
    private val onShareChat: (HashMap<String?, String>, RealmChatHistory) -> Unit,
) : ListAdapter<RealmChatHistory, ChatHistoryAdapter.ViewHolderChat>(
    DiffUtils.itemCallback(
        areItemsTheSame = { oldItem, newItem ->
            val oldId = oldItem._id
            val newId = newItem._id
            oldId != null && newId != null && oldId == newId
        },
        areContentsTheSame = { oldItem, newItem ->
            oldItem._rev == newItem._rev &&
                oldItem.lastUsed == newItem.lastUsed &&
                oldItem.title == newItem.title &&
                oldItem.conversations?.firstOrNull()?.query ==
                newItem.conversations?.firstOrNull()?.query
        }
    )
) {
    private lateinit var rowChatHistoryBinding: RowChatHistoryBinding
    private var chatHistoryItemClickListener: OnChatHistoryItemClickListener? = null
    private var chatTitle: String? = ""
    private lateinit var expandableListAdapter: ChatShareTargetAdapter
    private lateinit var expandableTitleList: List<String>
    private lateinit var expandableDetailList: HashMap<String, List<String>>
    private var cachedSharedViewInIds: Map<String, Set<String>> = emptyMap()

    init {
        submitList(chatHistoryList)
    }

    fun updateCachedData(user: RealmUser?, sharedNews: List<RealmNews>) {
        currentUser = user
        newsList = sharedNews
        cachedSharedViewInIds = extractSharedViewInIds(sharedNews)
    }

    fun updateShareTargets(newTargets: ChatShareTargets) {
        shareTargets = newTargets
    }

    fun notifyChatShared(chatId: String?) {
        val position = currentList.indexOfFirst { it._id == chatId }
        if (position != -1) {
            notifyItemChanged(position, PAYLOAD_CHAT_SHARED)
        }
    }

    fun setChatHistoryItemClickListener(listener: OnChatHistoryItemClickListener) {
        chatHistoryItemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderChat {
        rowChatHistoryBinding = RowChatHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderChat(rowChatHistoryBinding)
    }

    fun updateChatHistory(newChatHistory: List<RealmChatHistory>) {
        submitList(newChatHistory)
    }

    override fun onBindViewHolder(holder: ViewHolderChat, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_CHAT_SHARED)) {
            bindShareChat(holder, getItem(position))
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: ViewHolderChat, position: Int) {
        val item = getItem(position)
        if (item.conversations != null && item.conversations?.isNotEmpty() == true) {
            holder.rowChatHistoryBinding.chatTitle.text = item.conversations?.get(0)?.query
            holder.rowChatHistoryBinding.chatTitle.contentDescription = item.conversations?.get(0)?.query
            chatTitle = item.conversations?.get(0)?.query
        } else {
            holder.rowChatHistoryBinding.chatTitle.text = item.title
            holder.rowChatHistoryBinding.chatTitle.contentDescription = item.title
            chatTitle = item.title
        }

        holder.rowChatHistoryBinding.root.setOnClickListener {
            holder.rowChatHistoryBinding.chatCardView.contentDescription = chatTitle
            chatHistoryItemClickListener?.onChatHistoryItemClicked(
                item.conversations?.toList(),
                "${item._id}",
                item._rev,
                item.aiProvider
            )
        }

        bindShareChat(holder, item)
    }

    private fun bindShareChat(holder: ViewHolderChat, item: RealmChatHistory) {
        holder.rowChatHistoryBinding.shareChat.setImageResource(R.drawable.baseline_share_24)

        holder.rowChatHistoryBinding.shareChat.setOnClickListener {
            val chatShareDialogBinding = ChatShareDialogBinding.inflate(LayoutInflater.from(context))
            var dialog: AlertDialog? = null

            val sharedIds = getSharedViewInIds(item._id)
            val isCommunityShared = shareTargets.community?._id?.let { it in sharedIds } == true
            val sharedChildren = if (isCommunityShared) setOf(context.getString(R.string.community)) else emptySet()
            expandableDetailList = getData() as HashMap<String, List<String>>
            expandableTitleList = ArrayList(expandableDetailList.keys)
            expandableListAdapter = ChatShareTargetAdapter(context, expandableTitleList, expandableDetailList, sharedChildren)
            chatShareDialogBinding.listView.setAdapter(expandableListAdapter)

            chatShareDialogBinding.listView.setOnChildClickListener { _, _, groupPosition, childPosition, _ ->
                if (expandableTitleList[groupPosition] == context.getString(R.string.share_with_team_enterprise)) {
                    val section = expandableDetailList[expandableTitleList[groupPosition]]?.get(childPosition)
                    if (section == context.getString(R.string.teams)) {
                        showGrandChildRecyclerView(shareTargets.teams, context.getString(R.string.teams), item, sharedIds)
                    } else {
                        showGrandChildRecyclerView(shareTargets.enterprises, context.getString(R.string.enterprises), item, sharedIds)
                    }
                } else if (!isCommunityShared) {
                    showEditTextAndShareButton(shareTargets.community, context.getString(R.string.community), item)
                }
                dialog?.dismiss()
                false
            }

            val builder = AlertDialog.Builder(context)
            builder.setView(chatShareDialogBinding.root)
            builder.setPositiveButton(context.getString(R.string.close)) { _, _ ->
                dialog?.dismiss()
            }
            dialog = builder.create()

            val backgroundColor = ContextCompat.getColor(context, R.color.daynight_grey)
            dialog.window?.setBackgroundDrawable(backgroundColor.toDrawable())

            dialog.show()
        }
    }

    @androidx.annotation.VisibleForTesting(otherwise = androidx.annotation.VisibleForTesting.PRIVATE)
    internal fun getSharedViewInIds(chatId: String?): Set<String> {
        if (chatId == null) return emptySet()
        return cachedSharedViewInIds[chatId] ?: emptySet()
    }

    private fun showGrandChildRecyclerView(items: List<TeamSummary>, section: String, realmChatHistory: RealmChatHistory, sharedIds: Set<String> = emptySet()) {
        val grandChildDialogBinding = GrandChildRecyclerviewDialogBinding.inflate(LayoutInflater.from(context))
        var dialog: AlertDialog? = null

        grandChildDialogBinding.title.text = if (section == context.getString(R.string.teams)) {
            context.getString(R.string.team)
        } else {
            context.getString(R.string.enterprises)
        }

        val teamSelectionAdapter = TeamsSelectionAdapter(section, sharedIds) { selectedItem ->
            showEditTextAndShareButton(selectedItem, section, realmChatHistory)
            dialog?.dismiss()
        }
        grandChildDialogBinding.recyclerView.layoutManager = LinearLayoutManager(context)
        grandChildDialogBinding.recyclerView.adapter = teamSelectionAdapter
        teamSelectionAdapter.submitList(items)

        val builder = AlertDialog.Builder(context, R.style.CustomAlertDialog)
        builder.setView(grandChildDialogBinding.root)
        builder.setPositiveButton(context.getString(R.string.close)) { _, _ ->
            dialog?.dismiss()
        }
        dialog = builder.create()
        val backgroundColor = ContextCompat.getColor(context, R.color.daynight_grey)
        dialog.window?.setBackgroundDrawable(backgroundColor.toDrawable())
        dialog.show()
    }

    private fun showEditTextAndShareButton(team: TeamSummary? = null, section: String, chatHistory: RealmChatHistory) {
        val addNoteDialogBinding = AddNoteDialogBinding.inflate(LayoutInflater.from(context))
        val builder = AlertDialog.Builder(context, R.style.AlertDialogTheme)
        builder.setView(addNoteDialogBinding.root)
        builder.setPositiveButton(context.getString(R.string.share_chat)) { dialog, _ ->
            val serializedConversations = chatHistory.conversations?.map { serializeConversation(it) }
            val serializedMap = HashMap<String?, String>()
            serializedMap["_id"] = chatHistory._id ?: ""
            serializedMap["_rev"] = chatHistory._rev ?: ""
            serializedMap["title"] = "${chatHistory.title}".trim()
            serializedMap["user"] = chatHistory.user ?: ""
            serializedMap["aiProvider"] = chatHistory.aiProvider ?: ""
            serializedMap["createdDate"] = "${Date().time}"
            serializedMap["updatedDate"] = "${Date().time}"
            serializedMap["conversations"] = JsonUtils.gson.toJson(serializedConversations)

            val map = HashMap<String?, String>()
            map["message"] = "${addNoteDialogBinding.editText.text}"
            map["viewInId"] = team?._id ?: ""
            map["viewInSection"] = section
            map["messageType"] = team?.teamType ?: ""
            map["messagePlanetCode"] = team?.teamPlanetCode ?: ""
            map["chat"] = "true"
            map["news"] = JsonUtils.gson.toJson(serializedMap)

            onShareChat(map, chatHistory)
            dialog.dismiss()
        }
        builder.setNegativeButton(context.getString(R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun serializeConversation(conversation: RealmConversation): HashMap<String?, String> {
        val conversationMap = HashMap<String?, String>()
        conversationMap["query"] = conversation.query ?: ""
        conversationMap["response"] = conversation.response ?: ""
        return conversationMap
    }

    private fun getData(): Map<String, List<String>> {
        val expandableListDetail: MutableMap<String, List<String>> = HashMap()
        expandableListDetail[context.getString(R.string.share_with_community)] = listOf(context.getString(R.string.community))

        val teams: MutableList<String> = ArrayList()
        teams.add(context.getString(R.string.teams))
        teams.add(context.getString(R.string.enterprises))

        expandableListDetail[context.getString(R.string.share_with_team_enterprise)] = teams
        return expandableListDetail
    }

    class ViewHolderChat(val rowChatHistoryBinding: RowChatHistoryBinding) : RecyclerView.ViewHolder(rowChatHistoryBinding.root)

    companion object {
        const val PAYLOAD_CHAT_SHARED = "PAYLOAD_CHAT_SHARED"
    }
}
