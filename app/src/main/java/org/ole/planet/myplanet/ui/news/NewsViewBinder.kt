package org.ole.planet.myplanet.ui.news

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import io.realm.Realm
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.databinding.RowNewsBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Markdown.prependBaseUrlToImages
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.makeExpandable

class NewsViewBinder(
    private val realm: Realm,
    private val currentUser: RealmUserModel?,
    private val profileDbHandler: UserProfileDbHandler
) {
    fun configureUser(binding: RowNewsBinding, news: RealmNews): RealmUserModel? {
        val userModel = realm.where(RealmUserModel::class.java)
            .equalTo("id", news.userId)
            .findFirst()
        val userFullName = userModel?.getFullNameWithMiddleName()?.trim()
        if (userModel != null && currentUser != null) {
            binding.tvName.text =
                if (userFullName.isNullOrEmpty()) news.userName else userFullName
            Utilities.loadImage(userModel.userImage, binding.imgUser)
        } else {
            binding.tvName.text = news.userName
            Utilities.loadImage(null, binding.imgUser)
        }
        return userModel
    }

    fun setMessageAndDate(binding: RowNewsBinding, news: RealmNews, sharedTeamName: String, teamName: String) {
        val markdownContentWithLocalPaths = prependBaseUrlToImages(
            news.message,
            "file://" + MainApplication.context.getExternalFilesDir(null) + "/ole/",
            600,
            350
        )
        setMarkdownText(binding.tvMessage, markdownContentWithLocalPaths)
        val fulltext = binding.tvMessage.text
        binding.tvMessage.makeExpandable(
            fullText = fulltext,
            collapsedMaxLines = 6
        )
        binding.tvDate.text =
            if (sharedTeamName.isEmpty() || teamName.isNotEmpty()) {
                formatDate(news.time)
            } else {
                "${'$'}{formatDate(news.time)} | Shared from ${'$'}sharedTeamName"
            }
        binding.tvEdited.visibility = if (news.isEdited) View.VISIBLE else View.GONE
    }

    fun setMemberClickListeners(binding: RowNewsBinding, userModel: RealmUserModel?, currentLeader: RealmUserModel?, fromLogin: Boolean) {
        if (!fromLogin) {
            binding.imgUser.setOnClickListener {
                val activity = it.context as AppCompatActivity
                val model = userModel ?: currentLeader
                NewsActions.showMemberDetails(activity, model, profileDbHandler)
            }
            binding.tvName.setOnClickListener {
                val activity = it.context as AppCompatActivity
                val model = userModel ?: currentLeader
                NewsActions.showMemberDetails(activity, model, profileDbHandler)
            }
        }
    }
}

