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
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Case
import io.realm.Realm
import io.realm.RealmList
import io.realm.Sort
import java.io.File
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import okhttp3.RequestBody
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNewsBinding
import org.ole.planet.myplanet.di.NewsRepository
import org.ole.planet.myplanet.di.NewsRepositoryImpl
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.chat.ChatAdapter
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Markdown.prependBaseUrlToImages
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.makeExpandable

class AdapterNews(var context: Context, private val list: MutableList<RealmNews?>, private var currentUser: RealmUserModel?, private val parentNews: RealmNews?, private val teamName: String = "", private val teamId: String? = null, private val userProfileDbHandler: UserProfileDbHandler) : RecyclerView.Adapter<RecyclerView.ViewHolder?>() {
    private lateinit var rowNewsBinding: RowNewsBinding
    private var listener: OnNewsItemClickListener? = null
    private var imageList: RealmList<String>? = null
    lateinit var mRealm: Realm
    lateinit var newsRepository: NewsRepository
    private var fromLogin = false
    private var nonTeamMember = false
    private var sharedPreferences: SharedPrefManager? = null
    private var recyclerView: RecyclerView? = null
    var user: RealmUserModel? = null
    private var labelManager: NewsLabelManager? = null
    private val gson = Gson()
    private val profileDbHandler = userProfileDbHandler
    lateinit var settings: SharedPreferences
    private val leadersList: List<RealmUserModel> by lazy {
        val raw = settings.getString("communityLeaders", "") ?: ""
        RealmUserModel.parseLeadersJson(raw)
    }

    fun setImageList(imageList: RealmList<String>?) {
        this.imageList = imageList
    }

    fun addItem(news: RealmNews?) {
        list.add(news)
        notifyDataSetChanged()
    }

    fun setFromLogin(fromLogin: Boolean) {
        this.fromLogin = fromLogin
    }

    fun setNonTeamMember(nonTeamMember: Boolean) {
        this.nonTeamMember = nonTeamMember
    }

    fun setListener(listener: OnNewsItemClickListener?) {
        this.listener = listener
    }

    fun setNewsRepository(repository: NewsRepository?) {
        if (repository != null) {
            this.newsRepository = repository
            this.mRealm = repository.getRealm()
            labelManager = NewsLabelManager(context, this.mRealm, currentUser)
        }
    }

    fun setmRealm(mRealm: Realm?) {
        if (mRealm != null) {
            this.mRealm = mRealm
            if (!::newsRepository.isInitialized) {
                newsRepository = NewsRepositoryImpl(mRealm, DummyApi())
            }
            labelManager = NewsLabelManager(context, this.mRealm, currentUser)
        }
    }

    private class DummyApi : ApiInterface {
        override fun downloadFile(header: String?, fileUrl: String?) = throw UnsupportedOperationException()
        override fun getDocuments(header: String?, url: String?) = throw UnsupportedOperationException()
        override fun getJsonObject(header: String?, url: String?) = throw UnsupportedOperationException()
        override fun findDocs(header: String?, c: String?, url: String?, s: JsonObject?) = throw UnsupportedOperationException()
        override fun postDoc(header: String?, c: String?, url: String?, s: JsonObject?) = throw UnsupportedOperationException()
        override fun uploadResource(headerMap: Map<String, String>, url: String?, body: RequestBody?) = throw UnsupportedOperationException()
        override fun putDoc(header: String?, c: String?, url: String?, s: JsonObject?) = throw UnsupportedOperationException()
        override suspend fun checkVersion(serverUrl: String?) = throw UnsupportedOperationException()
        override suspend fun getApkVersion(url: String?) = throw UnsupportedOperationException()
        override fun healthAccess(url: String?) = throw UnsupportedOperationException()
        override fun getChecksum(url: String?) = throw UnsupportedOperationException()
        override fun isPlanetAvailable(serverUrl: String?) = throw UnsupportedOperationException()
        override fun chatGpt(url: String?, requestBody: RequestBody?) = throw UnsupportedOperationException()
        override fun checkAiProviders(url: String?) = throw UnsupportedOperationException()
        override fun getConfiguration(url: String?) = throw UnsupportedOperationException()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        rowNewsBinding = RowNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        sharedPreferences = SharedPrefManager(context)
        user = userProfileDbHandler.userModel
        settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (::mRealm.isInitialized) {
            if (labelManager == null) labelManager = NewsLabelManager(context, mRealm, currentUser)
        }
        return ViewHolderNews(rowNewsBinding)
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderNews) {
            holder.bind(position)
            val news = getNews(holder, position)

            if (news?.isValid == true) {
                val viewHolder = holder
                val sharedTeamName = extractSharedTeamName(news)

                resetViews(viewHolder)

                updateReplyCount(viewHolder = viewHolder, getReplies(news), position)

                val userModel = configureUser(viewHolder, news)
                showShareButton(viewHolder, news)

                setMessageAndDate(viewHolder, news, sharedTeamName)

                configureEditDeleteButtons(viewHolder, news)

                loadImage(viewHolder.rowNewsBinding, news)
                showReplyButton(viewHolder, news, position)
                labelManager?.setupAddLabelMenu(viewHolder.rowNewsBinding, news)
                news.let { labelManager?.showChips(viewHolder.rowNewsBinding, it) }

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
                parentNews.id == newsId -> 0
                else -> list.indexOfFirst { it?.id == newsId }.let { if (it != -1) it + 1 else -1 }
            }
        } else {
            list.indexOfFirst { it?.id == newsId }
        }
        if (index >= 0) {
            notifyItemChanged(index)
        }
    }

    private fun extractSharedTeamName(news: RealmNews): String {
        if (!TextUtils.isEmpty(news.viewIn)) {
            val ar = gson.fromJson(news.viewIn, JsonArray::class.java)
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
        with(holder.rowNewsBinding) {
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
            recyclerGchat.visibility = View.GONE
            sharedChat.visibility = View.GONE
        }
    }

    private fun configureUser(holder: ViewHolderNews, news: RealmNews): RealmUserModel? {
        val userModel = mRealm.where(RealmUserModel::class.java)
            .equalTo("id", news.userId)
            .findFirst()
        val userFullName = userModel?.getFullNameWithMiddleName()?.trim()
        if (userModel != null && currentUser != null) {
            holder.rowNewsBinding.tvName.text =
                if (userFullName.isNullOrEmpty()) news.userName else userFullName
            Utilities.loadImage(userModel.userImage, holder.rowNewsBinding.imgUser)
            showHideButtons(news, holder)
        } else {
            holder.rowNewsBinding.tvName.text = news.userName
            Utilities.loadImage(null, holder.rowNewsBinding.imgUser)
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
        setMarkdownText(holder.rowNewsBinding.tvMessage, markdownContentWithLocalPaths)
        val fulltext = holder.rowNewsBinding.tvMessage.text
        holder.rowNewsBinding.tvMessage.makeExpandable(
            fullText = fulltext,
            collapsedMaxLines = 6
        )
        holder.rowNewsBinding.tvDate.text =
            if (sharedTeamName.isEmpty() || teamName.isNotEmpty()) {
                formatDate(news.time)
            } else {
                "${formatDate(news.time)} | Shared from $sharedTeamName"
            }
        holder.rowNewsBinding.tvEdited.visibility = if (news.isEdited) View.VISIBLE else View.GONE
    }

    private fun configureEditDeleteButtons(holder: ViewHolderNews, news: RealmNews) {
        if (news.sharedBy == currentUser?._id && !fromLogin && !nonTeamMember && teamName.isEmpty()) {
            holder.rowNewsBinding.imgDelete.visibility = View.VISIBLE
        }

        if (news.userId == currentUser?._id || news.sharedBy == currentUser?._id) {
            holder.rowNewsBinding.imgDelete.setOnClickListener {
                AlertDialog.Builder(context, R.style.AlertDialogTheme)
                    .setMessage(R.string.delete_record)
                    .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                        NewsActions.deletePost(context, mRealm, news, list, teamName,listener)
                        val pos = holder.adapterPosition
                        notifyItemRemoved(pos)
                        notifyItemRangeChanged(pos, list.size)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }

        if (news.userId == currentUser?._id) {
            holder.rowNewsBinding.imgEdit.setOnClickListener {
                NewsActions.showEditAlert(
                    context,
                    mRealm,
                    news.id,
                    true,
                    currentUser,
                    imageList,
                    listener,
                    holder,
                ) { holder, updatedNews, position ->
                    showReplyButton(holder, updatedNews, position)
                    notifyItemChanged(position)
                }
            }
            holder.rowNewsBinding.btnAddLabel.visibility = if (fromLogin || nonTeamMember) View.GONE else View.VISIBLE
        } else {
            holder.rowNewsBinding.imgEdit.visibility = View.GONE
            holder.rowNewsBinding.btnAddLabel.visibility = View.GONE
        }
    }

    private fun handleChat(holder: ViewHolderNews, news: RealmNews) {
        if (news.newsId?.isNotEmpty() == true) {
            val conversations = gson.fromJson(news.conversations, Array<Conversation>::class.java).toList()
            val chatAdapter = ChatAdapter(ArrayList(), context, holder.rowNewsBinding.recyclerGchat)

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

            holder.rowNewsBinding.recyclerGchat.adapter = chatAdapter
            holder.rowNewsBinding.recyclerGchat.layoutManager = LinearLayoutManager(context)
            holder.rowNewsBinding.recyclerGchat.visibility = View.VISIBLE
            holder.rowNewsBinding.sharedChat.visibility = View.VISIBLE
        } else {
            holder.rowNewsBinding.recyclerGchat.visibility = View.GONE
            holder.rowNewsBinding.sharedChat.visibility = View.GONE
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

    fun updateList(newList: List<RealmNews?>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }

    private fun setMemberClickListeners(holder: ViewHolderNews, userModel: RealmUserModel?, currentLeader: RealmUserModel?) {
        if (!fromLogin) {
            holder.rowNewsBinding.imgUser.setOnClickListener {
                val activity = it.context as AppCompatActivity
                val model = userModel ?: currentLeader
                NewsActions.showMemberDetails(activity, model, profileDbHandler)
            }
            holder.rowNewsBinding.tvName.setOnClickListener {
                val activity = it.context as AppCompatActivity
                val model = userModel ?: currentLeader
                NewsActions.showMemberDetails(activity, model, profileDbHandler)
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

    private fun canEdit(news: RealmNews?): Boolean =
        isLoggedInAndMember() && (isOwner(news) || isAdmin() || isTeamLeader())

    private fun canDelete(news: RealmNews?): Boolean =
        isLoggedInAndMember() && (isOwner(news) || isSharedByCurrentUser(news) || isAdmin() || isTeamLeader())

    private fun canReply(): Boolean =
        isLoggedInAndMember() && !isGuestUser()

    private fun canAddLabel(news: RealmNews?): Boolean =
        isLoggedInAndMember() && (isOwner(news) || isTeamLeader() || isAdmin())

    private fun canShare(news: RealmNews?): Boolean =
        isLoggedInAndMember() && !news?.isCommunityNews!! && !isGuestUser()

    private fun View.setVisibility(condition: Boolean) {
        visibility = if (condition) View.VISIBLE else View.GONE
    }

    fun isTeamLeader(): Boolean {
        if(teamId==null)return false
        val team = mRealm.where(RealmMyTeam::class.java)
            .equalTo("teamId", teamId)
            .equalTo("isLeader", true)
            .findFirst()
        return team?.userId == currentUser?._id
    }

    private fun getReplies(finalNews: RealmNews?): List<RealmNews> =
        newsRepository.getReplies(finalNews?.id)

    private fun updateReplyCount(viewHolder: ViewHolderNews, replies: List<RealmNews>, position: Int) {
        with(viewHolder.rowNewsBinding) {
            btnShowReply.text = String.format(Locale.getDefault(),"(%d)", replies.size)
            btnShowReply.setTextColor(context.getColor(R.color.daynight_textColor))
            val visible = replies.isNotEmpty() && !(position == 0 && parentNews != null) && canReply()
            btnShowReply.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    private fun getNews(holder: RecyclerView.ViewHolder, position: Int): RealmNews? {
        val news: RealmNews? = if (parentNews != null) {
            if (position == 0) {
                (holder.itemView as CardView).setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_blue_50))
                parentNews
            } else {
                (holder.itemView as CardView).setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_white_1000))
                list[position - 1]
            }
        } else {
            (holder.itemView as CardView).setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_white_1000))
            list[position]
        }
        return news
    }

    private fun showHideButtons(news: RealmNews?, holder: RecyclerView.ViewHolder) {
        val viewHolder = holder as ViewHolderNews
        with(viewHolder.rowNewsBinding) {
            imgEdit.setVisibility(canEdit(news))
            imgDelete.setVisibility(canDelete(news))
            btnAddLabel.setVisibility(canAddLabel(news))
            llEditDelete.setVisibility(canEdit(news) || canDelete(news))
        }
    }

    private fun shouldShowReplyButton(): Boolean = canReply()

    private fun showReplyButton(holder: RecyclerView.ViewHolder, finalNews: RealmNews?, position: Int) {
        val viewHolder = holder as ViewHolderNews
        if (shouldShowReplyButton()) {
            viewHolder.rowNewsBinding.btnReply.visibility = if (nonTeamMember) View.GONE else View.VISIBLE
            viewHolder.rowNewsBinding.btnReply.setOnClickListener {
                NewsActions.showEditAlert(
                    context,
                    mRealm,
                    finalNews?.id,
                    false,
                    currentUser,
                    imageList,
                    listener,
                     viewHolder,
                ) { holder, news, i -> showReplyButton(holder, news, i) }
            }
        } else {
            viewHolder.rowNewsBinding.btnReply.visibility = View.GONE
        }

        val replies = getReplies(finalNews)
        updateReplyCount(viewHolder, replies, position)

        viewHolder.rowNewsBinding.btnShowReply.setOnClickListener {
            sharedPreferences?.setRepliedNewsId(finalNews?.id)
            listener?.showReply(finalNews, fromLogin, nonTeamMember)
        }
    }

    override fun getItemCount(): Int {
        return if (parentNews == null) list.size else list.size + 1
    }

    interface OnNewsItemClickListener {
        fun showReply(news: RealmNews?, fromLogin: Boolean, nonTeamMember: Boolean)
        fun addImage(llImage: LinearLayout?)
        fun onNewsItemClick(news: RealmNews?)
        fun clearImages()
        fun onDataChanged()
    }

    private fun showShareButton(holder: RecyclerView.ViewHolder, news: RealmNews?) {
        val viewHolder = holder as ViewHolderNews

        viewHolder.rowNewsBinding.btnShare.setVisibility(canShare(news))

        viewHolder.rowNewsBinding.btnShare.setOnClickListener {
            AlertDialog.Builder(context, R.style.AlertDialogTheme)
                .setTitle(R.string.share_with_community)
                .setMessage(R.string.confirm_share_community)
                .setPositiveButton(R.string.yes) { _, _ ->
                    val array = gson.fromJson(news?.viewIn, JsonArray::class.java)
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
                    news?.sharedBy = currentUser?.id
                    news?.viewIn = gson.toJson(array)
                    mRealm.commitTransaction()
                    Utilities.toast(context, context.getString(R.string.shared_to_community))
                    viewHolder.rowNewsBinding.btnShare.visibility = View.GONE
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
        val imageUrls = news?.imageUrls
        if (!imageUrls.isNullOrEmpty()) {
            try {
                val imgObject = gson.fromJson(imageUrls[0], JsonObject::class.java)
                val path = JsonUtils.getString("imageUrl", imgObject)
                val request = Glide.with(context)
                val target = if (path.lowercase(Locale.getDefault()).endsWith(".gif")) {
                    request.asGif().load(if (File(path).exists()) File(path) else path)
                } else {
                    request.load(if (File(path).exists()) File(path) else path)
                }
                target.placeholder(R.drawable.ic_loading)
                    .error(R.drawable.ic_loading)
                    .into(binding.imgNews)
                binding.imgNews.visibility = View.VISIBLE
                binding.imgNews.setOnClickListener {
                    showZoomableImage(it.context, path)
                }
                return
            } catch (_: Exception) {
            }
        }

        news?.imagesArray?.let { imagesArray ->
            if (imagesArray.size() > 0) {
                val ob = imagesArray[0]?.asJsonObject
                val resourceId = JsonUtils.getString("resourceId", ob)
                val library = mRealm.where(RealmMyLibrary::class.java)
                    .equalTo("_id", resourceId)
                    .findFirst()

                val basePath = context.getExternalFilesDir(null)
                if (library != null && basePath != null) {
                    val imageFile = File(basePath, "ole/${library.id}/${library.resourceLocalAddress}")
                    if (imageFile.exists()) {
                        val request = Glide.with(context)
                        val isGif = library.resourceLocalAddress?.lowercase(Locale.getDefault())?.endsWith(".gif") == true
                        val target = if (isGif) {
                            request.asGif().load(imageFile)
                        } else {
                            request.load(imageFile)
                        }
                        target.placeholder(R.drawable.ic_loading)
                            .error(R.drawable.ic_loading)
                            .into(binding.imgNews)
                        binding.imgNews.visibility = View.VISIBLE
                        binding.imgNews.setOnClickListener {
                            showZoomableImage(it.context, imageFile.toString())
                        }
                        return
                    }
                }
            }
        }
        binding.imgNews.visibility = View.GONE
    }

    private fun showZoomableImage(context: Context, imageUrl: String) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_zoomable_image, null)
        val photoView = view.findViewById<PhotoView>(R.id.photoView)
        val closeButton = view.findViewById<ImageView>(R.id.closeButton)

        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(Color.BLACK.toDrawable())

        val request = Glide.with(context)
        val target = if (imageUrl.lowercase(Locale.getDefault()).endsWith(".gif")) {
            val file = File(imageUrl)
            if (file.exists()) request.asGif().load(file) else request.asGif().load(imageUrl)
        } else {
            val file = File(imageUrl)
            if (file.exists()) request.load(file) else request.load(imageUrl)
        }
        target.error(R.drawable.ic_loading).into(photoView)

        closeButton.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    internal inner class ViewHolderNews(val rowNewsBinding: RowNewsBinding) : RecyclerView.ViewHolder(rowNewsBinding.root) {
        private var adapterPosition = 0
        fun bind(position: Int) {
            adapterPosition = position
        }
    }
}