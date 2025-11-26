package org.ole.planet.myplanet.ui.news

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.github.chrisbanes.photoview.PhotoView
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Case
import io.realm.Realm
import io.realm.RealmList
import io.realm.Sort
import java.io.File
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNewsBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.chat.ChatAdapter
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.ImageUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Markdown.prependBaseUrlToImages
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.makeExpandable

class AdapterNews(var context: Context, private var currentUser: RealmUserModel?, private val parentNews: NewsItem?, private val teamName: String = "", private val teamId: String? = null, private val userProfileDbHandler: UserProfileDbHandler, private val databaseService: DatabaseService) : ListAdapter<NewsItem?, RecyclerView.ViewHolder?>(
    DiffUtils.itemCallback(
        areItemsTheSame = { oldItem, newItem ->
            if (oldItem === newItem) return@itemCallback true
            if (oldItem == null || newItem == null) return@itemCallback oldItem == newItem
            val oldNews = oldItem.news
            val newNews = newItem.news
            if (oldNews === newNews) return@itemCallback true
            if (oldNews == null || newNews == null) return@itemCallback oldNews == newNews

            try {
                val oId = oldNews.takeIf { it.isValid }?.id
                val nId = newNews.takeIf { it.isValid }?.id
                oId != null && oId == nId
            } catch (e: Exception) {
                false
            }
        },
        areContentsTheSame = { oldItem, newItem ->
            if (oldItem === newItem) return@itemCallback true
            if (oldItem == null || newItem == null) return@itemCallback oldItem == newItem
            val oldNews = oldItem.news
            val newNews = newItem.news
            if (oldNews === newNews) return@itemCallback true
            if (oldNews == null || newNews == null) return@itemCallback oldNews == newNews

            try {
                if (!oldNews.isValid || !newNews.isValid) return@itemCallback false

                oldNews.id == newNews.id &&
                        oldNews.time == newNews.time &&
                        oldNews.isEdited == newNews.isEdited &&
                        oldNews.message == newNews.message &&
                        oldNews.userName == newNews.userName &&
                        oldNews.userId == newNews.userId &&
                        oldNews.sharedBy == newNews.sharedBy &&
                        oldItem.replyCount == newItem.replyCount &&
                        oldItem.isTeamLeader == newItem.isTeamLeader
            } catch (e: Exception) {
                false
            }
        }
    )
) {
    private var listener: OnNewsItemClickListener? = null
    @Inject
    lateinit var sharedPrefManager: SharedPrefManager
    private var imageList: RealmList<String>? = null
    lateinit var mRealm: Realm
    private var fromLogin = false
    private var nonTeamMember = false
    private var recyclerView: RecyclerView? = null
    var user: RealmUserModel? = null
    private var labelManager: NewsLabelManager? = null
    private val profileDbHandler = userProfileDbHandler
    lateinit var settings: SharedPreferences
    private val userCache = mutableMapOf<String, RealmUserModel?>()
    private val leadersList: List<RealmUserModel> by lazy {
        val raw = settings.getString("communityLeaders", "") ?: ""
        RealmUserModel.parseLeadersJson(raw)
    }

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = RowNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        user = userProfileDbHandler.userModel
        settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (::mRealm.isInitialized) {
            if (labelManager == null) labelManager = NewsLabelManager(context, mRealm)
        }
        return ViewHolderNews(binding)
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderNews) {
            holder.bind(position)
            val newsItem = getNewsItem(holder, position)
            val news = newsItem?.news

            if (news?.isValid == true) {
                val viewHolder = holder
                val sharedTeamName = extractSharedTeamName(news)

                resetViews(viewHolder)

                updateReplyCount(viewHolder, newsItem, position)

                val userModel = configureUser(viewHolder, news)
                showHideButtons(viewHolder, news, newsItem.isTeamLeader)
                showShareButton(viewHolder, news)

                setMessageAndDate(viewHolder, news, sharedTeamName)

                configureEditDeleteButtons(viewHolder, news)

                loadImage(viewHolder.binding, news)
                showReplyButton(viewHolder, newsItem, position)
                val canManageLabels = canAddLabel(news, newsItem.isTeamLeader)
                labelManager?.setupAddLabelMenu(viewHolder.binding, news, canManageLabels)
                news.let { labelManager?.showChips(viewHolder.binding, it, canManageLabels) }

                handleChat(viewHolder, news)

                val currentLeader = getCurrentLeader(userModel, news)
                setMemberClickListeners(viewHolder, userModel, currentLeader)
            }
        }
    }

    fun updateReplyBadge(newsId: String?) {
        if (newsId.isNullOrEmpty()) return
        val index = if (parentNews != null) {
            when {
                parentNews.news?.id == newsId -> 0
                else -> currentList.indexOfFirst { it?.news?.id == newsId }.let { if (it != -1) it + 1 else -1 }
            }
        } else {
            currentList.indexOfFirst { it?.news?.id == newsId }
        }
        if (index >= 0) {
            notifyItemChanged(index)
        }
    }

    private fun extractSharedTeamName(news: RealmNews): String {
        if (!TextUtils.isEmpty(news.viewIn)) {
            val ar = GsonUtils.gson.fromJson(news.viewIn, JsonArray::class.java)
            if (ar.size() > 1) {
                val ob = ar[0].asJsonObject
                if (ob.has("name") && !ob.get("name").isJsonNull) {
                    return ob.get("name").asString
                }
            }
        }
        return ""
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
            imgNews.visibility = View.GONE
            llNewsImages.visibility = View.GONE
            llNewsImages.removeAllViews()
            recyclerGchat.visibility = View.GONE
            sharedChat.visibility = View.GONE
        }
    }

    private fun configureUser(holder: ViewHolderNews, news: RealmNews): RealmUserModel? {
        val userId = news.userId
        val userModel = when {
            userId.isNullOrEmpty() -> null
            userCache.containsKey(userId) -> userCache[userId]
            ::mRealm.isInitialized -> {
                val managedUser = mRealm.where(RealmUserModel::class.java)
                    .equalTo("id", userId)
                    .findFirst()
                val detachedUser = managedUser?.let {
                    try {
                        mRealm.copyFromRealm(it)
                    } catch (e: Exception) {
                        null
                    }
                }
                if (detachedUser != null) {
                    userCache[userId] = detachedUser
                } else if (managedUser == null) {
                    userCache[userId] = null
                }
                detachedUser ?: managedUser
            }
            else -> null
        }
        val userFullName = userModel?.getFullNameWithMiddleName()?.trim()
        if (userModel != null && currentUser != null) {
            holder.binding.tvName.text =
                if (userFullName.isNullOrEmpty()) news.userName else userFullName
            ImageUtils.loadImage(userModel.userImage, holder.binding.imgUser)
            showHideButtons(news, holder)
        } else {
            holder.binding.tvName.text = news.userName
            ImageUtils.loadImage(null, holder.binding.imgUser)
        }
        return userModel
    }

    private fun setMessageAndDate(holder: ViewHolderNews, news: RealmNews, sharedTeamName: String) {
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
            if (sharedTeamName.isEmpty() || teamName.isNotEmpty()) {
                formatDate(news.time)
            } else {
                "${formatDate(news.time)} | Shared from $sharedTeamName"
            }
        holder.binding.tvEdited.visibility = if (news.isEdited) View.VISIBLE else View.GONE
    }

    private fun configureEditDeleteButtons(holder: ViewHolderNews, news: RealmNews) {
        if (news.sharedBy == currentUser?._id && !fromLogin && !nonTeamMember && teamName.isEmpty()) {
            holder.binding.imgDelete.visibility = View.VISIBLE
        }

        if (news.userId == currentUser?._id || news.sharedBy == currentUser?._id) {
            holder.binding.imgDelete.setOnClickListener {
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
                        NewsActions.deletePost(mRealm, news, currentList.mapNotNull { it?.news }.toMutableList(), teamName, listener)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }

        if (news.userId == currentUser?._id) {
            holder.binding.imgEdit.setOnClickListener {
                NewsActions.showEditAlert(
                    context,
                    mRealm,
                    news.id,
                    true,
                    currentUser,
                    listener,
                    holder,
                ) { holder, updatedNews, position ->
                    showReplyButton(holder, updatedNews, position)
                    notifyItemChanged(position)
                }
            }
        } else {
            holder.binding.imgEdit.visibility = View.GONE
        }
    }

    private fun handleChat(holder: ViewHolderNews, news: RealmNews) {
        if (news.newsId?.isNotEmpty() == true) {
            val conversations = GsonUtils.gson.fromJson(news.conversations, Array<Conversation>::class.java).toList()
            val chatAdapter = ChatAdapter(context, holder.binding.recyclerGchat, holder.itemView.findViewTreeLifecycleOwner()?.lifecycleScope)

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
        } else {
            holder.binding.recyclerGchat.visibility = View.GONE
            holder.binding.sharedChat.visibility = View.GONE
        }
    }

    private fun getCurrentLeader(userModel: RealmUserModel?, news: RealmNews): RealmUserModel? {
        if (userModel == null) {
            for (leader in leadersList) {
                if (leader.name == news.userName) {
                    return leader
                }
            }
        }
        return null
    }

    fun updateList(newList: List<NewsItem?>) {
        submitList(newList)
    }

    fun refreshCurrentItems() {
        submitList(currentList.toList())
    }

    private fun setMemberClickListeners(holder: ViewHolderNews, userModel: RealmUserModel?, currentLeader: RealmUserModel?) {
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

    private fun isGuestUser() = user?.id?.startsWith("guest") == true

    private fun isOwner(news: RealmNews?): Boolean =
        news?.userId == currentUser?._id

    private fun isSharedByCurrentUser(news: RealmNews?): Boolean =
        news?.sharedBy == currentUser?._id

    private fun isAdmin(): Boolean =
        currentUser?.level.equals("admin", ignoreCase = true)

    private fun isLoggedInAndMember(): Boolean =
        !fromLogin && !nonTeamMember

    private fun canEdit(news: RealmNews?, isTeamLeader: Boolean): Boolean =
        isLoggedInAndMember() && (isOwner(news) || isAdmin() || isTeamLeader)

    private fun canDelete(news: RealmNews?, isTeamLeader: Boolean): Boolean =
        isLoggedInAndMember() && (isOwner(news) || isSharedByCurrentUser(news) || isAdmin() || isTeamLeader)

    private fun canReply(): Boolean =
        isLoggedInAndMember() && !isGuestUser()

    private fun canAddLabel(news: RealmNews?, isTeamLeader: Boolean): Boolean =
        isLoggedInAndMember() && (isOwner(news) || isTeamLeader)

    private fun canShare(news: RealmNews?): Boolean =
        isLoggedInAndMember() && !news?.isCommunityNews!! && !isGuestUser()

    private fun View.setVisibility(condition: Boolean) {
        visibility = if (condition) View.VISIBLE else View.GONE
    }

    private fun updateReplyCount(viewHolder: ViewHolderNews, newsItem: NewsItem?, position: Int) {
        with(viewHolder.binding) {
            btnShowReply.text = String.format(Locale.getDefault(), "(%d)", newsItem?.replyCount)
            btnShowReply.setTextColor(context.getColor(R.color.daynight_textColor))
            val visible = (newsItem?.replyCount ?: 0) > 0 && !(position == 0 && parentNews != null) && canReply()
            btnShowReply.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    private fun getNewsItem(holder: RecyclerView.ViewHolder, position: Int): NewsItem? {
        val newsItem: NewsItem? = if (parentNews != null) {
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
        return newsItem
    }

    private fun showHideButtons(news: RealmNews?, holder: RecyclerView.ViewHolder, isTeamLeader: Boolean) {
        val viewHolder = holder as ViewHolderNews
        with(viewHolder.binding) {
            imgEdit.setVisibility(canEdit(news, isTeamLeader))
            imgDelete.setVisibility(canDelete(news, isTeamLeader))
            btnAddLabel.setVisibility(canAddLabel(news, isTeamLeader))
            llEditDelete.setVisibility(canEdit(news, isTeamLeader) || canDelete(news, isTeamLeader))
        }
    }

    private fun shouldShowReplyButton(): Boolean = canReply()

    private fun showReplyButton(holder: RecyclerView.ViewHolder, newsItem: NewsItem?, position: Int) {
        val viewHolder = holder as ViewHolderNews
        val news = newsItem?.news
        if (shouldShowReplyButton()) {
            viewHolder.binding.btnReply.visibility = if (nonTeamMember) View.GONE else View.VISIBLE
            viewHolder.binding.btnReply.setOnClickListener {
                NewsActions.showEditAlert(
                    context,
                    mRealm,
                    news?.id,
                    false,
                    currentUser,
                    listener,
                    viewHolder,
                ) { holder, news, i -> showReplyButton(holder, newsItem, i) }
            }
        } else {
            viewHolder.binding.btnReply.visibility = View.GONE
        }

        updateReplyCount(viewHolder, newsItem, position)

        viewHolder.binding.btnShowReply.setOnClickListener {
            sharedPrefManager.setRepliedNewsId(news?.id)
            listener?.showReply(news, fromLogin, nonTeamMember)
        }
    }

    override fun getItemCount(): Int {
        return if (parentNews == null) super.getItemCount() else super.getItemCount() + 1
    }

    interface OnNewsItemClickListener {
        fun showReply(news: RealmNews?, fromLogin: Boolean, nonTeamMember: Boolean)
        fun addImage(llImage: ViewGroup?)
        fun onNewsItemClick(news: RealmNews?)
        fun clearImages()
        fun onDataChanged()
        fun onMemberSelected(userModel: RealmUserModel?)
        fun getCurrentImageList(): RealmList<String>?
    }

    private fun showShareButton(holder: RecyclerView.ViewHolder, news: RealmNews?) {
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
                    if (!mRealm.isInTransaction) {
                        mRealm.beginTransaction()
                    }

                    val managedNews = news?.let { newsItem ->
                        if (newsItem.isManaged) {
                            newsItem
                        } else {
                            mRealm.where(RealmNews::class.java)
                                .equalTo("id", newsItem.id)
                                .findFirst()
                        }
                    }
                    
                    managedNews?.sharedBy = currentUser?.id
                    managedNews?.viewIn = GsonUtils.gson.toJson(array)
                    mRealm.commitTransaction()
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

    private fun loadImage(binding: RowNewsBinding, news: RealmNews?) {
        binding.imgNews.visibility = View.GONE
        binding.llNewsImages.visibility = View.GONE
        binding.llNewsImages.removeAllViews()

        val imageUrls = news?.imageUrls
        if (!imageUrls.isNullOrEmpty()) {
            try {
                if (imageUrls.size == 1) {
                    val imgObject = GsonUtils.gson.fromJson(imageUrls[0], JsonObject::class.java)
                    val path = JsonUtils.getString("imageUrl", imgObject)
                    loadSingleImage(binding, path)
                } else {
                    binding.llNewsImages.visibility = View.VISIBLE
                    for (imageUrl in imageUrls) {
                        val imgObject = GsonUtils.gson.fromJson(imageUrl, JsonObject::class.java)
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
        val target = if (path.lowercase(Locale.getDefault()).endsWith(".gif")) {
            request.asGif().load(if (File(path).exists()) File(path) else path)
        } else {
            request.load(if (File(path).exists()) File(path) else path)
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
        val target = if (path.lowercase(Locale.getDefault()).endsWith(".gif")) {
            request.asGif().load(if (File(path).exists()) File(path) else path)
        } else {
            request.load(if (File(path).exists()) File(path) else path)
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
        val library = mRealm.where(RealmMyLibrary::class.java)
            .equalTo("_id", resourceId)
            .findFirst()

        val basePath = context.getExternalFilesDir(null)
        if (library != null && basePath != null) {
            val imageFile = File(basePath, "ole/${library.id}/${library.resourceLocalAddress}")
            if (imageFile.exists()) {
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
        val library = mRealm.where(RealmMyLibrary::class.java)
            .equalTo("_id", resourceId)
            .findFirst()

        val basePath = context.getExternalFilesDir(null)
        if (library != null && basePath != null) {
            val imageFile = File(basePath, "ole/${library.id}/${library.resourceLocalAddress}")
            if (imageFile.exists()) {
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
        val target = if (imageUrl.lowercase(Locale.getDefault()).endsWith(".gif")) {
            val file = File(imageUrl)
            if (file.exists()) request.asGif().load(file) else request.asGif().load(imageUrl)
        } else {
            val file = File(imageUrl)
            if (file.exists()) request.load(file) else request.load(imageUrl)
        }
        target.diskCacheStrategy(DiskCacheStrategy.ALL).fitCenter().error(R.drawable.ic_loading).into(photoView)

        closeButton.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    internal inner class ViewHolderNews(val binding: RowNewsBinding) : RecyclerView.ViewHolder(binding.root) {
        private var adapterPosition = 0
        fun bind(position: Int) {
            adapterPosition = position
        }
    }

}
