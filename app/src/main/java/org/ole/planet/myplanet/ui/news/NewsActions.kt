package org.ole.planet.myplanet.ui.news

import android.content.Context
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.JsonArray
import io.realm.Realm
import io.realm.RealmList
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNews.Companion.createNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.team.teamMember.MemberDetailFragment

object NewsActions {
    data class EditDialogComponents(
        val view: android.view.View,
        val editText: EditText,
        val inputLayout: com.google.android.material.textfield.TextInputLayout,
        val imageLayout: LinearLayout
    )

    fun createEditDialogComponents(
        context: Context,
        listener: AdapterNews.OnNewsItemClickListener?
    ): EditDialogComponents {
        val v = android.view.LayoutInflater.from(context).inflate(R.layout.alert_input, null)
        val tlInput = v.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tl_input)
        val et = v.findViewById<EditText>(R.id.et_input)
        val llImage = v.findViewById<LinearLayout>(R.id.ll_alert_image)
        v.findViewById<android.view.View>(R.id.add_news_image).setOnClickListener { listener?.addImage(llImage) }
        return EditDialogComponents(v, et, tlInput, llImage)
    }

    fun handlePositiveButton(
        dialog: AlertDialog,
        isEdit: Boolean,
        components: EditDialogComponents,
        news: RealmNews?,
        realm: Realm,
        currentUser: RealmUserModel?,
        imageList: RealmList<String>?,
        listener: AdapterNews.OnNewsItemClickListener?
    ) {
        val s = components.editText.text.toString().trim()
        if (s.isEmpty()) {
            components.inputLayout.error = dialog.context.getString(R.string.please_enter_message)
            return
        }
        if (isEdit) {
            editPost(realm, s, news)
        } else {
            postReply(realm, s, news, currentUser, imageList)
        }
        dialog.dismiss()
        listener?.clearImages()
        listener?.onDataChanged()
    }

    fun showEditAlert(
        context: Context,
        realm: Realm,
        id: String?,
        isEdit: Boolean,
        currentUser: RealmUserModel?,
        listener: AdapterNews.OnNewsItemClickListener?,
        viewHolder: RecyclerView.ViewHolder,
        updateReplyButton: (RecyclerView.ViewHolder, RealmNews?, Int) -> Unit = { _, _, _ -> }
    ) {
        val components = createEditDialogComponents(context, listener)
        val message = components.view.findViewById<TextView>(R.id.cust_msg)
        message.text = context.getString(if (isEdit) R.string.edit_post else R.string.reply)
        val icon = components.view.findViewById<ImageView>(R.id.alert_icon)
        icon.setImageResource(R.drawable.ic_edit)

        val news = realm.where(RealmNews::class.java).equalTo("id", id).findFirst()
        if (isEdit) {
            components.editText.setText(context.getString(R.string.message_placeholder, news?.message))
        }
        val dialog = AlertDialog.Builder(context, R.style.ReplyAlertDialog)
            .setView(components.view)
            .setPositiveButton(R.string.button_submit, null)
            .setNegativeButton(R.string.cancel) { d, _ -> d.dismiss() }
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val currentImageList = listener?.getCurrentImageList()
            handlePositiveButton(dialog, isEdit, components, news, realm, currentUser, currentImageList, listener)
            updateReplyButton(viewHolder,news,viewHolder.bindingAdapterPosition)
        }
    }

    private fun postReply(
        realm: Realm,
        s: String?,
        news: RealmNews?,
        currentUser: RealmUserModel?,
        imageList: RealmList<String>?
    ) {
        val shouldCommit = !realm.isInTransaction
        if (shouldCommit) realm.beginTransaction()
        val map = HashMap<String?, String>()
        map["message"] = s ?: ""
        map["viewableBy"] = news?.viewableBy ?: ""
        map["viewableId"] = news?.viewableId ?: ""
        map["replyTo"] = news?.id ?: ""
        map["messageType"] = news?.messageType ?: ""
        map["messagePlanetCode"] = news?.messagePlanetCode ?: ""
        map["viewIn"] = news?.viewIn ?: ""
        currentUser?.let { createNews(map, realm, it, imageList, true) }
        if (shouldCommit) realm.commitTransaction()
    }

    private fun editPost(realm: Realm, s: String, news: RealmNews?) {
        if (s.isEmpty()) return
        if (!realm.isInTransaction) realm.beginTransaction()
        news?.updateMessage(s)
        realm.commitTransaction()
    }

    fun showMemberDetails(
        userModel: RealmUserModel?,
        profileDbHandler: UserProfileDbHandler
    ): MemberDetailFragment? {
        if (userModel == null) return null
        val userName = "${userModel.firstName} ${userModel.lastName}".trim().ifBlank { userModel.name }
        val fragment = MemberDetailFragment.newInstance(
            userName.toString(),
            userModel.email.toString(),
            userModel.dob.toString().substringBefore("T"),
            userModel.language.toString(),
            userModel.phoneNumber.toString(),
            profileDbHandler.getOfflineVisits(userModel).toString(),
            profileDbHandler.getLastVisit(userModel),
            "${userModel.firstName} ${userModel.lastName}",
            userModel.level.toString(),
            userModel.userImage
        )
        return fragment
    }

    fun deletePost(
        context: Context,
        realm: Realm,
        news: RealmNews?,
        list: MutableList<RealmNews?>,
        teamName: String,
        listener: AdapterNews.OnNewsItemClickListener? = null
    ) {
        val ar = Gson().fromJson(news?.viewIn, JsonArray::class.java)
        if (!realm.isInTransaction) realm.beginTransaction()
        val position = list.indexOf(news)
        if (position != -1) {
            list.removeAt(position)
        }
        if (teamName.isNotEmpty() || ar.size() < 2) {
            news?.let { newsItem ->
                deleteChildPosts(realm, newsItem.id, list)

                val managedNews = if (newsItem.isManaged) {
                    newsItem
                } else {
                    realm.where(RealmNews::class.java)
                        .equalTo("id", newsItem.id)
                        .findFirst()
                }
                
                managedNews?.deleteFromRealm()
            }
        } else {
            news?.let { newsItem ->
                val filtered = JsonArray().apply {
                    ar.forEach { elem ->
                        if (!elem.asJsonObject.has("sharedDate")) {
                            add(elem)
                        }
                    }
                }
                
                val managedNews = if (newsItem.isManaged) {
                    newsItem
                } else {
                    realm.where(RealmNews::class.java)
                        .equalTo("id", newsItem.id)
                        .findFirst()
                }
                
                managedNews?.viewIn = Gson().toJson(filtered)
            }
        }
        realm.commitTransaction()
        listener?.onDataChanged()
    }

    private fun deleteChildPosts(
        realm: Realm,
        parentId: String?,
        list: MutableList<RealmNews?>
    ) {
        if (parentId == null) return
        val children = realm.where(RealmNews::class.java)
            .equalTo("replyTo", parentId)
            .findAll()
        children.forEach { child ->
            deleteChildPosts(realm, child.id, list)
            val idx = list.indexOf(child)
            if (idx != -1) list.removeAt(idx)
            child.deleteFromRealm()
        }
    }
}
