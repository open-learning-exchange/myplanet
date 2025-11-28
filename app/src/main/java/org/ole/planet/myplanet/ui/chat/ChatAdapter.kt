package org.ole.planet.myplanet.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemAiResponseMessageBinding
import org.ole.planet.myplanet.databinding.ItemUserMessageBinding
import org.ole.planet.myplanet.model.ChatMessage
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.Utilities

class ChatAdapter(val context: Context, private val recyclerView: RecyclerView, private val scope: CoroutineScope?) :
    ListAdapter<ChatMessage, RecyclerView.ViewHolder>(
        DiffUtils.itemCallback(
            { old, new -> old == new },
            { old, new -> old == new }
        )
    ) {
    val animatedMessages = HashMap<Int, Boolean>()
    var lastAnimatedPosition: Int = -1

    interface OnChatItemClickListener {
        fun onChatItemClick(position: Int, chatItem: ChatMessage)
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
        private val coroutineScope: CoroutineScope?
    ) : RecyclerView.ViewHolder(textAiMessageBinding.root) {
        internal var animationJob: kotlinx.coroutines.Job? = null
        fun bind(response: String, responseSource: Int,  shouldAnimate: Boolean, markAnimated: () -> Unit) {
            textAiMessageBinding.textGchatMessageOther.visibility = View.VISIBLE
            animationJob?.cancel()
            if (responseSource == ChatMessage.RESPONSE_SOURCE_NETWORK) {
                if (shouldAnimate && coroutineScope != null) {
                    textAiMessageBinding.textGchatMessageOther.text = context.getString(R.string.empty_text)
                    animationJob = coroutineScope.launch {
                        animateTyping(response, markAnimated)
                    }
                } else{
                    textAiMessageBinding.textGchatMessageOther.text = response
                }

            } else if (responseSource == ChatMessage.RESPONSE_SOURCE_SHARED_VIEW_MODEL) {
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
                if (!isActive) {
                    return
                }
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
        Utilities.toast(
            context,
            context.getString(R.string.copied_to_clipboard),
            android.widget.Toast.LENGTH_SHORT
        )
    }

    fun addQuery(query: String) {
        val currentList = currentList.toMutableList()
        currentList.add(ChatMessage(query, ChatMessage.QUERY))
        submitList(currentList) {
            scrollToLastItem()
        }
    }

    fun addResponse(response: String, source: Int) {
        val currentList = currentList.toMutableList()
        currentList.add(ChatMessage(response, ChatMessage.RESPONSE, source))
        lastAnimatedPosition = currentList.size - 1
        submitList(currentList) {
            scrollToLastItem()
        }
    }

    fun clearData() {
        animatedMessages.clear()
        lastAnimatedPosition = -1
        submitList(emptyList())
    }

    private fun scrollToLastItem() {
        val lastPosition = itemCount - 1
        if (lastPosition >= 0) {
            recyclerView.scrollToPosition(lastPosition)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return getItem(position).viewType
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ChatMessage.QUERY -> {
                val userMessageBinding = ItemUserMessageBinding.inflate(LayoutInflater.from(context), parent, false)
                QueryViewHolder(userMessageBinding, this::copyToClipboard)
            }
            ChatMessage.RESPONSE -> {
                val aiMessageBinding = ItemAiResponseMessageBinding.inflate(LayoutInflater.from(context), parent, false)
                ResponseViewHolder(aiMessageBinding, this::copyToClipboard, context, recyclerView, scope)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val chatItem = getItem(position)
        when (holder.itemViewType) {
            ChatMessage.QUERY -> {
                val queryViewHolder = holder as QueryViewHolder
                queryViewHolder.bind(chatItem.message)
            }
            ChatMessage.RESPONSE -> {
                val responseViewHolder = holder as ResponseViewHolder
                val shouldAnimate = (position == lastAnimatedPosition && !animatedMessages.containsKey(position))
                responseViewHolder.bind(chatItem.message, chatItem.source, shouldAnimate) {
                    animatedMessages[position] = true
                }
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
        holder.itemView.setOnClickListener {
            chatItemClickListener?.onChatItemClick(position, chatItem)
        }
    }
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is ResponseViewHolder) {
            holder.animationJob?.cancel()
        }
    }

    companion object {
        const val RESPONSE_SOURCE_SHARED_VIEW_MODEL = ChatMessage.RESPONSE_SOURCE_SHARED_VIEW_MODEL
        const val RESPONSE_SOURCE_NETWORK = ChatMessage.RESPONSE_SOURCE_NETWORK
        const val RESPONSE_SOURCE_UNKNOWN = ChatMessage.RESPONSE_SOURCE_UNKNOWN
    }
}
