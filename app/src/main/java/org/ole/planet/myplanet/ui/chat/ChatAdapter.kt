package org.ole.planet.myplanet.ui.chat

import android.content.Context
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R

class ChatAdapter(private val chatList: ArrayList<History>, val context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val VIEW_TYPE_QUERY = 1
    private val VIEW_TYPE_RESPONSE = 2

    class QueryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val queryTextView: TextView = itemView.findViewById(R.id.text_gchat_message_me)
        fun bind(query: String) {
            queryTextView.text = query
        }
    }

    class ResponseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val responseTextView: TextView = itemView.findViewById(R.id.text_gchat_message_other)
        fun bind(response: String) {
            val typingDelayMillis = 10L
            val typingAnimationDurationMillis = response.length * typingDelayMillis

            responseTextView.text = ""
            Handler().postDelayed({
                animateTyping(response, typingDelayMillis)
            }, typingAnimationDurationMillis)
        }

        private fun animateTyping(response: String, typingDelayMillis: Long) {
            var currentIndex = 0

            val typingRunnable = object : Runnable {
                override fun run() {
                    if (currentIndex < response.length) {
                        responseTextView.text = response.substring(0, currentIndex + 1)
                        currentIndex++
                        Handler().postDelayed(this, typingDelayMillis)
                    }
                }
            }

            Handler().postDelayed(typingRunnable, typingDelayMillis)
        }
    }

    fun addQuery(query: String) {
        chatList.add(History(query, null))
        notifyItemInserted(chatList.size - 1)
    }

    fun addResponse(response: String) {
        chatList.add(History(null, response))
        notifyItemInserted(chatList.size - 1)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position % 2 == 0) VIEW_TYPE_QUERY else VIEW_TYPE_RESPONSE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_QUERY -> {
                val queryView = LayoutInflater.from(parent.context).inflate(R.layout.item_user_message, parent, false)
                QueryViewHolder(queryView)
            }
            VIEW_TYPE_RESPONSE -> {
                val responseView = LayoutInflater.from(parent.context).inflate(R.layout.item_ai_response_message, parent, false)
                ResponseViewHolder(responseView)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val chatItem = chatList[position]
        when (holder.itemViewType) {
            VIEW_TYPE_QUERY -> {
                val queryViewHolder = holder as QueryViewHolder
                chatItem.query?.let { queryViewHolder.bind(it) }
            }
            VIEW_TYPE_RESPONSE -> {
                val responseViewHolder = holder as ResponseViewHolder
                chatItem.response?.let { responseViewHolder.bind(it) }
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun getItemCount(): Int {
        return chatList.size
    }
}