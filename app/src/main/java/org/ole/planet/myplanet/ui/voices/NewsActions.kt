package org.ole.planet.myplanet.ui.voices

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository

object NewsActions {
    fun showEditAlert(
        context: Context,
        voicesRepository: VoicesRepository,
        teamsRepository: TeamsRepository,
        userRepository: UserRepository,
        newsId: String?,
        isEdit: Boolean,
        currentUser: RealmUserModel?,
        listener: (NewsViewData) -> Unit
    ) {
        val v = LayoutInflater.from(context).inflate(R.layout.alert_input, null)
        val et = v.findViewById<EditText>(R.id.et_input)
        val builder = AlertDialog.Builder(context)
        CoroutineScope(Dispatchers.Main).launch {
            if (isEdit) {
                builder.setTitle(R.string.edit_post)
                val news = newsId?.let { voicesRepository.getNewsById(it) }
                et.setText(news?.message)
            } else {
                builder.setTitle(R.string.add_reply)
            }
            builder.setView(v)
                .setPositiveButton(R.string.button_submit) { _: DialogInterface?, _: Int ->
                    val s = et.text.toString()
                    if (s.isEmpty()) {
                        return@setPositiveButton
                    }
                    CoroutineScope(Dispatchers.IO).launch {
                        if (isEdit) {
                            newsId?.let { voicesRepository.updateNews(it, s) }
                            val updatedNews = newsId?.let { voicesRepository.getNewsById(it) }
                            if (updatedNews != null) {
                                val newsViewData = NewsMapper.toNewsViewData(updatedNews, context, currentUser, teamsRepository, userRepository, voicesRepository)
                                withContext(Dispatchers.Main) {
                                    listener(newsViewData)
                                }
                            }
                        } else {
                            val newReply = newsId?.let { voicesRepository.createReply(it, s, currentUser) }
                            if (newReply != null) {
                                val newsViewData = NewsMapper.toNewsViewData(newReply, context, currentUser, teamsRepository, userRepository, voicesRepository)
                                withContext(Dispatchers.Main) {
                                    listener(newsViewData)
                                }
                            }
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null).show()
        }
    }
}
