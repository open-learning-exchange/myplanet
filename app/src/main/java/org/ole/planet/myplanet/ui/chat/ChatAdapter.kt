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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemAiResponseMessageBinding
import org.ole.planet.myplanet.databinding.ItemUserMessageBinding
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.Utilities

class ChatAdapter(val context: Context, private val recyclerView: RecyclerView) :
    ListAdapter<String, RecyclerView.ViewHolder>(
        DiffUtils.itemCallback(
            { old, new -> old == new },
            { old, new -> old == new }
        )
    ) {
    var responseSource: Int = RESPONSE_SOURCE_UNKNOWN
    private val viewTypeQuery = 1
    private val viewTypeResponse = 2
    val animatedMessages = HashMap<Int, Boolean>()
    var lastAnimatedPosition: Int = -1
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val messages = mutableListOf<String>()

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
            textAiMessageBinding.textGchatMessageOther.visibility = View.VISIBLE
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
        Utilities.toast(
            context,
            context.getString(R.string.copied_to_clipboard),
            android.widget.Toast.LENGTH_SHORT
        )
    }

    fun addQuery(query: String) {
        messages.add(query)
        submitList(messages.toList()) {
            scrollToLastItem()
        }
    }

    fun addResponse(response: String) {
        messages.add(response)
        lastAnimatedPosition = messages.size - 1
        submitList(messages.toList()) {
            scrollToLastItem()
        }
    }

    fun clearData() {
        messages.clear()
        animatedMessages.clear()
        lastAnimatedPosition = -1
        submitList(emptyList())
    }

    private fun scrollToLastItem() {
        val lastPosition = messages.size - 1
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
                val binding = ItemUserMessageBinding.inflate(LayoutInflater.from(context), parent, false)
                QueryViewHolder(binding, this::copyToClipboard)
            }
            viewTypeResponse -> {
                val binding = ItemAiResponseMessageBinding.inflate(LayoutInflater.from(context), parent, false)
                ResponseViewHolder(binding, this::copyToClipboard, context, recyclerView, coroutineScope)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val chatItem = getItem(position)
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

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        coroutineScope.cancel()
    }

    companion object {
        const val RESPONSE_SOURCE_SHARED_VIEW_MODEL = 1
        const val RESPONSE_SOURCE_NETWORK = 2
        const val RESPONSE_SOURCE_UNKNOWN = 0
    }
}
