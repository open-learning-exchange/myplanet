package org.ole.planet.myplanet.ui.news

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
import fisk.chipcloud.ChipCloud
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNewsBinding
import org.ole.planet.myplanet.model.ChatMessage
import org.ole.planet.myplanet.model.dto.NewsItem
import org.ole.planet.myplanet.ui.chat.ChatAdapter
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.ImageUtils
import org.ole.planet.myplanet.utilities.Markdown.prependBaseUrlToImages
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.makeExpandable
import java.io.File
import java.util.Locale

class AdapterNews(
    private val context: Context,
    private val parentNews: NewsItem?,
    private val listener: OnNewsItemClickListener?
) : ListAdapter<NewsItem, RecyclerView.ViewHolder>(
    DiffUtils.itemCallback(
        areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
        areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
    )
) {
    private var fromLogin = false
    private var nonTeamMember = false
    private var imageList: List<String>? = null

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

    fun updateList(newList: List<NewsItem>) {
        submitList(newList)
    }

    fun addItem(news: NewsItem?) {
        if (news == null) return
        val current = currentList.toMutableList()
        current.add(0, news)
        submitList(current)
    }

    fun refreshCurrentItems() {
        submitList(currentList.toList())
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
            listener?.onDataChanged()
        }
    }

    override fun getItemCount(): Int {
        return if (parentNews == null) super.getItemCount() else super.getItemCount() + 1
    }

    public override fun getItem(position: Int): NewsItem {
        return super.getItem(position)
    }

    private fun getNewsItem(position: Int): NewsItem? {
        if (parentNews != null) {
            if (position == 0) return parentNews
            if (position - 1 < super.getItemCount()) return getItem(position - 1)
            return null
        }
        if (position < super.getItemCount()) return getItem(position)
        return null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = RowNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderNews(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderNews) {
            val news = getNewsItem(position) ?: return

            if (parentNews != null && position == 0) {
                (holder.itemView as androidx.cardview.widget.CardView).setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_blue_50))
            } else {
                (holder.itemView as androidx.cardview.widget.CardView).setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_white_1000))
            }

            val binding = holder.binding
            with(binding) {
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

            binding.tvName.text = news.userName
            ImageUtils.loadImage(news.userImage, binding.imgUser)

            val markdownContentWithLocalPaths = prependBaseUrlToImages(
                news.message,
                "file://" + context.getExternalFilesDir(null) + "/ole/",
                600,
                350
            )
            setMarkdownText(binding.tvMessage, markdownContentWithLocalPaths)
            val fulltext = binding.tvMessage.text
            binding.tvMessage.makeExpandable(fullText = fulltext, collapsedMaxLines = 6)
            binding.tvDate.text = news.dateText
            binding.tvEdited.visibility = if (news.isEdited) View.VISIBLE else View.GONE

            if (canEdit(news)) {
                binding.imgEdit.visibility = View.VISIBLE
                binding.imgEdit.setOnClickListener { listener?.onEditNews(news) }
            }
            if (canDelete(news)) {
                binding.imgDelete.visibility = View.VISIBLE
                binding.imgDelete.setOnClickListener { listener?.onDeleteNews(news) }
            }
            binding.llEditDelete.visibility = if (binding.imgEdit.visibility == View.VISIBLE || binding.imgDelete.visibility == View.VISIBLE) View.VISIBLE else View.GONE

            binding.btnShowReply.text = String.format(Locale.getDefault(), "(%d)", news.replyCount)
            binding.btnShowReply.setTextColor(context.getColor(R.color.daynight_textColor))
            val showReplyCount = news.replyCount > 0 && !(position == 0 && parentNews != null) && canReply(news)
            binding.btnShowReply.visibility = if (showReplyCount) View.VISIBLE else View.GONE
            binding.btnShowReply.setOnClickListener { listener?.showReply(news, fromLogin, nonTeamMember) }

            if (canReply(news)) {
                binding.btnReply.visibility = if (nonTeamMember) View.GONE else View.VISIBLE
                binding.btnReply.setOnClickListener { listener?.onReplyNews(news) }
            } else {
                binding.btnReply.visibility = View.GONE
            }

            binding.btnShare.visibility = if (canShare(news)) View.VISIBLE else View.GONE
            binding.btnShare.setOnClickListener { listener?.onShareNews(news) }

            loadImage(binding, news)
            handleChat(holder, news)

            updateAddLabelVisibility(binding, news)
            binding.btnAddLabel.setOnClickListener { listener?.onAddLabel(news, binding.btnAddLabel) }
            showChips(binding, news)

            if (!fromLogin) {
                binding.imgUser.setOnClickListener { listener?.onMemberSelected(news) }
                binding.tvName.setOnClickListener { listener?.onMemberSelected(news) }
            }
        }
    }

    private fun canEdit(news: NewsItem): Boolean = !fromLogin && !nonTeamMember && news.canEdit
    private fun canDelete(news: NewsItem): Boolean = !fromLogin && !nonTeamMember && news.canDelete
    private fun canReply(news: NewsItem): Boolean = !fromLogin && !nonTeamMember && news.canReply
    private fun canShare(news: NewsItem): Boolean = !fromLogin && !nonTeamMember && news.canShare
    private fun canAddLabel(news: NewsItem): Boolean = !fromLogin && !nonTeamMember && news.canAddLabel

    private fun updateAddLabelVisibility(binding: RowNewsBinding, news: NewsItem) {
        if (!canAddLabel(news)) {
            binding.btnAddLabel.visibility = View.GONE
            return
        }
        binding.btnAddLabel.visibility = View.VISIBLE
    }

    private fun showChips(binding: RowNewsBinding, news: NewsItem) {
        binding.fbChips.removeAllViews()
        news.labels?.forEach { label ->
            val chipConfig = Utilities.getCloudConfig().apply {
                selectMode(if (canAddLabel(news)) ChipCloud.SelectMode.close else ChipCloud.SelectMode.none)
            }
            val chipCloud = ChipCloud(context, binding.fbChips, chipConfig)
            chipCloud.addChip(getLabelDisplay(label))

            if (canAddLabel(news)) {
                chipCloud.setDeleteListener { _: Int, _: String? ->
                    listener?.onRemoveLabel(news, label)
                }
            }
        }
    }

    private fun getLabelDisplay(label: String): String {
        for ((key, value) in Constants.LABELS) {
            if (label == value) return key
        }
        return formatLabel(label)
    }

    private fun formatLabel(raw: String): String {
        val cleaned = raw.replace("_", " ").replace("-", " ")
        if (cleaned.isBlank()) return raw
        return cleaned.trim().split(Regex("\\s+")).joinToString(" ") { part ->
             part.lowercase(Locale.getDefault()).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }

    private fun loadImage(binding: RowNewsBinding, news: NewsItem) {
        val images = mutableListOf<String>()
        news.imageUrls?.let { images.addAll(it) }
        news.libraryImages?.let { images.addAll(it) }

        if (images.isEmpty()) {
            binding.imgNews.visibility = View.GONE
            binding.llNewsImages.visibility = View.GONE
            return
        }

        if (images.size == 1) {
            loadSingleImage(binding, images[0])
        } else {
            binding.llNewsImages.visibility = View.VISIBLE
            images.forEach { addImageToContainer(binding, it) }
        }
    }

    private fun loadSingleImage(binding: RowNewsBinding, path: String) {
        val request = Glide.with(binding.imgNews.context)
        val target = if (path.lowercase(Locale.getDefault()).endsWith(".gif")) {
            request.asGif().load(File(path).takeIf { it.exists() } ?: path)
        } else {
            request.load(File(path).takeIf { it.exists() } ?: path)
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
            request.asGif().load(File(path).takeIf { it.exists() } ?: path)
        } else {
            request.load(File(path).takeIf { it.exists() } ?: path)
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
        val dialog = android.app.Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_zoomable_image, null)
        val photoView = view.findViewById<PhotoView>(R.id.photoView)
        val closeButton = view.findViewById<ImageView>(R.id.closeButton)

        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(android.graphics.Color.BLACK.toDrawable())

        val request = Glide.with(photoView.context)
        val file = File(imageUrl)
        val target = if (imageUrl.lowercase(Locale.getDefault()).endsWith(".gif")) {
            if (file.exists()) request.asGif().load(file) else request.asGif().load(imageUrl)
        } else {
            if (file.exists()) request.load(file) else request.load(imageUrl)
        }
        target.diskCacheStrategy(DiskCacheStrategy.ALL).fitCenter().error(R.drawable.ic_loading).into(photoView)

        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun handleChat(holder: ViewHolderNews, news: NewsItem) {
        if (news.chat && news.conversations?.isNotEmpty() == true) {
            val chatAdapter = ChatAdapter(context, holder.binding.recyclerGchat, holder.itemView.findViewTreeLifecycleOwner()?.lifecycleScope)
            if (canReply(news)) {
                chatAdapter.setOnChatItemClickListener(object : ChatAdapter.OnChatItemClickListener {
                    override fun onChatItemClick(position: Int, chatItem: ChatMessage) {
                        listener?.onNewsItemClick(news)
                    }
                })
            }
            chatAdapter.submitList(news.conversations)

            holder.binding.recyclerGchat.adapter = chatAdapter
            holder.binding.recyclerGchat.layoutManager = LinearLayoutManager(context)
            holder.binding.recyclerGchat.visibility = View.VISIBLE
            holder.binding.sharedChat.visibility = View.VISIBLE
        } else {
            holder.binding.recyclerGchat.visibility = View.GONE
            holder.binding.sharedChat.visibility = View.GONE
        }
    }

    internal inner class ViewHolderNews(val binding: RowNewsBinding) : RecyclerView.ViewHolder(binding.root)

    interface OnNewsItemClickListener {
        fun showReply(news: NewsItem, fromLogin: Boolean, nonTeamMember: Boolean)
        fun addImage(llImage: ViewGroup?)
        fun onNewsItemClick(news: NewsItem)
        fun clearImages()
        fun onDataChanged()
        fun onMemberSelected(news: NewsItem)
        fun getCurrentImageList(): List<String>?

        // New methods
        fun onEditNews(news: NewsItem)
        fun onDeleteNews(news: NewsItem)
        fun onReplyNews(news: NewsItem)
        fun onShareNews(news: NewsItem)
        fun onAddLabel(news: NewsItem, view: View)
        fun onRemoveLabel(news: NewsItem, label: String)
    }
}
