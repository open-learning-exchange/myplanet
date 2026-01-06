package org.ole.planet.myplanet.ui.voices

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.github.chrisbanes.photoview.PhotoView
import java.io.File
import java.util.Locale
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNewsBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.dto.NewsViewData
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.ImageUtils
import org.ole.planet.myplanet.utilities.makeExpandable

class NewsAdapter(
    var context: Context,
) : ListAdapter<NewsViewData, NewsAdapter.ViewHolderNews>(
    DiffUtils.itemCallback(
        areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
        areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
    )
) {
    private var listener: OnNewsItemClickListener? = null

    fun setListener(listener: OnNewsItemClickListener?) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderNews {
        val binding = RowNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderNews(binding)
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onBindViewHolder(holder: ViewHolderNews, position: Int) {
        val news = getItem(position)
        holder.bind(news)
    }

    interface OnNewsItemClickListener {
        fun showReply(news: NewsViewData?)
        fun onNewsItemClick(news: NewsViewData?)
        fun onMemberSelected(userModel: RealmUserModel?)
        fun onDelete(news: NewsViewData?)
        fun onEdit(news: NewsViewData?)
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

    inner class ViewHolderNews(val binding: RowNewsBinding) : RecyclerView.ViewHolder(binding.root) {
        private val imageAdapter = ImageAdapter { imageUrl ->
            showZoomableImage(context, imageUrl)
        }

        init {
            binding.rvImages.adapter = imageAdapter
            binding.rvImages.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        }

        fun bind(news: NewsViewData) {
            binding.tvName.text = news.userName
            ImageUtils.loadImage(news.userImage, binding.imgUser)
            binding.tvMessage.text = news.message
            binding.tvMessage.makeExpandable(fullText = news.message, collapsedMaxLines = 6)
            binding.tvDate.text = news.date
            binding.tvEdited.visibility = if (news.isEdited) View.VISIBLE else View.GONE
            binding.btnShowReply.text = String.format(Locale.getDefault(), "(%d)", news.replyCount)
            binding.btnShowReply.visibility = if (news.replyCount > 0) View.VISIBLE else View.GONE
            binding.btnReply.visibility = if (news.canReply) View.VISIBLE else View.GONE
            binding.imgEdit.visibility = if (news.canEdit) View.VISIBLE else View.GONE
            binding.imgDelete.visibility = if (news.canDelete) View.VISIBLE else View.GONE
            binding.btnAddLabel.visibility = if (news.canAddLabel) View.VISIBLE else View.GONE
            binding.btnShare.visibility = if (news.canShare) View.VISIBLE else View.GONE

            binding.btnReply.setOnClickListener { listener?.onNewsItemClick(news) }
            binding.btnShowReply.setOnClickListener { listener?.showReply(news) }
            binding.imgUser.setOnClickListener { listener?.onMemberSelected(news.user) }
            binding.tvName.setOnClickListener { listener?.onMemberSelected(news.user) }
            binding.imgDelete.setOnClickListener {
                AlertDialog.Builder(context, R.style.AlertDialogTheme)
                    .setMessage(R.string.delete_record)
                    .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                        listener?.onDelete(news)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            binding.imgEdit.setOnClickListener { listener?.onEdit(news) }

            // Image loading
            val allImages = news.imageUrls + news.libraryImageUrls.map { it.second }
            if (allImages.isEmpty()) {
                binding.imgNews.visibility = View.GONE
                binding.rvImages.visibility = View.GONE
            } else if (allImages.size == 1) {
                binding.imgNews.visibility = View.VISIBLE
                binding.rvImages.visibility = View.GONE
                val imageUrl = allImages.first()
                loadImage(binding.imgNews, imageUrl)
                binding.imgNews.setOnClickListener { showZoomableImage(context, imageUrl) }
            } else {
                binding.imgNews.visibility = View.GONE
                binding.rvImages.visibility = View.VISIBLE
                imageAdapter.submitList(allImages)
            }
        }

        private fun loadImage(imageView: ImageView, path: String) {
            val request = Glide.with(imageView.context)
            val file = File(path)
            val target = if (path.lowercase(Locale.getDefault()).endsWith(".gif")) {
                request.asGif().load(file).error(request.asGif().load(path))
            } else {
                request.load(file).error(request.load(path))
            }
            target.diskCacheStrategy(DiskCacheStrategy.ALL)
                .fitCenter()
                .placeholder(R.drawable.ic_loading)
                .error(R.drawable.ic_loading)
                .into(imageView)
        }
    }
}