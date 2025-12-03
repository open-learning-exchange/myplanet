package org.ole.planet.myplanet.ui.news

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
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
import io.realm.RealmList
import java.io.File
import java.util.Locale
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNewsBinding
import org.ole.planet.myplanet.model.ChatMessage
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.dto.NewsItem
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.chat.ChatAdapter
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.ImageUtils
import org.ole.planet.myplanet.utilities.Markdown.prependBaseUrlToImages
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.makeExpandable

class AdapterNews(
    var context: Context,
    private var currentUser: RealmUserModel?,
    private val parentNews: NewsItem?,
    private val fromLogin: Boolean,
    private val userProfileDbHandler: UserProfileDbHandler
) : ListAdapter<NewsItem, AdapterNews.ViewHolderNews>(
    DiffUtils.itemCallback(
        areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
        areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
    )
) {
    private var listener: OnNewsItemClickListener? = null
    @Inject
    lateinit var sharedPrefManager: SharedPrefManager
    private var imageList: RealmList<String>? = null
    private var nonTeamMember = false
    private var recyclerView: RecyclerView? = null
    var user: RealmUserModel? = null
    private var labelManager: NewsLabelManager? = null
    lateinit var settings: SharedPreferences
    private val leadersList: List<RealmUserModel> by lazy {
        val raw = settings.getString("communityLeaders", "") ?: ""
        RealmUserModel.parseLeadersJson(raw)
    }

    fun setImageList(imageList: RealmList<String>?) {
        this.imageList = imageList
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderNews {
        val binding = RowNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        user = userProfileDbHandler.userModel
        settings = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return ViewHolderNews(binding)
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onBindViewHolder(holder: ViewHolderNews, position: Int) {
        val news = getItem(position)
        holder.bind(position)
        if (parentNews != null && position == 0) {
            (holder.itemView as CardView).setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_blue_50))
        } else {
            (holder.itemView as CardView).setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_white_1000))
        }
        resetViews(holder)
        updateReplyCount(holder, news.replyCount, position)
        configureUser(holder, news)
        showShareButton(holder, news)
        setMessageAndDate(holder, news)
        configureEditDeleteButtons(holder, news)
        loadImage(holder.binding, news)
        showReplyButton(holder, news, position)
        val currentLeader = getCurrentLeader(news)
        setMemberClickListeners(holder, news, currentLeader)
        handleChat(holder, news)
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

    private fun configureUser(holder: ViewHolderNews, news: NewsItem) {
        val userFullName = news.userFullName?.trim()
        holder.binding.tvName.text = if (userFullName.isNullOrEmpty()) news.userName else userFullName
        ImageUtils.loadImage(news.userImage, holder.binding.imgUser)
        showHideButtons(news, holder)
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
        holder.binding.tvMessage.makeExpandable(fullText = fulltext, collapsedMaxLines = 6)
        holder.binding.tvDate.text = if (news.sharedTeamName.isEmpty()) {
            formatDate(news.time)
        } else {
            "${formatDate(news.time)} | Shared from ${news.sharedTeamName}"
        }
        holder.binding.tvEdited.visibility = if (news.isEdited) View.VISIBLE else View.GONE
    }

    private fun configureEditDeleteButtons(holder: ViewHolderNews, news: NewsItem) {
        if (news.sharedBy == currentUser?._id && !fromLogin && !nonTeamMember) {
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
                listener?.onEdit(news)
            }
        } else {
            holder.binding.imgEdit.visibility = View.GONE
        }
    }

    private fun handleChat(holder: ViewHolderNews, news: NewsItem) {
        if (!news.conversations.isNullOrEmpty()) {
            val conversations = try {
                GsonUtils.gson.fromJson(news.conversations, Array<Conversation>::class.java).toList()
            } catch (e: Exception) {
                emptyList()
            }
            val chatAdapter = ChatAdapter(context, holder.binding.recyclerGchat, holder.itemView.findViewTreeLifecycleOwner()?.lifecycleScope)

            if (user?.id?.startsWith("guest") == false) {
                chatAdapter.setOnChatItemClickListener(object : ChatAdapter.OnChatItemClickListener {
                    override fun onChatItemClick(position: Int, chatItem: ChatMessage) {
                        listener?.onNewsItemClick(news)
                    }
                })
            }

            val messages = mutableListOf<ChatMessage>()
            for (conversation in conversations) {
                messages.add(ChatMessage(conversation.query!!, ChatMessage.QUERY))
                messages.add(ChatMessage(conversation.response!!, ChatMessage.RESPONSE, ChatMessage.RESPONSE_SOURCE_SHARED_VIEW_MODEL))
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

    private fun getCurrentLeader(news: NewsItem): RealmUserModel? {
        for (leader in leadersList) {
            if (leader.name == news.userName) {
                return leader
            }
        }
        return null
    }

    private fun setMemberClickListeners(holder: ViewHolderNews, news: NewsItem, currentLeader: RealmUserModel?) {
        if (!fromLogin) {
            holder.binding.imgUser.setOnClickListener {
                listener?.onMemberSelected(news.userId, currentLeader)
            }
            holder.binding.tvName.setOnClickListener {
                listener?.onMemberSelected(news.userId, currentLeader)
            }
        }
    }

    private fun showHideButtons(news: NewsItem, holder: ViewHolderNews) {
        with(holder.binding) {
            imgEdit.visibility = if (news.canEdit) View.VISIBLE else View.GONE
            imgDelete.visibility = if (news.canDelete) View.VISIBLE else View.GONE
            btnAddLabel.visibility = if (news.canAddLabel) View.VISIBLE else View.GONE
            llEditDelete.visibility = if (news.canEdit || news.canDelete) View.VISIBLE else View.GONE
        }
    }

    private fun showReplyButton(holder: ViewHolderNews, finalNews: NewsItem, position: Int) {
        if (finalNews.canReply) {
            holder.binding.btnReply.visibility = if (nonTeamMember) View.GONE else View.VISIBLE
            holder.binding.btnReply.setOnClickListener {
                listener?.onReply(finalNews)
            }
        } else {
            holder.binding.btnReply.visibility = View.GONE
        }
        updateReplyCount(holder, finalNews.replyCount, position)
        holder.binding.btnShowReply.setOnClickListener {
            sharedPrefManager.setRepliedNewsId(finalNews.id)
            listener?.showReply(finalNews, fromLogin, nonTeamMember)
        }
    }

    interface OnNewsItemClickListener {
        fun showReply(news: NewsItem, fromLogin: Boolean, nonTeamMember: Boolean)
        fun addImage(llImage: ViewGroup?)
        fun onNewsItemClick(news: NewsItem)
        fun clearImages()
        fun onDataChanged()
        fun onMemberSelected(userId: String?, leader: RealmUserModel?)
        fun getCurrentImageList(): RealmList<String>?
        fun onEdit(news: NewsItem)
        fun onDelete(news: NewsItem)
        fun onReply(news: NewsItem)
        fun onShare(news: NewsItem)
    }

    private fun showShareButton(holder: ViewHolderNews, news: NewsItem) {
        holder.binding.btnShare.visibility = if (news.canShare) View.VISIBLE else View.GONE
        holder.binding.btnShare.setOnClickListener {
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

        if (news.imageUrls.isNotEmpty()) {
            if (news.imageUrls.size == 1) {
                loadSingleImage(binding, news.imageUrls.first())
            } else {
                binding.llNewsImages.visibility = View.VISIBLE
                for (imageUrl in news.imageUrls) {
                    addImageToContainer(binding, imageUrl)
                }
            }
        } else if (news.resolvedLibraryImagePaths.isNotEmpty()) {
            if (news.resolvedLibraryImagePaths.size == 1) {
                loadSingleImage(binding, news.resolvedLibraryImagePaths.first())
            } else {
                binding.llNewsImages.visibility = View.VISIBLE
                for (path in news.resolvedLibraryImagePaths) {
                    addImageToContainer(binding, path)
                }
            }
        }
    }

    private fun loadSingleImage(binding: RowNewsBinding, path: String) {
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

    private fun addImageToContainer(binding: RowNewsBinding, path: String) {
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

    private fun updateReplyCount(viewHolder: ViewHolderNews, replyCount: Int, position: Int) {
        with(viewHolder.binding) {
            btnShowReply.text = String.format(Locale.getDefault(), "(%d)", replyCount)
            btnShowReply.setTextColor(context.getColor(R.color.daynight_textColor))
            val visible = replyCount > 0 && !(position == 0 && parentNews != null)
            btnShowReply.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    inner class ViewHolderNews(val binding: RowNewsBinding) : RecyclerView.ViewHolder(binding.root) {
        private var adapterPosition = 0
        fun bind(position: Int) {
            adapterPosition = position
        }
    }
}
