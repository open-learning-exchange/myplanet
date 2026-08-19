package org.ole.planet.myplanet.ui.teams

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.ItemInlineCommentBinding
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.ImageUtils
import org.ole.planet.myplanet.utils.TimeUtils

class InlineCommentsAdapter(
    private var currentUserId: String? = null,
    private var isLeader: Boolean = false,
    private val onDeleteComment: ((News) -> Unit)? = null
) : ListAdapter<News, InlineCommentsAdapter.CommentViewHolder>(DIFF_CALLBACK) {

    fun updateCurrentUser(userId: String?, leader: Boolean) {
        currentUserId = userId
        isLeader = leader
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemInlineCommentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = getItem(position)
        val binding = holder.binding

        binding.tvUserName.text = if (!comment.userName.isNullOrEmpty()) {
            comment.userName
        } else {
            "Anonymous"
        }

        binding.tvTime.text = TimeUtils.getRelativeTime(comment.time)
        binding.tvMessage.text = comment.message ?: ""
        ImageUtils.loadImage(null, binding.imgUser)

        val canDelete = (currentUserId != null && comment.userId == currentUserId) || isLeader
        binding.btnDeleteComment.isVisible = canDelete
        binding.btnDeleteComment.setOnClickListener {
            onDeleteComment?.invoke(comment)
        }
    }

    class CommentViewHolder(val binding: ItemInlineCommentBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DIFF_CALLBACK = DiffUtils.itemCallback<News>(
            areItemsTheSame = { old, new -> old.id == new.id },
            areContentsTheSame = { old, new ->
                old.message == new.message &&
                    old.time == new.time &&
                    old.userName == new.userName &&
                    old.userId == new.userId
            }
        )
    }
}
