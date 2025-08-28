package org.ole.planet.myplanet.ui.chat

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import io.realm.Case
import java.text.Normalizer
import java.util.Date
import java.util.Locale
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AddNoteDialogBinding
import org.ole.planet.myplanet.databinding.ChatShareDialogBinding
import org.ole.planet.myplanet.databinding.GrandChildRecyclerviewDialogBinding
import org.ole.planet.myplanet.databinding.RowChatHistoryBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNews.Companion.createNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.news.ExpandableListAdapter
import org.ole.planet.myplanet.ui.news.GrandChildAdapter

class ChatHistoryListAdapter(
    var context: Context,
    private var chatHistory: List<RealmChatHistory>,
    private val fragment: ChatHistoryListFragment,
    private val databaseService: DatabaseService,
    private val settings: SharedPreferences
) : ListAdapter<RealmChatHistory, ChatHistoryListAdapter.ViewHolderChat>(DIFF_CALLBACK) {
    private lateinit var rowChatHistoryBinding: RowChatHistoryBinding
    private var chatHistoryItemClickListener: ChatHistoryItemClickListener? = null
    private var chatTitle: String? = ""
    private lateinit var expandableListAdapter: ExpandableListAdapter
    private lateinit var expandableTitleList: List<String>
    private lateinit var expandableDetailList: HashMap<String, List<String>>
    var user: RealmUserModel? = null
    private var newsList: List<RealmNews> = emptyList()

    init {
        chatHistory = chatHistory.sortedByDescending { it.lastUsed }
        submitList(chatHistory)
    }

    interface ChatHistoryItemClickListener {
        fun onChatHistoryItemClicked(conversations: List<Conversation>?, id: String, rev: String?, aiProvider: String?)
    }

    fun setChatHistoryItemClickListener(listener: ChatHistoryItemClickListener) {
        chatHistoryItemClickListener = listener
    }

    fun filter(query: String) {
        val filteredChatHistory = chatHistory.filter { chat ->
            if (chat.conversations != null && chat.conversations?.isNotEmpty() == true) {
                chat.conversations?.get(0)?.query?.contains(query, ignoreCase = true) == true
            } else {
                chat.title?.contains(query, ignoreCase = true) == true
            }
        }
        submitList(filteredChatHistory)
    }

    private fun normalizeText(str: String): String {
        return Normalizer.normalize(str.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    fun search(s: String, isFullSearch: Boolean, isQuestion: Boolean) {
        val results = if (isFullSearch) {
            FullConvoSearch(s, isQuestion)
        } else {
            searchByTitle(s)
        }
        submitList(results)
    }

    private fun FullConvoSearch(s: String, isQuestion: Boolean): List<RealmChatHistory> {
        var conversation: String?
        val queryParts = s.split(" ").filterNot { it.isEmpty() }
        val normalizedQuery = normalizeText(s)
        val inTitleStartQuery = mutableListOf<RealmChatHistory>()
        val inTitleContainsQuery = mutableListOf<RealmChatHistory>()
        val startsWithQuery = mutableListOf<RealmChatHistory>()
        val containsQuery = mutableListOf<RealmChatHistory>()

        for (chat in chatHistory) {
            if (chat.conversations != null && chat.conversations?.isNotEmpty() == true) {
                for (i in 0 until chat.conversations!!.size) {
                    conversation = if (isQuestion) {
                        chat.conversations?.get(i)?.query?.let { normalizeText(it) }
                    } else {
                        chat.conversations?.get(i)?.response?.let { normalizeText(it) }
                    }
                    if (conversation == null) continue
                    if (conversation.startsWith(normalizedQuery, ignoreCase = true)) {
                        if (i == 0) inTitleStartQuery.add(chat) else startsWithQuery.add(chat)
                        break
                    } else if (queryParts.all { conversation.contains(normalizeText(it), ignoreCase = true) }) {
                        if (i == 0) inTitleContainsQuery.add(chat) else containsQuery.add(chat)
                        break
                    }
                }
            }
        }
        return inTitleStartQuery + inTitleContainsQuery + startsWithQuery + containsQuery
    }

    private fun searchByTitle(s: String): List<RealmChatHistory> {
        var title: String?
        val queryParts = s.split(" ").filterNot { it.isEmpty() }
        val normalizedQuery = normalizeText(s)
        val startsWithQuery = mutableListOf<RealmChatHistory>()
        val containsQuery = mutableListOf<RealmChatHistory>()

        for (chat in chatHistory) {
            if (chat.conversations != null && chat.conversations?.isNotEmpty() == true) {
                title = chat.conversations?.get(0)?.query?.let { normalizeText(it) }
            } else {
                title = chat.title?.let { normalizeText(it) }
            }
            if (title == null) continue
            if (title.startsWith(normalizedQuery, ignoreCase = true)) {
                startsWithQuery.add(chat)
            } else if (queryParts.all { title.contains(normalizeText(it), ignoreCase = true) }) {
                containsQuery.add(chat)
            }
        }
        return startsWithQuery + containsQuery
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderChat {
        rowChatHistoryBinding = RowChatHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        databaseService.withRealm { realm ->
            val userId = settings.getString("userId", "")
            user = realm.where(RealmUserModel::class.java)
                .equalTo("id", userId)
                .findFirst()?.let { realm.copyFromRealm(it) }

            newsList = user?.planetCode?.let { planetCode ->
                realm.copyFromRealm(
                    realm.where(RealmNews::class.java)
                        .equalTo("docType", "message", Case.INSENSITIVE)
                        .equalTo("createdOn", planetCode, Case.INSENSITIVE)
                        .findAll()
                )
            } ?: emptyList()
        }
        return ViewHolderChat(rowChatHistoryBinding)
    }

    fun updateChatHistory(newChatHistory: List<RealmChatHistory>) {
        chatHistory = newChatHistory.sortedByDescending { it.lastUsed }
        submitList(chatHistory)
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

        val isInNewsList = newsList.any { newsItem ->
            newsItem.newsId == item._id
        }

        if (isInNewsList) {
            holder.rowChatHistoryBinding.shareChat.setImageResource(R.drawable.baseline_check_24)
        } else {
            holder.rowChatHistoryBinding.shareChat.setImageResource(R.drawable.baseline_share_24)
            holder.rowChatHistoryBinding.shareChat.setOnClickListener {
                val chatShareDialogBinding = ChatShareDialogBinding.inflate(LayoutInflater.from(context))
                var dialog: AlertDialog? = null

                expandableDetailList = getData() as HashMap<String, List<String>>
                expandableTitleList = ArrayList(expandableDetailList.keys)
                expandableListAdapter = ExpandableListAdapter(context, expandableTitleList, expandableDetailList)
                chatShareDialogBinding.listView.setAdapter(expandableListAdapter)

                chatShareDialogBinding.listView.setOnChildClickListener { _, _, groupPosition, childPosition, _ ->
                    if (expandableTitleList[groupPosition] == context.getString(R.string.share_with_team_enterprise)) {
                        databaseService.withRealm { realm ->
                            val teamList = realm.copyFromRealm(
                                realm.where(RealmMyTeam::class.java)
                                    .isEmpty("teamId").notEqualTo("status", "archived")
                                    .equalTo("type", "team").findAll()
                            )

                            val enterpriseList = realm.copyFromRealm(
                                realm.where(RealmMyTeam::class.java)
                                    .isEmpty("teamId").notEqualTo("status", "archived")
                                    .equalTo("type", "enterprise").findAll()
                            )

                            if (expandableDetailList[expandableTitleList[groupPosition]]?.get(childPosition) == context.getString(R.string.teams)) {
                                showGrandChildRecyclerView(teamList, context.getString(R.string.teams), item)
                            } else {
                                showGrandChildRecyclerView(enterpriseList, context.getString(R.string.enterprises), item)
                            }
                        }
                    } else {
                        val sParentcode = settings.getString("parentCode", "")
                        val communityName = settings.getString("communityName", "")
                        val teamId = "$communityName@$sParentcode"
                        databaseService.withRealm { realm ->
                            val community = realm.where(RealmMyTeam::class.java)
                                .equalTo("_id", teamId)
                                .findFirst()?.let { realm.copyFromRealm(it) }
                            showEditTextAndShareButton(community, context.getString(R.string.community), item)
                        }
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
    }

    private fun showGrandChildRecyclerView(items: List<RealmMyTeam>, section: String, realmChatHistory: RealmChatHistory) {
        val grandChildDialogBinding = GrandChildRecyclerviewDialogBinding.inflate(LayoutInflater.from(context))
        var dialog: AlertDialog? = null

        grandChildDialogBinding.title.text = if (section == context.getString(R.string.teams)) {
            context.getString(R.string.team)
        } else {
            context.getString(R.string.enterprises)
        }

        val grandChildAdapter = GrandChildAdapter(items, section) { selectedItem ->
            showEditTextAndShareButton(selectedItem, section, realmChatHistory)
            dialog?.dismiss()
        }
        grandChildDialogBinding.recyclerView.layoutManager = LinearLayoutManager(context)
        grandChildDialogBinding.recyclerView.adapter = grandChildAdapter

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

    private fun showEditTextAndShareButton(team: RealmMyTeam? = null, section: String, chatHistory: RealmChatHistory) {
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
            serializedMap["conversations"] = Gson().toJson(serializedConversations)

            val map = HashMap<String?, String>()
            map["message"] = "${addNoteDialogBinding.editText.text}"
            map["viewInId"] = team?._id ?: ""
            map["viewInSection"] = section
            map["messageType"] = team?.teamType ?: ""
            map["messagePlanetCode"] = team?.teamPlanetCode ?: ""
            map["chat"] = "true"
            map["news"] = Gson().toJson(serializedMap)

            databaseService.withRealm { realm ->
                createNews(map, realm, user, null)
            }

            fragment.refreshChatHistoryList()
            dialog.dismiss()
        }
        builder.setNegativeButton(context.getString(R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun serializeConversation(conversation: Conversation): HashMap<String?, String> {
        val conversationMap = HashMap<String?, String>()
        conversationMap["query"] = conversation.query ?: ""
        conversationMap["response"] = conversation.response ?: ""
        return conversationMap
    }

    private fun getData(): Map<String, List<String>> {
        val expandableListDetail: MutableMap<String, List<String>> = HashMap()
        val community: MutableList<String> = ArrayList()
        community.add(context.getString(R.string.community))

        val teams: MutableList<String> = ArrayList()
        teams.add(context.getString(R.string.teams))
        teams.add(context.getString(R.string.enterprises))

        expandableListDetail[context.getString(R.string.share_with_community)] = community
        expandableListDetail[context.getString(R.string.share_with_team_enterprise)] = teams
        return expandableListDetail
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RealmChatHistory>() {
            override fun areItemsTheSame(oldItem: RealmChatHistory, newItem: RealmChatHistory): Boolean {
                return oldItem._id == newItem._id
            }

            override fun areContentsTheSame(oldItem: RealmChatHistory, newItem: RealmChatHistory): Boolean {
                return oldItem == newItem
            }
        }
    }

    class ViewHolderChat(val rowChatHistoryBinding: RowChatHistoryBinding) : RecyclerView.ViewHolder(rowChatHistoryBinding.root)
}
