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
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.github.chrisbanes.photoview.PhotoView
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fisk.chipcloud.ChipCloud
import io.realm.RealmList
import java.io.File
import java.util.Calendar
import java.util.Locale
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNewsBinding
import org.ole.planet.myplanet.model.ChatMessage
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.NewsItem
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.chat.ChatAdapter
import org.ole.planet.myplanet.utilities.Constants
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

class AdapterNews(
    var context: Context,
    private var currentUser: RealmUserModel?,
    private val parentNews: NewsItem?,
    private val teamName: String = "",
    private val isTeamLeader: Boolean = false
) : ListAdapter<NewsItem, RecyclerView.ViewHolder>(
    DiffUtils.itemCallback(
        areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
        areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
    )
) {
    private var listener: OnNewsItemClickListener? = null
    var sharedPrefManager: SharedPrefManager? = null
    private var fromLogin = false
    private var nonTeamMember = false
    private var recyclerView: RecyclerView? = null
    lateinit var settings: SharedPreferences

    fun addItem(news: NewsItem?) {
        if (news == null) return
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = RowNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ViewHolderNews(binding)
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderNews) {
            holder.bind(position)
            val news = getNews(holder, position)

            if (news != null) {
                resetViews(holder)
                val sharedTeamName = extractSharedTeamName(news)

                holder.binding.tvName.text = news.userFullName
                ImageUtils.loadImage(news.userImage, holder.binding.imgUser)

                showHideButtons(news, holder)
                updateReplyCount(holder, news, position)
                showShareButton(holder, news)
                setMessageAndDate(holder, news, sharedTeamName)
                configureEditDeleteButtons(holder, news)
                loadImage(holder.binding, news)
                showReplyButton(holder, news, position)

                val canManageLabels = canAddLabel(news)
                setupAddLabelMenu(holder.binding, news, canManageLabels)
                showChips(holder.binding, news, canManageLabels)

                handleChat(holder, news)
                setMemberClickListeners(holder, news)
            }
        }
    }

    fun updateReplyBadge(newsId: String?) {
        if (newsId.isNullOrEmpty()) return
        val index = if (parentNews != null) {
            when {
                parentNews.id == newsId -> 0
                else -> currentList.indexOfFirst { it.id == newsId }.let { if (it != -1) it + 1 else -1 }
            }
        } else {
            currentList.indexOfFirst { it.id == newsId }
        }
        if (index >= 0) {
            notifyItemChanged(index)
        }
    }

    private fun extractSharedTeamName(news: NewsItem): String {
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

    private fun setMessageAndDate(holder: ViewHolderNews, news: NewsItem, sharedTeamName: String) {
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

    private fun configureEditDeleteButtons(holder: ViewHolderNews, news: NewsItem) {
        if (news.sharedBy == currentUser?._id && !fromLogin && !nonTeamMember && teamName.isEmpty()) {
            holder.binding.imgDelete.visibility = View.VISIBLE
        }

        if (news.userId == currentUser?._id || news.sharedBy == currentUser?._id) {
            holder.binding.imgDelete.setOnClickListener {
                AlertDialog.Builder(context, R.style.AlertDialogTheme)
                    .setMessage(R.string.delete_record)
                    .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                        listener?.onDelete(news)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }

        if (news.userId == currentUser?._id) {
            holder.binding.imgEdit.setOnClickListener {
                listener?.onEdit(news, holder)
            }
        } else {
            holder.binding.imgEdit.visibility = View.GONE
        }
    }

    private fun handleChat(holder: ViewHolderNews, news: NewsItem) {
        if (!news.newsId.isNullOrEmpty()) {
            val conversations = GsonUtils.gson.fromJson(news.conversations, Array<Conversation>::class.java).toList()
            val chatAdapter = ChatAdapter(context, holder.binding.recyclerGchat, holder.itemView.findViewTreeLifecycleOwner()?.lifecycleScope)

            if (currentUser?.id?.startsWith("guest") == false) {
                chatAdapter.setOnChatItemClickListener(object : ChatAdapter.OnChatItemClickListener {
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

    fun updateList(newList: List<NewsItem>) {
        submitList(newList)
    }

    fun refreshCurrentItems() {
        submitList(currentList.toList())
    }

    private fun setMemberClickListeners(holder: ViewHolderNews, news: NewsItem) {
        if (!fromLogin) {
            holder.binding.imgUser.setOnClickListener {
                listener?.onMemberSelected(news)
            }
            holder.binding.tvName.setOnClickListener {
                listener?.onMemberSelected(news)
            }
        }
    }

    private fun isGuestUser() = currentUser?.id?.startsWith("guest") == true

    private fun isOwner(news: NewsItem?): Boolean =
        news?.userId == currentUser?._id

    private fun isSharedByCurrentUser(news: NewsItem?): Boolean =
        news?.sharedBy == currentUser?._id

    private fun isAdmin(): Boolean =
        currentUser?.level.equals("admin", ignoreCase = true)

    private fun isLoggedInAndMember(): Boolean =
        !fromLogin && !nonTeamMember

    private fun canEdit(news: NewsItem?): Boolean =
        isLoggedInAndMember() && (isOwner(news) || isAdmin() || isTeamLeader)

    private fun canDelete(news: NewsItem?): Boolean =
        isLoggedInAndMember() && (isOwner(news) || isSharedByCurrentUser(news) || isAdmin() || isTeamLeader)

    private fun canReply(): Boolean =
        isLoggedInAndMember() && !isGuestUser()

    private fun canAddLabel(news: NewsItem?): Boolean =
        isLoggedInAndMember() && (isOwner(news) || isTeamLeader)

    private fun canShare(news: NewsItem?): Boolean =
        isLoggedInAndMember() && !news?.isCommunityNews!! && !isGuestUser()

    private fun View.setVisibility(condition: Boolean) {
        visibility = if (condition) View.VISIBLE else View.GONE
    }

    private fun updateReplyCount(viewHolder: ViewHolderNews, news: NewsItem, position: Int) {
        with(viewHolder.binding) {
            btnShowReply.text = String.format(Locale.getDefault(),"(%d)", news.replyCount)
            btnShowReply.setTextColor(context.getColor(R.color.daynight_textColor))
            val visible = news.replyCount > 0 && !(position == 0 && parentNews != null) && canReply()
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

    private fun showHideButtons(news: NewsItem, holder: RecyclerView.ViewHolder) {
        val viewHolder = holder as ViewHolderNews
        with(viewHolder.binding) {
            imgEdit.setVisibility(canEdit(news))
            imgDelete.setVisibility(canDelete(news))
            btnAddLabel.setVisibility(canAddLabel(news))
            llEditDelete.setVisibility(canEdit(news) || canDelete(news))
        }
    }

    private fun shouldShowReplyButton(): Boolean = canReply()

    private fun showReplyButton(holder: RecyclerView.ViewHolder, news: NewsItem, position: Int) {
        val viewHolder = holder as ViewHolderNews
        if (shouldShowReplyButton()) {
            viewHolder.binding.btnReply.visibility = if (nonTeamMember) View.GONE else View.VISIBLE
            viewHolder.binding.btnReply.setOnClickListener {
                listener?.onReply(news, holder)
            }
        } else {
            viewHolder.binding.btnReply.visibility = View.GONE
        }

        updateReplyCount(viewHolder, news, position)

        viewHolder.binding.btnShowReply.setOnClickListener {
            sharedPrefManager?.setRepliedNewsId(news.id)
            listener?.showReply(news, fromLogin, nonTeamMember)
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
        fun onMemberSelected(news: NewsItem)
        fun getCurrentImageList(): RealmList<String>?
        fun onDelete(news: NewsItem)
        fun onEdit(news: NewsItem, holder: RecyclerView.ViewHolder)
        fun onReply(news: NewsItem, holder: RecyclerView.ViewHolder)
        fun onShare(news: NewsItem)
        fun onAddLabel(news: NewsItem, label: String)
        fun onRemoveLabel(news: NewsItem, label: String)
    }

    private fun showShareButton(holder: RecyclerView.ViewHolder, news: NewsItem) {
        val viewHolder = holder as ViewHolderNews
        viewHolder.binding.btnShare.setVisibility(canShare(news))

        viewHolder.binding.btnShare.setOnClickListener {
            listener?.onShare(news)
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

    private fun loadImage(binding: RowNewsBinding, news: NewsItem) {
        binding.imgNews.visibility = View.GONE
        binding.llNewsImages.visibility = View.GONE
        binding.llNewsImages.removeAllViews()

        val imageUrls = news.imageUrls
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

        val resolved = news.resolvedLibraryImages
        if (resolved.isNotEmpty()) {
             if (resolved.size == 1) {
                 val path = resolved.values.first()
                 loadLibraryImage(binding, path)
             } else {
                 binding.llNewsImages.visibility = View.VISIBLE
                 for (path in resolved.values) {
                     addLibraryImageToContainer(binding, path)
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

    private fun loadLibraryImage(binding: RowNewsBinding, localPath: String?) {
        if (localPath == null) return
        val basePath = context.getExternalFilesDir(null)
        if (basePath != null) {
            val imageFile = File(basePath, "ole/$localPath")
            if (imageFile.exists()) {
                val request = Glide.with(binding.imgNews.context)
                val isGif = localPath.lowercase(Locale.getDefault()).endsWith(".gif")
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

    private fun addLibraryImageToContainer(binding: RowNewsBinding, localPath: String?) {
        if (localPath == null) return
        val basePath = context.getExternalFilesDir(null)
        if (basePath != null) {
            val imageFile = File(basePath, "ole/$localPath")
            if (imageFile.exists()) {
                val imageView = ImageView(context)
                val size = (100 * context.resources.displayMetrics.density).toInt()
                val margin = (4 * context.resources.displayMetrics.density).toInt()
                val params = ViewGroup.MarginLayoutParams(size, size)
                params.setMargins(margin, margin, margin, margin)
                imageView.layoutParams = params
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP

                val request = Glide.with(context)
                val isGif = localPath.lowercase(Locale.getDefault()).endsWith(".gif")
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

    private fun setupAddLabelMenu(binding: RowNewsBinding, news: NewsItem, canManageLabels: Boolean) {
        binding.btnAddLabel.setOnClickListener(null)
        binding.btnAddLabel.isEnabled = canManageLabels
        if (!canManageLabels) {
             binding.btnAddLabel.visibility = View.GONE
             return
        }

        val usedLabels = news.labels?.toSet() ?: emptySet()
        val labels = Constants.LABELS.values.toSet()
        binding.btnAddLabel.visibility = if (usedLabels.containsAll(labels)) View.GONE else View.VISIBLE

        binding.btnAddLabel.setOnClickListener {
             val availableLabels = Constants.LABELS.filterValues { it !in usedLabels }
             val wrapper = androidx.appcompat.view.ContextThemeWrapper(context, R.style.CustomPopupMenu)
             val menu = android.widget.PopupMenu(wrapper, binding.btnAddLabel)
             availableLabels.keys.forEach { labelName -> menu.menu.add(labelName) }
             menu.setOnMenuItemClickListener { menuItem ->
                 val selectedLabel = Constants.LABELS[menuItem.title]
                 if (selectedLabel != null) {
                     listener?.onAddLabel(news, selectedLabel)
                 }
                 true
             }
             menu.show()
        }
    }

    private fun showChips(binding: RowNewsBinding, news: NewsItem, canManageLabels: Boolean) {
        binding.fbChips.removeAllViews()

        for (label in news.labels ?: emptyList()) {
            val chipConfig = Utilities.getCloudConfig().apply {
                selectMode(if (canManageLabels) ChipCloud.SelectMode.close else ChipCloud.SelectMode.none)
            }

            val chipCloud = ChipCloud(context, binding.fbChips, chipConfig)
            chipCloud.addChip(getLabel(label))

            if (canManageLabels) {
                chipCloud.setDeleteListener { _: Int, labelText: String? ->
                    val selectedLabel = when {
                        labelText == null -> null
                        Constants.LABELS.containsKey(labelText) -> Constants.LABELS[labelText]
                        else -> news.labels?.firstOrNull { getLabel(it) == labelText }
                    }
                    if (selectedLabel != null) {
                         listener?.onRemoveLabel(news, selectedLabel)
                    }
                }
            }
        }
    }

    private fun getLabel(s: String): String {
        for (key in Constants.LABELS.keys) {
            if (s == Constants.LABELS[key]) {
                return key
            }
        }
        return formatLabelValue(s)
    }

    private fun formatLabelValue(raw: String): String {
        val whitespaceRegex = Regex("\\s+")
        val cleaned = raw.replace("_", " ").replace("-", " ")
        if (cleaned.isBlank()) {
            return raw
        }
        return cleaned
            .trim()
            .split(whitespaceRegex)
            .joinToString(" ") { part ->
                part.lowercase(Locale.getDefault()).replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                }
            }
    }

    internal inner class ViewHolderNews(val binding: RowNewsBinding) : RecyclerView.ViewHolder(binding.root) {
        private var adapterPosition = 0
        fun bind(position: Int) {
            adapterPosition = position
        }
    }
}
