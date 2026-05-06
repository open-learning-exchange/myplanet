package org.ole.planet.myplanet.ui.voices

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Build
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.github.chrisbanes.photoview.PhotoView
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.isActive
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnChatItemClickListener
import org.ole.planet.myplanet.callback.OnNewsItemClickListener
import org.ole.planet.myplanet.databinding.RowNewsBinding
import org.ole.planet.myplanet.model.ChatMessage
import org.ole.planet.myplanet.model.RealmConversation
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.VoicesLabelManager
import org.ole.planet.myplanet.ui.chat.ChatAdapter
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.ImageUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.MarkdownUtils.prependBaseUrlToImages
import org.ole.planet.myplanet.utils.MarkdownUtils.setMarkdownText
import org.ole.planet.myplanet.utils.TimeUtils.formatDate
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.makeExpandable

class VoicesAdapter(
    var context: Context,
    private var currentUser: RealmUser?,
    private var parentNews: RealmNews?,
    private val teamName: String = "",
    private val teamId: String? = null,
    private val userSessionManager: UserSessionManager,
    private val isTeamLeaderFn: ((Boolean) -> Unit) -> (() -> Unit),
    private val getUserFn: (String, (RealmUser?) -> Unit) -> (() -> Unit),
    private val getReplyCountFn: (String, (Int) -> Unit) -> (() -> Unit),
    private val deletePostFn: (String, () -> Unit) -> (() -> Unit),
    private val shareNewsFn: (String, String, String, String, String, (Result<Unit>) -> Unit) -> (() -> Unit),
    private val getLibraryResourceFn: (String, (RealmMyLibrary?) -> Unit) -> (() -> Unit),
    private val launchCoroutine: (suspend () -> Unit) -> (() -> Unit),
    private val labelManager: VoicesLabelManager,
    private val voicesRepository: VoicesRepository,
    private val userRepository: org.ole.planet.myplanet.repository.UserRepository
) : RecyclerView.Adapter<RecyclerView.ViewHolder?>() {
    private val diffCallback = DiffUtils.itemCallback<RealmNews>(
        areItemsTheSame = { oldItem, newItem ->
            if (oldItem === newItem) return@itemCallback true

            try {
                val oId = oldItem.takeIf { it.isValid }?.id
                val nId = newItem.takeIf { it.isValid }?.id
                oId != null && oId == nId
            } catch (e: Exception) {
                false
            }
        },
        areContentsTheSame = { oldItem, newItem ->
            if (oldItem === newItem) return@itemCallback true

            try {
                if (!oldItem.isValid || !newItem.isValid) return@itemCallback false

                oldItem.id == newItem.id && oldItem.time == newItem.time &&
                        oldItem.isEdited == newItem.isEdited && oldItem.message == newItem.message &&
                        oldItem.userName == newItem.userName && oldItem.userId == newItem.userId &&
                        oldItem.sharedBy == newItem.sharedBy
            } catch (e: Exception) {
                false
            }
        }
    )

    private val mDiffer = AsyncListDiffer(
        object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) {
                notifyItemRangeInserted(if (parentNews != null) position + 1 else position, count)
            }
            override fun onRemoved(position: Int, count: Int) {
                notifyItemRangeRemoved(if (parentNews != null) position + 1 else position, count)
            }
            override fun onMoved(fromPosition: Int, toPosition: Int) {
                notifyItemMoved(
                    if (parentNews != null) fromPosition + 1 else fromPosition,
                    if (parentNews != null) toPosition + 1 else toPosition
                )
            }
            override fun onChanged(position: Int, count: Int, payload: Any?) {
                notifyItemRangeChanged(if (parentNews != null) position + 1 else position, count, payload)
            }
        },
        AsyncDifferConfig.Builder(diffCallback).build()
    )

    val currentList: List<RealmNews?> get() = mDiffer.currentList

    fun submitList(list: List<RealmNews?>?) {
        list?.forEach { preParseNews(it) }
        mDiffer.submitList(list as List<RealmNews>?)
    }

    fun submitList(list: List<RealmNews?>?, commitCallback: Runnable?) {
        list?.forEach { preParseNews(it) }
        mDiffer.submitList(list as List<RealmNews>?, commitCallback)
    }

    private val externalFilesDir = FileUtils.getExternalFilesDir(context)
    private var listener: OnNewsItemClickListener? = null
    @Inject
    lateinit var sharedPrefManager: SharedPrefManager
    private var imageList: List<String>? = null
    private var fromLogin = false
    private var nonTeamMember = false
    private var recyclerView: RecyclerView? = null
    private val profileDbHandler = userSessionManager
    private val userCache = mutableMapOf<String, RealmUser?>()
    private val fetchingUserIds = mutableSetOf<String>()
    private val replyCountCache = mutableMapOf<String, Int>()
    private val leadersList: List<RealmUser> by lazy {
        val raw = sharedPrefManager.getCommunityLeaders()
        userRepository.parseLeadersJson(raw)
    }
    private var _isTeamLeader: Boolean? = null

    init {
        fetchTeamLeaderStatus()
        preParseNews(parentNews)
    }

    private fun fetchTeamLeaderStatus() {
        if (teamId == null) {
            _isTeamLeader = false
            return
        }
        isTeamLeaderFn { isLeader ->
            _isTeamLeader = isLeader
        }
    }

    fun setImageList(imageList: List<String>?) {
        this.imageList = imageList
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = RowNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VoicesViewHolder(binding)
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is VoicesViewHolder) {
            holder.bind(position)
            val news = getNews(holder, position)

            if (news?.isValid == true) {
                val sharedTeamName = extractSharedTeamName(news)
                resetViews(holder)
                updateReplyCount(holder, news, position)
                val userModel = configureUser(holder, news)
                showShareButton(holder, news)
                setMessageAndDate(holder, news, sharedTeamName)
                configureEditDeleteButtons(holder, news)
                loadImage(holder.binding, news)
                showReplyButton(holder, news, position)
                val canManageLabels = canAddLabel(news)
                labelManager.setupAddLabelMenu(holder.binding, news, canManageLabels)
                news.let { labelManager.showChips(holder.binding, it, canManageLabels) }
                handleChat(holder, news)
                val currentLeader = getCurrentLeader(userModel, news)
                setMemberClickListeners(holder, userModel, currentLeader)
            }
        }
    }

    fun updateReplyBadge(newsId: String?) {
        if (newsId.isNullOrEmpty()) return
        replyCountCache.remove(newsId)
        val localParentNews = parentNews
        val index = if (localParentNews != null) {
            when {
                localParentNews.id == newsId -> 0
                else -> currentList.indexOfFirst { it?.id == newsId }.let { if (it != -1) it + 1 else -1 }
            }
        } else {
            currentList.indexOfFirst { it?.id == newsId }
        }
        if (index >= 0) {
            notifyItemChanged(index)
        }
    }

    private fun extractSharedTeamName(news: RealmNews): String {
        val ar = news.parsedViewIn

        if (ar != null && ar.size() > 1) {
            val ob = ar[0].asJsonObject
            if (ob.has("name") && !ob.get("name").isJsonNull) {
                return ob.get("name").asString
            }
        }
        return ""
    }

    private fun resetViews(holder: VoicesViewHolder) {
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
            imgNews.visibility = View.GONE
            llNewsImages.visibility = View.GONE
            llNewsImages.removeAllViews()
            recyclerGchat.visibility = View.GONE
            sharedChat.visibility = View.GONE
        }
    }

    private fun configureUser(holder: VoicesViewHolder, news: RealmNews): RealmUser? {
        val userId = news.userId
        if (userId.isNullOrEmpty()) return null

        if (userCache.containsKey(userId)) {
            val userModel = userCache[userId]
            val userFullName = userModel?.getFullNameWithMiddleName()?.trim()
            if (userModel != null && currentUser != null) {
                holder.binding.tvName.text =
                    if (userFullName.isNullOrEmpty()) news.userName else userFullName
                ImageUtils.loadImage(userModel.userImage, holder.binding.imgUser)
                showHideButtons(news, holder)
            } else {
                holder.binding.tvName.text = news.userName
                ImageUtils.loadImage(null, holder.binding.imgUser)
                showHideButtons(news, holder)
            }
            return userModel
        } else {
            holder.binding.tvName.text = news.userName
            ImageUtils.loadImage(null, holder.binding.imgUser)
            showHideButtons(news, holder)
            if (!fetchingUserIds.contains(userId)) {
                fetchingUserIds.add(userId)
                getUserFn(userId) { userModel ->
                    userCache[userId] = userModel
                    fetchingUserIds.remove(userId)
                    if (parentNews?.userId == userId) {
                        notifyItemChanged(0)
                    }
                    currentList.forEachIndexed { index, item ->
                        if (item?.userId == userId) {
                            notifyItemChanged(if (parentNews != null) index + 1 else index)
                        }
                    }
                }
            }
            return null
        }
    }

    private fun setMessageAndDate(holder: VoicesViewHolder, news: RealmNews, sharedTeamName: String) {
        val markdownContentWithLocalPaths = prependBaseUrlToImages(
            news.message,
            "file://$externalFilesDir/ole/",
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
            if (sharedTeamName.isEmpty() || teamName.isNotEmpty()) {
                formatDate(news.time)
            } else {
                "${formatDate(news.time)} | Shared from $sharedTeamName"
            }
        holder.binding.tvEdited.visibility = if (news.isEdited) View.VISIBLE else View.GONE
    }

    private fun configureEditDeleteButtons(holder: VoicesViewHolder, news: RealmNews) {
        if (news.sharedBy == currentUser?._id && !fromLogin && !nonTeamMember && teamName.isEmpty()) {
            holder.binding.imgDelete.visibility = View.VISIBLE
        }

        if (news.userId == currentUser?._id || news.sharedBy == currentUser?._id) {
            holder.binding.imgDelete.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                val snapshotList = currentList.toMutableList()
                val adjustedPos = if (parentNews != null && pos > 0) pos - 1 else pos
                val newsToDelete = if (parentNews != null && pos == 0) parentNews else snapshotList[adjustedPos]
                AlertDialog.Builder(context, R.style.AlertDialogTheme)
                    .setMessage(R.string.delete_record)
                    .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                        newsToDelete?.id?.let { id ->
                            deletePostFn(id) {
                                if (!(parentNews != null && pos == 0)) {
                                    val newList = snapshotList.toMutableList().apply { removeAt(adjustedPos) }
                                    submitList(newList)
                                }
                                parentNews?.id?.let { pid ->
                                    val current = replyCountCache[pid]
                                    replyCountCache[pid] = if (current != null) maxOf(0, current - 1) else 0
                                    notifyItemChanged(0)
                                }
                                listener?.onDataChanged()
                            }
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }

        if (news.userId == currentUser?._id) {
            holder.binding.imgEdit.setOnClickListener {
                launchCoroutine {
                    VoicesActions.showEditAlert(
                        context,
                        news.id,
                        true,
                        currentUser,
                        listener,
                        holder,
                        voicesRepository,
                        { h, updatedNews, pos ->
                            showReplyButton(h, updatedNews, pos)
                            notifyItemChanged(pos)
                        },
                        { action -> launchCoroutine(action) }
                    )
                }
            }
        } else {
            holder.binding.imgEdit.visibility = View.GONE
        }
    }

    private fun handleChat(holder: VoicesViewHolder, news: RealmNews) {
        if (news.newsId?.isNotEmpty() == true) {
            val conversations = news.parsedConversations!!
            val chatAdapter = ChatAdapter(context, holder.binding.recyclerGchat) { response, onUpdate, onComplete ->
                val cancelJob = launchCoroutine {
                    var currentIndex = 0
                    while (currentIndex < response.length) {
                        if (!kotlin.coroutines.coroutineContext.isActive) return@launchCoroutine
                        onUpdate(response.substring(0, currentIndex + 1))
                        currentIndex++
                        kotlinx.coroutines.delay(10L)
                    }
                    onComplete()
                }
                return@ChatAdapter { cancelJob() }
            }

            if (currentUser?.id?.startsWith("guest") == false) {
                chatAdapter.setOnChatItemClickListener(object : OnChatItemClickListener {
                    override fun onChatItemClick(position: Int, chatItem: ChatMessage) {
                        listener?.onNewsItemClick(news)
                    }
                })
            }

            val messages = mutableListOf<ChatMessage>()
            for (conversation in conversations) {
                val query = conversation.query
                val response = conversation.response
                if (query != null) {
                    messages.add(ChatMessage(query, ChatMessage.QUERY))
                }
                if (response != null) {
                    messages.add(ChatMessage(response, ChatMessage.RESPONSE, ChatMessage.RESPONSE_SOURCE_SHARED_VIEW_MODEL))
                }
            }
            chatAdapter.submitList(messages)

            holder.binding.recyclerGchat.adapter = chatAdapter
            holder.binding.recyclerGchat.layoutManager = LinearLayoutManager(context)
            holder.binding.recyclerGchat.visibility = View.VISIBLE
            holder.binding.sharedChat.visibility = View.VISIBLE
        } else {
            holder.binding.recyclerGchat.visibility = View.GONE
            holder.binding.sharedChat.visibility = View.GONE
        }
    }

    private fun getCurrentLeader(userModel: RealmUser?, news: RealmNews): RealmUser? {
        if (userModel == null) {
            for (leader in leadersList) {
                if (leader.name == news.userName) {
                    return leader
                }
            }
        }
        return null
    }

    fun updateParentNews(news: RealmNews?) {
        val contentChanged = parentNews?.message != news?.message ||
            parentNews?.isEdited != news?.isEdited
        parentNews = news
        preParseNews(parentNews)
        if (contentChanged) notifyItemChanged(0)
    }

    private fun parseViewIn(viewIn: String?): JsonArray? {
        if (TextUtils.isEmpty(viewIn)) return null
        return try {
            JsonUtils.gson.fromJson(viewIn, JsonArray::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseConversations(conversations: String?): List<RealmConversation>? {
        if (conversations.isNullOrEmpty()) return null
        return try {
            JsonUtils.gson.fromJson(conversations, Array<RealmConversation>::class.java).toList()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseImageUrls(imageUrls: List<String>?): List<JsonObject>? {
        if (imageUrls.isNullOrEmpty()) return null
        return try {
            imageUrls.map { JsonUtils.gson.fromJson(it, JsonObject::class.java) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun preParseNews(news: RealmNews?) {
        news?.let {
            try {
                if ((it.parsedViewIn == null || it.rawViewIn != it.viewIn) && !TextUtils.isEmpty(it.viewIn)) {
                    val parsed = parseViewIn(it.viewIn)
                    if (parsed != null) {
                        it.parsedViewIn = parsed
                        it.rawViewIn = it.viewIn
                    }
                }
                if ((it.parsedConversations == null || it.rawConversations != it.conversations) && !it.conversations.isNullOrEmpty()) {
                    val parsed = parseConversations(it.conversations)
                    if (parsed != null) {
                        it.parsedConversations = parsed
                        it.rawConversations = it.conversations
                    }
                }

                val currentImageUrls = it.imageUrls?.toList()
                if ((it.parsedImageUrls == null || it.rawImageUrls != currentImageUrls) && !currentImageUrls.isNullOrEmpty()) {
                    val parsed = parseImageUrls(currentImageUrls)
                    if (parsed != null) {
                        it.parsedImageUrls = parsed
                        it.rawImageUrls = currentImageUrls
                    }
                }
            } catch (e: IllegalStateException) {
                // If Realm manages the object and we are on a different thread, mutating @Ignore fields might throw.
                e.printStackTrace()
            }
        }
    }

    private fun setMemberClickListeners(holder: VoicesViewHolder, userModel: RealmUser?, currentLeader: RealmUser?) {
        if (!fromLogin) {
            holder.binding.imgUser.setOnClickListener {
                val model = userModel ?: currentLeader
                listener?.onMemberSelected(model)
            }
            holder.binding.tvName.setOnClickListener {
                val model = userModel ?: currentLeader
                listener?.onMemberSelected(model)
            }
        }
    }

    private fun isGuestUser() = currentUser?.id?.startsWith("guest") == true

    private fun isOwner(news: RealmNews?): Boolean =
        news?.userId == currentUser?._id

    private fun isSharedByCurrentUser(news: RealmNews?): Boolean =
        news?.sharedBy == currentUser?._id

    private fun isAdmin(): Boolean =
        currentUser?.level.equals("admin", ignoreCase = true)

    private fun isLoggedInAndMember(): Boolean =
        !fromLogin && !nonTeamMember

    private fun canEdit(news: RealmNews?): Boolean =
        isLoggedInAndMember() && (isOwner(news) || isAdmin() || isTeamLeader())

    private fun canDelete(news: RealmNews?): Boolean =
        isLoggedInAndMember() && (isOwner(news) || isSharedByCurrentUser(news) || isAdmin() || isTeamLeader())

    private fun canReply(): Boolean =
        isLoggedInAndMember() && !isGuestUser()

    private fun canAddLabel(news: RealmNews?): Boolean =
        isLoggedInAndMember() && (isOwner(news) || isTeamLeader())

    private fun canShare(news: RealmNews?): Boolean =
        isLoggedInAndMember() && news?.isCommunityNews != true && !isGuestUser()

    private fun View.setVisibility(condition: Boolean) {
        visibility = if (condition) View.VISIBLE else View.GONE
    }

    fun isTeamLeader(): Boolean {
        return _isTeamLeader ?: false
    }

    fun invalidateTeamLeaderCache() {
        _isTeamLeader = null
        fetchTeamLeaderStatus()
    }

    private fun applyReplyCount(binding: RowNewsBinding, replyCount: Int, position: Int) {
        binding.btnShowReply.text = String.format(Locale.getDefault(), "(%d)", replyCount)
        binding.btnShowReply.setTextColor(context.getColor(R.color.daynight_textColor))
        val visible = replyCount > 0 && !(position == 0 && parentNews != null) && canReply()
        binding.btnShowReply.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun updateReplyCount(viewHolder: VoicesViewHolder, news: RealmNews?, position: Int) {
        val newsId = news?.id ?: return
        val cached = replyCountCache[newsId]
        if (cached != null) {
            applyReplyCount(viewHolder.binding, cached, position)
            return
        }
        viewHolder.cancelJob?.invoke()
        viewHolder.cancelJob = getReplyCountFn(newsId) { replyCount ->
            try {
                replyCountCache[newsId] = replyCount
                applyReplyCount(viewHolder.binding, replyCount, position)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getNews(holder: RecyclerView.ViewHolder, position: Int): RealmNews? {
        val news: RealmNews? = if (parentNews != null) {
            if (position == 0) {
                (holder.itemView as CardView).setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_blue_50))
                parentNews
            } else {
                (holder.itemView as CardView).setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_white_1000))
                currentList[position - 1]
            }
        } else {
            (holder.itemView as CardView).setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_white_1000))
            currentList[position]
        }
        return news
    }

    private fun showHideButtons(news: RealmNews?, holder: RecyclerView.ViewHolder) {
        val viewHolder = holder as VoicesViewHolder
        with(viewHolder.binding) {
            imgEdit.setVisibility(canEdit(news))
            imgDelete.setVisibility(canDelete(news))
            btnAddLabel.setVisibility(canAddLabel(news))
            llEditDelete.setVisibility(canEdit(news) || canDelete(news))
        }
    }

    private fun shouldShowReplyButton(): Boolean = canReply()

    private fun showReplyButton(holder: RecyclerView.ViewHolder, finalNews: RealmNews?, position: Int) {
        val viewHolder = holder as VoicesViewHolder
        if (shouldShowReplyButton()) {
            viewHolder.binding.btnReply.visibility = if (nonTeamMember) View.GONE else View.VISIBLE
            viewHolder.binding.btnReply.setOnClickListener {
                launchCoroutine {
                    VoicesActions.showEditAlert(
                        context,
                        finalNews?.id,
                        false,
                        currentUser,
                        listener,
                        viewHolder,
                        voicesRepository,
                        { _, _, _ -> },
                        { action -> launchCoroutine(action) }
                    )
                }
            }
        } else {
            viewHolder.binding.btnReply.visibility = View.GONE
        }

        updateReplyCount(viewHolder, finalNews, position)

        viewHolder.binding.btnShowReply.setOnClickListener {
            sharedPrefManager.setRepliedNewsId(finalNews?.id)
            listener?.showReply(finalNews, fromLogin, nonTeamMember)
        }
    }

    override fun getItemCount(): Int {
        return if (parentNews == null) currentList.size else currentList.size + 1
    }


    private fun showShareButton(holder: RecyclerView.ViewHolder, news: RealmNews?) {
        val viewHolder = holder as VoicesViewHolder

        viewHolder.binding.btnShare.setVisibility(canShare(news))

        viewHolder.binding.btnShare.setOnClickListener {
            AlertDialog.Builder(context, R.style.AlertDialogTheme)
                .setTitle(R.string.share_with_community)
                .setMessage(R.string.confirm_share_community)
                .setPositiveButton(R.string.yes) { _, _ ->
                     val newsId = news?.id
                     val userId = currentUser?.id
                     val planetCode = currentUser?.planetCode ?: ""
                     val parentCode = currentUser?.parentCode ?: ""

                     if (newsId != null && userId != null) {
                         shareNewsFn(newsId, userId, planetCode, parentCode, teamName) { result ->
                             if (result.isSuccess) {
                                 Utilities.toast(context, context.getString(R.string.shared_to_community))
                                 viewHolder.binding.btnShare.visibility = View.GONE
                             } else {
                                 Utilities.toast(context, "Failed to share news")
                             }
                         }
                     }
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

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is VoicesViewHolder) {
            holder.cancelJob?.invoke()
        }
    }

    private fun getParsedImageUrls(news: RealmNews?): List<JsonObject>? {
        return news?.parsedImageUrls
    }

    private fun loadImage(binding: RowNewsBinding, news: RealmNews?) {
        binding.imgNews.visibility = View.GONE
        binding.llNewsImages.visibility = View.GONE
        binding.llNewsImages.removeAllViews()

        val parsedImageUrls = getParsedImageUrls(news)

        if (!parsedImageUrls.isNullOrEmpty()) {
            try {
                if (parsedImageUrls.size == 1) {
                    val path = JsonUtils.getString("imageUrl", parsedImageUrls[0])
                    loadSingleImage(binding, path)
                } else {
                    binding.llNewsImages.visibility = View.VISIBLE
                    for (imgObject in parsedImageUrls) {
                        val path = JsonUtils.getString("imageUrl", imgObject)
                        addImageToContainer(binding, path)
                    }
                }
                return
            } catch (_: Exception) {
            }
        }

        news?.imagesArray?.let { imagesArray ->
            if (imagesArray.size() > 0) {
                if (imagesArray.size() == 1) {
                    val ob = imagesArray[0]?.asJsonObject
                    val resourceId = JsonUtils.getString("resourceId", ob)
                    loadLibraryImage(binding, resourceId)
                } else {
                    binding.llNewsImages.visibility = View.VISIBLE
                    for (i in 0 until imagesArray.size()) {
                        val ob = imagesArray[i]?.asJsonObject
                        val resourceId = JsonUtils.getString("resourceId", ob)
                        addLibraryImageToContainer(binding, resourceId)
                    }
                }
            }
        }
    }

    private fun loadSingleImage(binding: RowNewsBinding, path: String?) {
        if (path == null) return
        val request = Glide.with(binding.imgNews.context)
        val file = File(path)
        val target = if (path.lowercase(Locale.getDefault()).endsWith(".gif")) {
            request.asGif().load(file).error(request.asGif().load(path))
        } else {
            request.load(file).error(request.load(path))
        }
        target.diskCacheStrategy(DiskCacheStrategy.ALL).fitCenter().placeholder(R.drawable.ic_loading)
            .error(R.drawable.ic_loading)
            .into(binding.imgNews)
        binding.imgNews.visibility = View.VISIBLE
        binding.imgNews.setOnClickListener {
            showZoomableImage(it.context, path)
        }
    }

    private fun addImageToContainer(binding: RowNewsBinding, path: String?) {
        if (path == null) return
        val imageView = ImageView(context)
        val size = (100 * context.resources.displayMetrics.density).toInt()
        val margin = (4 * context.resources.displayMetrics.density).toInt()
        val params = ViewGroup.MarginLayoutParams(size, size)
        params.setMargins(margin, margin, margin, margin)
        imageView.layoutParams = params
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP

        val request = Glide.with(context)
        val file = File(path)
        val target = if (path.lowercase(Locale.getDefault()).endsWith(".gif")) {
            request.asGif().load(file).error(request.asGif().load(path))
        } else {
            request.load(file).error(request.load(path))
        }
        target.diskCacheStrategy(DiskCacheStrategy.ALL).fitCenter().placeholder(R.drawable.ic_loading)
            .error(R.drawable.ic_loading)
            .into(imageView)

        imageView.setOnClickListener {
            showZoomableImage(context, path)
        }

        binding.llNewsImages.addView(imageView)
    }

    private fun loadLibraryImage(binding: RowNewsBinding, resourceId: String?) {
        if (resourceId == null) return
        getLibraryResourceFn(resourceId) { library ->
            val basePath = externalFilesDir
            if (library != null && basePath != null) {
                val imageFile = File(basePath, "ole/${library.id}/${library.resourceLocalAddress}")
                val request = Glide.with(binding.imgNews.context)
                val isGif = library.resourceLocalAddress?.lowercase(Locale.getDefault())?.endsWith(".gif") == true
                val target = if (isGif) {
                    request.asGif().load(imageFile)
                } else {
                    request.load(imageFile)
                }
                target.diskCacheStrategy(DiskCacheStrategy.ALL).fitCenter().placeholder(R.drawable.ic_loading)
                    .error(R.drawable.ic_loading)
                    .into(binding.imgNews)
                binding.imgNews.visibility = View.VISIBLE
                binding.imgNews.setOnClickListener {
                    showZoomableImage(it.context, imageFile.toString())
                }
            }
        }
    }

    private fun addLibraryImageToContainer(binding: RowNewsBinding, resourceId: String?) {
        if (resourceId == null) return
        getLibraryResourceFn(resourceId) { library ->
            val basePath = externalFilesDir
            if (library != null && basePath != null) {
                val imageFile = File(basePath, "ole/${library.id}/${library.resourceLocalAddress}")
                val imageView = ImageView(context)
                val size = (100 * context.resources.displayMetrics.density).toInt()
                val margin = (4 * context.resources.displayMetrics.density).toInt()
                val params = ViewGroup.MarginLayoutParams(size, size)
                params.setMargins(margin, margin, margin, margin)
                imageView.layoutParams = params
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP

                val request = Glide.with(context)
                val isGif = library.resourceLocalAddress?.lowercase(Locale.getDefault())?.endsWith(".gif") == true
                val target = if (isGif) {
                    request.asGif().load(imageFile)
                } else {
                    request.load(imageFile)
                }
                target.diskCacheStrategy(DiskCacheStrategy.ALL).fitCenter().placeholder(R.drawable.ic_loading)
                    .error(R.drawable.ic_loading)
                    .into(imageView)

                imageView.setOnClickListener {
                    showZoomableImage(context, imageFile.toString())
                }

                binding.llNewsImages.addView(imageView)
            }
        }
    }

    private fun showZoomableImage(context: Context, imageUrl: String) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_zoomable_image, null)
        val photoView = view.findViewById<PhotoView>(R.id.photoView)
        val closeButton = view.findViewById<ImageView>(R.id.closeButton)

        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(Color.BLACK.toDrawable())

        val request = Glide.with(photoView.context)
        val file = File(imageUrl)
        val target = if (imageUrl.lowercase(Locale.getDefault()).endsWith(".gif")) {
            request.asGif().load(file).error(request.asGif().load(imageUrl))
        } else {
            request.load(file).error(request.load(imageUrl))
        }
        target.diskCacheStrategy(DiskCacheStrategy.ALL).fitCenter().error(R.drawable.ic_loading).into(photoView)

        closeButton.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    internal inner class VoicesViewHolder(val binding: RowNewsBinding) : RecyclerView.ViewHolder(binding.root) {
        var cancelJob: (() -> Unit)? = null
        private var adapterPosition = 0
        fun bind(position: Int) {
            adapterPosition = position
        }
    }
}
