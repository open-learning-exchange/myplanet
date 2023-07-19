package org.ole.planet.myplanet.ui.chat

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R

class ChatFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var sendMessage: ImageView
    private lateinit var chatMessage: EditText
    private lateinit var imageLoading: ImageView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_chat, container, false)
        sendMessage = rootView.findViewById(R.id.button_gchat_send)
        chatMessage = rootView.findViewById(R.id.edit_gchat_message)
        recyclerView = rootView.findViewById(R.id.recycler_gchat)
        imageLoading = rootView.findViewById(R.id.image_gchat_loading)

        sendMessage.setOnClickListener {

        }

        return rootView
    }
}