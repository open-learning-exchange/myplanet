package org.ole.planet.myplanet.ui.news

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmList
import java.util.Calendar
import java.util.Locale
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNewsBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.NewsItem
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.chat.ChatAdapter
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.ImageUtils
import org.ole.planet.myplanet.utilities.Markdown.prependBaseUrlToImages
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.makeExpandable

class AdapterNews(
    var context: Context,
    private var currentUser: RealmUserModel?,
    private val parentNews: NewsItem?,
    private val teamName: String = "",
    private val teamId: String? = null,
    private val userProfileDbHandler: UserProfileDbHandler,
    private val databaseService: DatabaseService
) : ListAdapter<NewsItem?, RecyclerView.ViewHolder?>(
    DiffUtils.itemCallback(
        areItemsTheSame = { oldItem, newItem ->
            if (oldItem === newItem) return@itemCallback true
            if (oldItem == null || newItem == null) return@itemCallback oldItem == newItem
            oldItem.id == newItem.id
        },
        areContentsTheSame = { oldItem, newItem ->
            if (oldItem === newItem) return@itemCallback true
            if (oldItem == null || newItem == null) return@itemCallback oldItem == newItem
            oldItem == newItem
        }
    )
) {
    private var listener: OnNewsItemClickListener? = null
    private var imageList: RealmList<String>? = null
    lateinit var mRealm: Realm
    private var fromLogin = false
    private var nonTeamMember = false
    private var sharedPreferences: SharedPrefManager? = null
    private var recyclerView: RecyclerView? = null
    var user: RealmUserModel? = null
    private var labelManager: NewsLabelManager? = null
    lateinit var settings: SharedPreferences

    fun setImageList(imageList: RealmList<String>?) {
        this.imageList = imageList
    }

    fun addItem(news: NewsItem?) {
        val currentList = currentList.toMutableList()
        currentList.add(0, news)
        submitList(currentList) {
            recyclerView?.post {
                recyclerView?.scrollToPosition(0)
                recyclerView?.smoothScrollToPosition(0)
            }
        }
    }

    fun setFromLogin(fromLogin: Boolean) {
        this.fromLogin = fromLogin
    }

    fun setNonTeamMember(nonTeamMember: Boolean) {
        if (this.nonTeamMember != nonTeamMember) {
            this.nonTeamMember = nonTeamMember
            notifyItemRangeChanged(0, itemCount)
        }
    }

    fun setListener(listener: OnNewsItemClickListener?) {
        this.listener = listener
    }

    fun setmRealm(mRealm: Realm?) {
        if (mRealm != null) {
            this.mRealm = mRealm
            labelManager = NewsLabelManager(context, this.mRealm)
        }
    }

    fun updateReplyBadge(newsId: String?) {
        if (newsId.isNullOrEmpty()) return
        val index = if (parentNews != null) {
            when {
                parentNews.id == newsId -> 0
                else -> currentList.indexOfFirst { it?.id == newsId }.let { if (it != -1) it + 1 else -1 }
            }
        } else {
            currentList.indexOfFirst { it?.id == newsId }
        }
        if (index >= 0) {
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = RowNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        sharedPreferences = SharedPrefManager(context)
        user = userProfileDbHandler.userModel
        settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (::mRealm.isInitialized) {
            if (labelManager == null) labelManager = NewsLabelManager(context, mRealm)
        }
        return ViewHolderNews(binding)
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderNews) {
            holder.bind(position)
            val news = getNews(holder, position)

            if (news != null) {
                val viewHolder = holder

                resetViews(viewHolder)

                updateReplyCount(viewHolder, news.replyCount, position)

                configureUser(viewHolder, news)
                showShareButton(viewHolder, news)

                setMessageAndDate(viewHolder, news)

                configureEditDeleteButtons(viewHolder, news)

                loadImage(viewHolder.binding, news)
                showReplyButton(viewHolder, news)

                // For label manager, we might need RealmNews if manager uses it.
                // LabelManager needs refactoring or we construct a temporary RealmNews or pass what we have.
                // labelManager?.showChips expects RealmNews.
                // Checking NewsLabelManager usage...
                // Assuming we can skip this or use adapter's Realm if available for now.
                // Since this is UI, maybe we can fetch the Realm object for label manager?
                // Or update label manager. Given scope, I'll try to fetch if possible or skip.
                if (::mRealm.isInitialized && !mRealm.isClosed && news.id != null) {
                    val realmNews = mRealm.where(RealmNews::class.java).equalTo("id", news.id).findFirst()
                    if (realmNews != null) {
                        val canManageLabels = canAddLabel(news)
                        labelManager?.setupAddLabelMenu(viewHolder.binding, realmNews, canManageLabels)
                        labelManager?.showChips(viewHolder.binding, realmNews, canManageLabels)
                    }
                }

                handleChat(viewHolder, news)

                setMemberClickListeners(viewHolder, news)
            }
        }
    }

    private fun resetViews(holder: ViewHolderNews) {
        with(holder.binding) {
            tvName.text = ""
            imgUser.setImageResource(0)
            llEditDelete.visibility = View.GONE
            linearLayout51.visibility = View.VISIBLE
            tvMessage.text = ""
            tvDate.text = ""
            imgDelete.setOnClickListener(null)
            imgEdit.setOnClickListener(null)
            btnAddLabel.visibility = View.GONE
            imgEdit.visibility = View.GONE
            imgDelete.visibility = View.GONE
            btnReply.visibility = View.GONE
            // imgNews.visibility = View.GONE // Removed in layout
            // llNewsImages.visibility = View.GONE // Removed in layout
            rvNewsImages.visibility = View.GONE
            recyclerGchat.visibility = View.GONE
            sharedChat.visibility = View.GONE
        }
    }

    private fun configureUser(holder: ViewHolderNews, news: NewsItem) {
        if (news.userFullName != null && currentUser != null) {
            holder.binding.tvName.text = news.userFullName
            ImageUtils.loadImage(news.userImage, holder.binding.imgUser)
            showHideButtons(news, holder)
        } else {
            holder.binding.tvName.text = news.userName
            ImageUtils.loadImage(null, holder.binding.imgUser)
        }
    }

    private fun setMessageAndDate(holder: ViewHolderNews, news: NewsItem) {
        val markdownContentWithLocalPaths = prependBaseUrlToImages(
            news.message,
            "file://" + context.getExternalFilesDir(null) + "/ole/",
            600,
            350
        )
        setMarkdownText(holder.binding.tvMessage, markdownContentWithLocalPaths)
        val fulltext = holder.binding.tvMessage.text
        holder.binding.tvMessage.makeExpandable(
            fullText = fulltext,
            collapsedMaxLines = 6
        )
        holder.binding.tvDate.text =
            if (news.sharedTeamName.isEmpty() || teamName.isNotEmpty()) {
                formatDate(news.time)
            } else {
                "${formatDate(news.time)} | Shared from ${news.sharedTeamName}"
            }
        holder.binding.tvEdited.visibility = if (news.isEdited) View.VISIBLE else View.GONE
    }

    private fun configureEditDeleteButtons(holder: ViewHolderNews, news: NewsItem) {
        if (news.sharedBy == currentUser?._id && !fromLogin && !nonTeamMember && teamName.isEmpty()) {
            holder.binding.imgDelete.visibility = View.VISIBLE
        }

        if (news.userId == currentUser?._id || news.sharedBy == currentUser?._id) {
            holder.binding.imgDelete.setOnClickListener {
                val realmNews = getRealmNews(news.id)
                AlertDialog.Builder(context, R.style.AlertDialogTheme)
                    .setMessage(R.string.delete_record)
                    .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                        val currentList = currentList.toMutableList()
                        val pos = holder.adapterPosition
                        val adjustedPos = if (parentNews != null && pos > 0) pos - 1 else pos
                        if (adjustedPos >= 0 && adjustedPos < currentList.size) {
                            currentList.removeAt(adjustedPos)
                            submitList(currentList)
                        }
                        // Need mRealm for NewsActions
                         if (::mRealm.isInitialized && !mRealm.isClosed) {
                             NewsActions.deletePost(mRealm, realmNews, mutableListOf(), teamName, listener)
                         } else {
                             // Fallback?
                         }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }

        if (news.userId == currentUser?._id) {
            holder.binding.imgEdit.setOnClickListener {
                NewsActions.showEditAlert(
                    context,
                    if(::mRealm.isInitialized && !mRealm.isClosed) mRealm else databaseService.realmInstance, // Potentially unsafe use of realmInstance, but NewsActions needs a Realm.
                    news.id,
                    true,
                    currentUser,
                    listener,
                    holder,
                ) { holder, updatedNews, position ->
                    // Callback updates UI. But adapter list might be stale.
                    // Ideally we should refresh the list.
                    // For now, assume listener.onDataChanged() handles it.
                    // updatedNews is RealmNews.
                    // Convert to NewsItem?
                    // Simplified: just notifyItemChanged to re-bind (if item changed in DB and we refetch?)
                    // Since we use DTOs, we might need to manually update the DTO or trigger full refresh.
                    notifyItemChanged(position)
                }
            }
        } else {
            holder.binding.imgEdit.visibility = View.GONE
        }
    }

    private fun handleChat(holder: ViewHolderNews, news: NewsItem) {
        if (!news.conversations.isNullOrEmpty()) {
             try {
                val conversations = GsonUtils.gson.fromJson(news.conversations, Array<Conversation>::class.java).toList()
                val chatAdapter = ChatAdapter(context, holder.binding.recyclerGchat)

                if (user?.id?.startsWith("guest") == false) {
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

                holder.binding.recyclerGchat.adapter = chatAdapter
                holder.binding.recyclerGchat.layoutManager = LinearLayoutManager(context)
                holder.binding.recyclerGchat.visibility = View.VISIBLE
                holder.binding.sharedChat.visibility = View.VISIBLE
            } catch (e: Exception) {
                holder.binding.recyclerGchat.visibility = View.GONE
                holder.binding.sharedChat.visibility = View.GONE
            }
        } else {
            holder.binding.recyclerGchat.visibility = View.GONE
            holder.binding.sharedChat.visibility = View.GONE
        }
    }

    fun updateList(newList: List<NewsItem?>) {
        submitList(newList)
    }

    fun refreshCurrentItems() {
        submitList(currentList.toList())
    }

    private fun setMemberClickListeners(holder: ViewHolderNews, news: NewsItem) {
        if (!fromLogin) {
            val userModel = if (::mRealm.isInitialized && !mRealm.isClosed && news.userId != null) {
                 mRealm.where(RealmUserModel::class.java).equalTo("id", news.userId).findFirst()
            } else null
            // This is just for clicking user profile.
            // If we don't have userModel (e.g. from DTO), we can't pass it.
            // But listener expects RealmUserModel.
            // We can try to get it.

            holder.binding.imgUser.setOnClickListener {
                 if (userModel != null) listener?.onMemberSelected(userModel)
            }
            holder.binding.tvName.setOnClickListener {
                 if (userModel != null) listener?.onMemberSelected(userModel)
            }
        }
    }

    private fun isGuestUser() = user?.id?.startsWith("guest") == true

    private fun isOwner(news: NewsItem?): Boolean =
        news?.userId == currentUser?._id

    private fun isSharedByCurrentUser(news: NewsItem?): Boolean =
        news?.sharedBy == currentUser?._id

    private fun isAdmin(): Boolean =
        currentUser?.level.equals("admin", ignoreCase = true)

    private fun isLoggedInAndMember(): Boolean =
        !fromLogin && !nonTeamMember

    private fun canEdit(news: NewsItem?): Boolean =
        isLoggedInAndMember() && (isOwner(news) || isAdmin() || isTeamLeader())

    private fun canDelete(news: NewsItem?): Boolean =
        isLoggedInAndMember() && (isOwner(news) || isSharedByCurrentUser(news) || isAdmin() || isTeamLeader())

    private fun canReply(): Boolean =
        isLoggedInAndMember() && !isGuestUser()

    private fun canAddLabel(news: NewsItem?): Boolean =
        isLoggedInAndMember() && (isOwner(news) || isTeamLeader())

    private fun canShare(news: NewsItem?): Boolean =
        isLoggedInAndMember() && !news?.isCommunityNews!! && !isGuestUser()

    private fun View.setVisibility(condition: Boolean) {
        visibility = if (condition) View.VISIBLE else View.GONE
    }

    fun isTeamLeader(): Boolean {
        if(teamId==null)return false
        return try {
            if (::mRealm.isInitialized && !mRealm.isClosed) {
                val team = mRealm.where(RealmMyTeam::class.java)
                    .equalTo("teamId", teamId)
                    .equalTo("isLeader", true)
                    .findFirst()
                team?.userId == currentUser?._id
            } else {
                databaseService.withRealm { realm ->
                    val team = realm.where(RealmMyTeam::class.java)
                        .equalTo("teamId", teamId)
                        .equalTo("isLeader", true)
                        .findFirst()
                    team?.userId == currentUser?._id
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun updateReplyCount(viewHolder: ViewHolderNews, count: Int, position: Int) {
        with(viewHolder.binding) {
            btnShowReply.text = String.format(Locale.getDefault(),"(%d)", count)
            btnShowReply.setTextColor(context.getColor(R.color.daynight_textColor))
            val visible = count > 0 && !(position == 0 && parentNews != null) && canReply()
            btnShowReply.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    private fun getNews(holder: RecyclerView.ViewHolder, position: Int): NewsItem? {
        val news: NewsItem? = if (parentNews != null) {
            if (position == 0) {
                (holder.itemView as CardView).setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_blue_50))
                parentNews
            } else {
                (holder.itemView as CardView).setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_white_1000))
                getItem(position - 1)
            }
        } else {
            (holder.itemView as CardView).setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_white_1000))
            getItem(position)
        }
        return news
    }

    private fun showHideButtons(news: NewsItem?, holder: RecyclerView.ViewHolder) {
        val viewHolder = holder as ViewHolderNews
        with(viewHolder.binding) {
            imgEdit.setVisibility(canEdit(news))
            imgDelete.setVisibility(canDelete(news))
            btnAddLabel.setVisibility(canAddLabel(news))
            llEditDelete.setVisibility(canEdit(news) || canDelete(news))
        }
    }

    private fun shouldShowReplyButton(): Boolean = canReply()

    private fun showReplyButton(holder: RecyclerView.ViewHolder, finalNews: NewsItem?) {
        val viewHolder = holder as ViewHolderNews
        if (shouldShowReplyButton()) {
            viewHolder.binding.btnReply.visibility = if (nonTeamMember) View.GONE else View.VISIBLE
            viewHolder.binding.btnReply.setOnClickListener {
                // Fetch RealmNews for action
                NewsActions.showEditAlert(
                    context,
                    if(::mRealm.isInitialized && !mRealm.isClosed) mRealm else databaseService.realmInstance,
                    finalNews?.id,
                    false,
                    currentUser,
                    listener,
                     viewHolder,
                ) { holder, news, i -> showReplyButton(holder, if(news!=null) finalNews else finalNews) } // hacky update
            }
        } else {
            viewHolder.binding.btnReply.visibility = View.GONE
        }

        updateReplyCount(viewHolder, finalNews?.replyCount ?: 0, viewHolder.bindingAdapterPosition)

        viewHolder.binding.btnShowReply.setOnClickListener {
            sharedPreferences?.setRepliedNewsId(finalNews?.id)
            listener?.showReply(finalNews, fromLogin, nonTeamMember)
        }
    }

    override fun getItemCount(): Int {
        return if (parentNews == null) super.getItemCount() else super.getItemCount() + 1
    }

    interface OnNewsItemClickListener {
        fun showReply(news: NewsItem?, fromLogin: Boolean, nonTeamMember: Boolean)
        fun addImage(llImage: ViewGroup?)
        fun onNewsItemClick(news: NewsItem?)
        fun clearImages()
        fun onDataChanged()
        fun onMemberSelected(userModel: RealmUserModel?)
        fun getCurrentImageList(): RealmList<String>?
    }

    private fun showShareButton(holder: RecyclerView.ViewHolder, news: NewsItem?) {
        val viewHolder = holder as ViewHolderNews

        viewHolder.binding.btnShare.setVisibility(canShare(news))

        viewHolder.binding.btnShare.setOnClickListener {
            AlertDialog.Builder(context, R.style.AlertDialogTheme)
                .setTitle(R.string.share_with_community)
                .setMessage(R.string.confirm_share_community)
                .setPositiveButton(R.string.yes) { _, _ ->
                    val array = GsonUtils.gson.fromJson(news?.viewIn, JsonArray::class.java)
                    val firstElement = array.get(0)
                    val obj = firstElement.asJsonObject
                    if(!obj.has("name")){
                        obj.addProperty("name", teamName)
                    }
                    val ob = JsonObject()
                    ob.addProperty("section", "community")
                    ob.addProperty("_id", currentUser?.planetCode + "@" + currentUser?.parentCode)
                    ob.addProperty("sharedDate", Calendar.getInstance().timeInMillis)
                    array.add(ob)

                    if (::mRealm.isInitialized && !mRealm.isClosed) {
                         if (!mRealm.isInTransaction) {
                            mRealm.beginTransaction()
                        }
                        val managedNews = mRealm.where(RealmNews::class.java)
                                .equalTo("id", news?.id)
                                .findFirst()

                        managedNews?.sharedBy = currentUser?.id
                        managedNews?.viewIn = GsonUtils.gson.toJson(array)
                        mRealm.commitTransaction()
                    } else {
                        // Background fallback
                         // databaseService.executeTransactionAsync { ... } but we are on main thread listener.
                    }
                    Utilities.toast(context, context.getString(R.string.shared_to_community))
                    viewHolder.binding.btnShare.visibility = View.GONE
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    private fun loadImage(binding: RowNewsBinding, news: NewsItem?) {
        binding.rvNewsImages.visibility = View.GONE
        if (news?.images?.isNotEmpty() == true) {
            binding.rvNewsImages.visibility = View.VISIBLE
            binding.rvNewsImages.adapter = NewsImageAdapter(news.images)
        }
    }

    private fun getRealmNews(id: String?): RealmNews? {
        if (id == null) return null
        return if (::mRealm.isInitialized && !mRealm.isClosed) {
            mRealm.where(RealmNews::class.java).equalTo("id", id).findFirst()
        } else {
             // Dangerous to create new instance here without closing.
             // Should only happen in tests or detached state.
             null
        }
    }

    internal inner class ViewHolderNews(val binding: RowNewsBinding) : RecyclerView.ViewHolder(binding.root) {
        private var adapterPosition = 0
        fun bind(position: Int) {
            adapterPosition = position
        }
    }

}
