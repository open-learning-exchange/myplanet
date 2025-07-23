package org.ole.planet.myplanet.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemAiResponseMessageBinding
import org.ole.planet.myplanet.databinding.ItemUserMessageBinding

class ChatAdapter(private val chatList: ArrayList<String>, val context: Context, private val recyclerView: RecyclerView) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private lateinit var textUserMessageBinding: ItemUserMessageBinding
    private lateinit var textAiMessageBinding: ItemAiResponseMessageBinding
    var responseSource: Int = RESPONSE_SOURCE_UNKNOWN
    private val viewTypeQuery = 1
    private val viewTypeResponse = 2
    val animatedMessages = HashMap<Int, Boolean>()
    var lastAnimatedPosition: Int = -1
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    interface OnChatItemClickListener {
        fun onChatItemClick(position: Int, chatItem: String)
    }

    private var chatItemClickListener: OnChatItemClickListener? = null

    fun setOnChatItemClickListener(listener: OnChatItemClickListener) {
        this.chatItemClickListener = listener
    }

    class QueryViewHolder(private val textUserMessageBinding: ItemUserMessageBinding, private val copyToClipboard: (String) -> Unit) : RecyclerView.ViewHolder(textUserMessageBinding.root) {
        fun bind(query: String) {
            textUserMessageBinding.textGchatMessageMe.text = query

            textUserMessageBinding.textGchatMessageMe.setOnLongClickListener {
                copyToClipboard(query)
                true
            }
        }
    }

    class ResponseViewHolder(
        private val textAiMessageBinding: ItemAiResponseMessageBinding,
        private val copyToClipboard: (String) -> Unit,
        val context: Context,
        private val recyclerView: RecyclerView,
        private val coroutineScope: CoroutineScope
    ) : RecyclerView.ViewHolder(textAiMessageBinding.root) {
        fun bind(response: String, responseSource: Int,  shouldAnimate: Boolean, markAnimated: () -> Unit) {
            if (responseSource == RESPONSE_SOURCE_NETWORK) {
                if (shouldAnimate) {
                    textAiMessageBinding.textGchatMessageOther.text = context.getString(R.string.empty_text)
                    coroutineScope.launch {
                        animateTyping(response, markAnimated)
                    }
                } else{
                    textAiMessageBinding.textGchatMessageOther.text = response
                }

            } else if (responseSource == RESPONSE_SOURCE_SHARED_VIEW_MODEL) {
                if (response.isNotEmpty()) {
                    textAiMessageBinding.textGchatMessageOther.text = response
                } else{
                    textAiMessageBinding.textGchatMessageOther.visibility = View.GONE
                }
            }
            textAiMessageBinding.textGchatMessageOther.setOnLongClickListener {
                copyToClipboard(response)
                true
            }
        }

        private suspend fun animateTyping(response: String, markAnimated: () -> Unit) {
            var currentIndex = 0
            while (currentIndex < response.length) {
                textAiMessageBinding.textGchatMessageOther.text = response.substring(0, currentIndex + 1)
                recyclerView.scrollToPosition(bindingAdapterPosition)
                currentIndex++
                delay(10L)
            }
            markAnimated()
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("copied Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    fun addQuery(query: String) {
        chatList.add(query)
        notifyItemInserted(chatList.size - 1)
        scrollToLastItem()
    }

    fun addResponse(response: String) {
        chatList.add(response)
        lastAnimatedPosition = chatList.size - 1
        notifyItemInserted(chatList.size - 1)
        scrollToLastItem()
    }

    fun clearData() {
        val size = chatList.size
        chatList.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun updateChatData(newChatList: List<String>) {
        val diffCallback = ChatDiffCallback()
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = chatList.size
            override fun getNewListSize(): Int = newChatList.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                diffCallback.areItemsTheSame(chatList[oldItemPosition], newChatList[newItemPosition], oldItemPosition, newItemPosition)
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                diffCallback.areContentsTheSame(chatList[oldItemPosition], newChatList[newItemPosition])
        })
        chatList.clear()
        chatList.addAll(newChatList)
        diffResult.dispatchUpdatesTo(this)
    }

    private fun scrollToLastItem() {
        val lastPosition = chatList.size - 1
        if (lastPosition >= 0) {
            recyclerView.scrollToPosition(lastPosition)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position % 2 == 0) viewTypeQuery else viewTypeResponse
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            viewTypeQuery -> {
                textUserMessageBinding = ItemUserMessageBinding.inflate(LayoutInflater.from(context), parent, false)
                QueryViewHolder(textUserMessageBinding, this::copyToClipboard)
            }
            viewTypeResponse -> {
                textAiMessageBinding = ItemAiResponseMessageBinding.inflate(LayoutInflater.from(context), parent, false)
                ResponseViewHolder(textAiMessageBinding, this::copyToClipboard, context, recyclerView,coroutineScope)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val chatItem = chatList[position]
        when (holder.itemViewType) {
            viewTypeQuery -> {
                val queryViewHolder = holder as QueryViewHolder
                queryViewHolder.bind(chatItem)
            }
            viewTypeResponse -> {
                val responseViewHolder = holder as ResponseViewHolder
                val shouldAnimate = (position == lastAnimatedPosition && !animatedMessages.containsKey(position))
                responseViewHolder.bind(chatItem,responseSource, shouldAnimate) {
                    animatedMessages[position] = true
                }
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
        holder.itemView.setOnClickListener {
            chatItemClickListener?.onChatItemClick(position, chatItem)
        }
    }

    override fun getItemCount(): Int {
        return chatList.size
    }

    private class ChatDiffCallback {
        fun areItemsTheSame(oldItem: String, newItem: String, oldPosition: Int, newPosition: Int): Boolean {
            // For chat, we consider items the same if they have the same content and position type (query/response)
            return oldItem == newItem && (oldPosition % 2 == newPosition % 2)
        }

        fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        const val RESPONSE_SOURCE_SHARED_VIEW_MODEL = 1
        const val RESPONSE_SOURCE_NETWORK = 2
        const val RESPONSE_SOURCE_UNKNOWN = 0
    }
}
