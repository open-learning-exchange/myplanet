package org.ole.planet.myplanet.ui.chat

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    companion object {
        const val RESPONSE_SOURCE_SHARED_VIEW_MODEL = 1
        const val RESPONSE_SOURCE_NETWORK = 2
        const val RESPONSE_SOURCE_UNKNOWN = 0
    }
    class QueryViewHolder(private val textUserMessageBinding: ItemUserMessageBinding) : RecyclerView.ViewHolder(textUserMessageBinding.root) {
        fun bind(query: String) {
            textUserMessageBinding.textGchatMessageMe.text = query
        }
    }

    class ResponseViewHolder(private val textAiMessageBinding: ItemAiResponseMessageBinding, val context: Context) : RecyclerView.ViewHolder(textAiMessageBinding.root) {
        fun bind(response: String, responseSource: Int) {
            if(responseSource == RESPONSE_SOURCE_NETWORK){
                val typingDelayMillis = 10L
                val typingAnimationDurationMillis = response.length * typingDelayMillis
                textAiMessageBinding.textGchatMessageOther.text = context.getString(R.string.empty_text)
                Handler(Looper.getMainLooper()).postDelayed({
                    animateTyping(response)
                }, typingAnimationDurationMillis)
            } else if(responseSource == RESPONSE_SOURCE_SHARED_VIEW_MODEL){
                if (response.isNotEmpty()) {
                    textAiMessageBinding.textGchatMessageOther.text = response
                } else{
                    textAiMessageBinding.textGchatMessageOther.visibility = View.GONE
                }
            }
        }

        private fun animateTyping(response: String) {
            var currentIndex = 0
            val typingRunnable = object : Runnable {
                override fun run() {
                    if (currentIndex < response.length) {
                        textAiMessageBinding.textGchatMessageOther.text = response.substring(0, currentIndex + 1)
                        currentIndex++
                        Handler(Looper.getMainLooper()).postDelayed(this, 10L)
                    }
                }
            }
            Handler(Looper.getMainLooper()).postDelayed(typingRunnable, 10L)
        }
    }

    fun addQuery(query: String) {
        Log.d("ChatAdapter", "addQuery: $query")
        chatList.add(query)
        notifyItemInserted(chatList.size - 1)
        scrollToLastItem()
    }

    fun addResponse(response: String) {
        Log.d("ChatAdapter", "addResponse: $response")
        chatList.add(response)
        notifyItemInserted(chatList.size - 1)
        scrollToLastItem()
    }

    fun clearData() {
        val size = chatList.size
        chatList.clear()
        notifyItemRangeRemoved(0, size)
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
                QueryViewHolder(textUserMessageBinding)
            }
            viewTypeResponse -> {
                textAiMessageBinding = ItemAiResponseMessageBinding.inflate(LayoutInflater.from(context), parent, false)
                ResponseViewHolder(textAiMessageBinding, context)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val chatItem = chatList[position]
        Log.d("ChatAdapter", "chatList: $chatList")
        when (holder.itemViewType) {
            viewTypeQuery -> {
                val queryViewHolder = holder as QueryViewHolder
                queryViewHolder.bind(chatItem)
            }
            viewTypeResponse -> {
                val responseViewHolder = holder as ResponseViewHolder
                responseViewHolder.bind(chatItem, responseSource)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun getItemCount(): Int {
        return chatList.size
    }
}