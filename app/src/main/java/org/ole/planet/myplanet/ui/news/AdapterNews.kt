package org.ole.planet.myplanet.ui.news

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fisk.chipcloud.ChipCloud
import fisk.chipcloud.ChipCloudConfig
import io.realm.Case
import io.realm.Realm
import io.realm.RealmList
import io.realm.Sort
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNewsBinding
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNews.Companion.createNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.chat.ChatAdapter
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.showBetaFeature
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities
import java.io.File
import java.util.Calendar
import androidx.core.graphics.drawable.toDrawable

class AdapterNews(var context: Context, private val list: MutableList<RealmNews?>, private var currentUser: RealmUserModel?, private val parentNews: RealmNews?, private val teamName: String, private val showTeamName: Boolean) : RecyclerView.Adapter<RecyclerView.ViewHolder?>() {

    private lateinit var rowNewsBinding: RowNewsBinding
    private var listener: OnNewsItemClickListener? = null
    private var imageList: RealmList<String>? = null
    lateinit var mRealm: Realm
    private var fromLogin = false
    private var nonTeamMember = false
    private var sharedPreferences: SharedPrefManager? = null
    private var recyclerView: RecyclerView? = null
    var user: RealmUserModel? = null
    private var currentZoomDialog: Dialog? = null

    fun setImageList(imageList: RealmList<String>?) {
        this.imageList = imageList
    }

    fun addItem(news: RealmNews?) {
        list.add(news)
        notifyDataSetChanged()
    }

    fun setFromLogin(fromLogin: Boolean) {
        this.fromLogin = fromLogin
    }

    fun setNonTeamMember(nonTeamMember: Boolean) {
        this.nonTeamMember = nonTeamMember
    }

    fun setListener(listener: OnNewsItemClickListener?) {
        this.listener = listener
    }

    fun setmRealm(mRealm: Realm?) {
        if (mRealm != null) {
            this.mRealm = mRealm
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        rowNewsBinding = RowNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        sharedPreferences = SharedPrefManager(context)
        user = UserProfileDbHandler(context).userModel
        return ViewHolderNews(rowNewsBinding)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderNews) {
            holder.bind(position)
            val news = getNews(holder, position)
            var teamName = ""
            val viewInJson = news?.viewIn
            if (!viewInJson.isNullOrEmpty()) {
                try {
                    val jsonArray = Gson().fromJson(viewInJson, JsonArray::class.java)
                    for (jsonElement in jsonArray) {
                        val jsonObject = jsonElement.asJsonObject
                        if (jsonObject.has("teamName")) {
                            teamName = jsonObject.get("teamName").asString
                            break
                        }
                    }
                } catch (e: Exception) {
                }
            }
            val prefix = context.getString(R.string.shared_from_prefix)
            val fullText = "$prefix $teamName"
            holder.rowNewsBinding.tvTeamName.text = if (showTeamName && teamName.isNotBlank()) fullText else ""

            if (news?.isValid == true) {
                holder.rowNewsBinding.tvName.text = ""
                holder.rowNewsBinding.imgUser.setImageResource(0)
                holder.rowNewsBinding.llEditDelete.visibility = View.GONE
                holder.rowNewsBinding.linearLayout51.visibility = View.VISIBLE
                holder.rowNewsBinding.tvMessage.text = ""
                holder.rowNewsBinding.tvDate.text = ""
                holder.rowNewsBinding.imgDelete.setOnClickListener(null)
                holder.rowNewsBinding.imgEdit.setOnClickListener(null)
                holder.rowNewsBinding.btnAddLabel.visibility = View.GONE
                holder.rowNewsBinding.imgEdit.visibility = View.GONE
                holder.rowNewsBinding.imgDelete.visibility = View.GONE
                holder.rowNewsBinding.btnReply.visibility = View.GONE
                holder.rowNewsBinding.imgNews.visibility = View.GONE
                holder.rowNewsBinding.recyclerGchat.visibility = View.GONE
                holder.rowNewsBinding.sharedChat.visibility = View.GONE

                val userModel = mRealm.where(RealmUserModel::class.java).equalTo("id", news.userId).findFirst()
                val userFullName = userModel?.getFullNameWithMiddleName()?.trim()
                if (userModel != null && currentUser != null) {
                    if(userFullName.isNullOrEmpty()){
                        holder.rowNewsBinding.tvName.text = news.userName
                    } else {
                        holder.rowNewsBinding.tvName.text = userFullName
                    }
                    Utilities.loadImage(userModel.userImage, holder.rowNewsBinding.imgUser)
                    showHideButtons(userModel, holder)
                } else {
                    holder.rowNewsBinding.tvName.text = news.userName
                }
                showShareButton(holder, news)
                if ("${news.messageWithoutMarkdown}" != "</br>") {
                    holder.rowNewsBinding.tvMessage.text = news.messageWithoutMarkdown
                } else {
                    holder.rowNewsBinding.linearLayout51.visibility = View.GONE
                }
                holder.rowNewsBinding.tvDate.text = formatDate(news.time)
                if (news.isEdited) {
                    holder.rowNewsBinding.tvEdited.visibility = View.VISIBLE
                } else {
                    holder.rowNewsBinding.tvEdited.visibility = View.GONE
                }
                if (news.userId == currentUser?._id) {
                    holder.rowNewsBinding.imgDelete.setOnClickListener {
                        AlertDialog.Builder(context)
                            .setMessage(R.string.delete_record)
                            .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                                deletePost(news, context)
                            }.setNegativeButton(R.string.cancel, null).show()
                    }
                    holder.rowNewsBinding.imgEdit.setOnClickListener {
                        showEditAlert(news.id, true)
                    }
                    holder.rowNewsBinding.btnAddLabel.visibility = if (fromLogin || nonTeamMember) View.GONE else View.VISIBLE
                } else {
                    holder.rowNewsBinding.imgEdit.visibility = View.GONE
                    holder.rowNewsBinding.imgDelete.visibility = View.GONE
                    holder.rowNewsBinding.btnAddLabel.visibility = View.GONE
                }
                holder.rowNewsBinding.llEditDelete.visibility = if (fromLogin || nonTeamMember) View.GONE else View.VISIBLE
                holder.rowNewsBinding.btnReply.visibility = if (fromLogin || nonTeamMember) View.GONE else View.VISIBLE
                loadImage(holder, news)
                showReplyButton(holder, news, position)
                addLabels(holder, news)
                showChips(holder, news)

                if (news.newsId?.isNotEmpty() == true) {
                    val conversations = Gson().fromJson(news.conversations, Array<Conversation>::class.java).toList()
                    val chatAdapter = ChatAdapter(ArrayList(), context, holder.rowNewsBinding.recyclerGchat)

                    if (user?.id?.startsWith("guest") == false) {
                        chatAdapter.setOnChatItemClickListener(object : ChatAdapter.OnChatItemClickListener {
                            override fun onChatItemClick(position: Int, chatItem: String) {
                                listener?.onNewsItemClick(news)
                            }
                        })
                    }

                    for (conversation in conversations) {
                        val query = conversation.query
                        val response = conversation.response
                        if (query != null) {
                            chatAdapter.addQuery(query)
                        }
                        chatAdapter.responseSource = ChatAdapter.RESPONSE_SOURCE_SHARED_VIEW_MODEL
                        if (response != null) {
                            chatAdapter.addResponse(response)
                        }
                    }

                    holder.rowNewsBinding.recyclerGchat.adapter = chatAdapter
                    holder.rowNewsBinding.recyclerGchat.layoutManager = LinearLayoutManager(context)
                    holder.rowNewsBinding.recyclerGchat.visibility = View.VISIBLE
                    holder.rowNewsBinding.sharedChat.visibility = View.VISIBLE
                } else {
                    holder.rowNewsBinding.recyclerGchat.visibility = View.GONE
                    holder.rowNewsBinding.sharedChat.visibility = View.GONE
                }
            }
        }
    }

    private fun addLabels(holder: RecyclerView.ViewHolder, news: RealmNews?) {
        val viewHolder = holder as ViewHolderNews
        viewHolder.rowNewsBinding.btnAddLabel.setOnClickListener {
            val wrapper = ContextThemeWrapper(context, R.style.CustomPopupMenu)
            val menu = PopupMenu(wrapper, viewHolder.rowNewsBinding.btnAddLabel)
            val inflater = menu.menuInflater
            inflater.inflate(R.menu.menu_add_label, menu.menu)
            menu.setOnMenuItemClickListener { menuItem: MenuItem ->
                if (!mRealm.isInTransaction) {
                    mRealm.beginTransaction()
                }
                news?.addLabel(Constants.LABELS["${menuItem.title}"])
                Utilities.toast(context, context.getString(R.string.label_added))
                mRealm.commitTransaction()
                news?.let { it1 -> showChips(holder, it1) }
                false
            }
            menu.show()
        }
    }

    private fun showChips(holder: RecyclerView.ViewHolder, news: RealmNews) {
        val viewHolder = holder as ViewHolderNews
        val isOwner = (news.userId == currentUser?.id)
        viewHolder.rowNewsBinding.fbChips.removeAllViews()

        for (label in news.labels ?: emptyList()) {
            val chipConfig = Utilities.getCloudConfig().apply {
                selectMode(if (isOwner) ChipCloud.SelectMode.close else ChipCloud.SelectMode.none)
            }

            val chipCloud = ChipCloud(context, viewHolder.rowNewsBinding.fbChips, chipConfig)
            chipCloud.addChip(getLabel(label))

            if (isOwner) {
                chipCloud.setDeleteListener { _: Int, labelText: String? ->

                    if (!mRealm.isInTransaction) {
                        mRealm.beginTransaction()
                    }

                    news.labels?.remove(Constants.LABELS[labelText])
                    mRealm.commitTransaction()

                    viewHolder.rowNewsBinding.btnAddLabel.isEnabled = (news.labels?.size ?: 0) < 3
                }
            }
        }
        viewHolder.rowNewsBinding.btnAddLabel.isEnabled = (news.labels?.size ?: 0) < 3
    }

    private fun loadImage(holder: RecyclerView.ViewHolder, news: RealmNews?) {
        val viewHolder = holder as ViewHolderNews
        val imageUrls = news?.imageUrls
        if (imageUrls != null && imageUrls.isNotEmpty()) {
            try {
                val imgObject = Gson().fromJson(imageUrls[0], JsonObject::class.java)
                viewHolder.rowNewsBinding.imgNews.visibility = View.VISIBLE
                Glide.with(context).load(File(getString("imageUrl", imgObject)))
                    .into(viewHolder.rowNewsBinding.imgNews)

                viewHolder.rowNewsBinding.imgNews.setOnClickListener {
                    showZoomableImage(it.context, getString("imageUrl", imgObject))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            loadRemoteImage(holder, news)
        }
    }

    private fun loadRemoteImage(holder: RecyclerView.ViewHolder, news: RealmNews?) {
        val viewHolder = holder as ViewHolderNews
        news?.imagesArray?.let { imagesArray ->
            if (imagesArray.size() > 0) {
                val ob = imagesArray[0]?.asJsonObject
                getString("resourceId", ob).let { resourceId ->
                    mRealm.where(RealmMyLibrary::class.java).equalTo("_id", resourceId).findFirst()?.let { library ->
                        context.getExternalFilesDir(null)?.let { basePath ->
                            val imageFile = File(basePath, "ole/${library.id}/${library.resourceLocalAddress}")
                            if (imageFile.exists()) {
                                Glide.with(context)
                                    .load(imageFile)
                                    .into(viewHolder.rowNewsBinding.imgNews)
                                viewHolder.rowNewsBinding.imgNews.visibility = View.VISIBLE

                                viewHolder.rowNewsBinding.imgNews.setOnClickListener {
                                    showZoomableImage(it.context, imageFile.toString())
                                }
                                return
                            }
                        }
                    }
                }
            }
        }
        viewHolder.rowNewsBinding.imgNews.visibility = View.GONE
    }

    private fun showZoomableImage(context: Context, imageUrl: String) {
        currentZoomDialog?.dismiss()

        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        currentZoomDialog = dialog

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_zoomable_image, null)
        val photoView = view.findViewById<PhotoView>(R.id.photoView)
        val closeButton = view.findViewById<ImageView>(R.id.closeButton)

        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(Color.BLACK.toDrawable())

        Glide.with(context)
            .load(imageUrl)
            .error(R.drawable.ic_loading)
            .into(photoView)

        closeButton.setOnClickListener {
            dialog.dismiss()
            currentZoomDialog = null
        }

        dialog.setOnDismissListener {
            currentZoomDialog = null
        }

        dialog.show()
    }

    private fun showReplyButton(holder: RecyclerView.ViewHolder, finalNews: RealmNews?, position: Int) {
        val viewHolder = holder as ViewHolderNews
        if (listener == null || fromLogin) {
            viewHolder.rowNewsBinding.btnShowReply.visibility = View.GONE
        }
        viewHolder.rowNewsBinding.btnReply.setOnClickListener { showEditAlert(finalNews?.id, false) }
        val replies: List<RealmNews> = mRealm.where(RealmNews::class.java).sort("time", Sort.DESCENDING).equalTo("replyTo", finalNews?.id, Case.INSENSITIVE).findAll()
        viewHolder.rowNewsBinding.btnShowReply.text = String.format(context.getString(R.string.show_replies) + " (%d)", replies.size)
        viewHolder.rowNewsBinding.btnShowReply.setTextColor(context.getColor(R.color.daynight_textColor))
        viewHolder.rowNewsBinding.btnShowReply.visibility = if (replies.isNotEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        if (position == 0 && parentNews != null) {
            viewHolder.rowNewsBinding.btnShowReply.visibility = View.GONE
        }
        viewHolder.rowNewsBinding.btnShowReply.setOnClickListener {
            sharedPreferences?.setRepliedNewsId(finalNews?.id)
            listener?.showReply(finalNews, fromLogin, nonTeamMember)
        }
    }

    private fun showEditAlert(id: String?, isEdit: Boolean) {
        val v = LayoutInflater.from(context).inflate(R.layout.alert_input, null)
        val tlInput = v.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tl_input)
        val et = v.findViewById<EditText>(R.id.et_input)
        v.findViewById<View>(R.id.ll_image).visibility = if (showBetaFeature(Constants.KEY_NEWSADDIMAGE, context)) View.VISIBLE else View.GONE
        val llImage = v.findViewById<LinearLayout>(R.id.ll_alert_image)
        v.findViewById<View>(R.id.add_news_image).setOnClickListener { listener?.addImage(llImage) }
        val message = v.findViewById<TextView>(R.id.cust_msg)
        message.text = context.getString(if (isEdit) R.string.edit_post else R.string.reply)
        val icon = v.findViewById<ImageView>(R.id.alert_icon)
        icon.setImageResource(R.drawable.ic_edit)

        val news = mRealm.where(RealmNews::class.java).equalTo("id", id).findFirst()
        if (isEdit) et.setText(context.getString(R.string.message_placeholder, news?.message))
        val dialog = AlertDialog.Builder(context, R.style.ReplyAlertDialog)
            .setView(v)
            .setPositiveButton(R.string.button_submit, null)
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                listener?.clearImages()
                dialog.dismiss()
            }
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            val s = et.text.toString().trim()
            if (s.isEmpty()) {
                tlInput.error = context.getString(R.string.please_enter_message)
                return@setOnClickListener
            }
            if (isEdit) {
                editPost(s, news)
            } else {
                postReply(s, news)
            }
            dialog.dismiss()
        }
    }

    private fun postReply(s: String?, news: RealmNews?) {
        if (!mRealm.isInTransaction) mRealm.beginTransaction()
        val map = HashMap<String?, String>()
        map["message"] = s ?: ""
        map["viewableBy"] = news?.viewableBy ?: ""
        map["viewableId"] = news?.viewableId ?: ""
        map["replyTo"] = news?.id ?: ""
        map["messageType"] = news?.messageType ?: ""
        map["messagePlanetCode"] = news?.messagePlanetCode ?: ""

        currentUser?.let { createNews(map, mRealm, it, imageList) }
        notifyDataSetChanged()
        listener?.clearImages()
    }

    private fun editPost(s: String, news: RealmNews?) {
        if (s.isEmpty()) {
            Utilities.toast(context, context.getString(R.string.please_enter_message))
            return
        }
        if (!mRealm.isInTransaction) mRealm.beginTransaction()
        news?.updateMessage(s)
        mRealm.commitTransaction()
        notifyDataSetChanged()
        listener?.clearImages()
    }

    private fun getNews(holder: RecyclerView.ViewHolder, position: Int): RealmNews? {
        val news: RealmNews? = if (parentNews != null) {
            if (position == 0) {
                (holder.itemView as CardView).setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_blue_50))
                parentNews
            } else {
                (holder.itemView as CardView).setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_white_1000))
                list[position - 1]
            }
        } else {
            (holder.itemView as CardView).setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_white_1000))
            list[position]
        }
        return news
    }

    private fun showHideButtons(userModel: RealmUserModel, holder: RecyclerView.ViewHolder) {
        val viewHolder = holder as ViewHolderNews
        if (currentUser?.id == userModel.id && !fromLogin && !nonTeamMember) {
            viewHolder.rowNewsBinding.llEditDelete.visibility = View.VISIBLE
            viewHolder.rowNewsBinding.btnAddLabel.visibility = View.VISIBLE
            viewHolder.rowNewsBinding.imgEdit.visibility = View.VISIBLE
            viewHolder.rowNewsBinding.imgDelete.visibility = View.VISIBLE
        } else {
            viewHolder.rowNewsBinding.llEditDelete.visibility = View.GONE
            viewHolder.rowNewsBinding.btnAddLabel.visibility = View.GONE
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun deletePost(news: RealmNews?, context: Context) {
        if (!mRealm.isInTransaction) mRealm.beginTransaction()
        val position = list.indexOf(news)
        if (position != -1) {
            list.removeAt(position)
            notifyItemRemoved(position)
        }
        news?.let {
            it.deleteFromRealm()
            if (context is ReplyActivity) {
                val restartIntent = context.intent
                context.finish()
                context.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0, 0)
                context.startActivity(restartIntent)
                context.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0, 0)
            }
        }
        mRealm.commitTransaction()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return if (parentNews == null) list.size else list.size + 1
    }

    interface OnNewsItemClickListener {
        fun showReply(news: RealmNews?, fromLogin: Boolean, nonTeamMember: Boolean)
        fun addImage(llImage: LinearLayout?)
        fun onNewsItemClick(news: RealmNews?)
        fun clearImages()
    }

    private fun getLabel(s: String): String {
        for (key in Constants.LABELS.keys) {
            if (s == Constants.LABELS[key]) {
                return key
            }
        }
        return ""
    }

    private fun showShareButton(holder: RecyclerView.ViewHolder, news: RealmNews?) {
        val viewHolder = holder as ViewHolderNews
        viewHolder.rowNewsBinding.btnShare.visibility = if (news?.isCommunityNews == true || fromLogin || nonTeamMember) View.GONE else View.VISIBLE
        viewHolder.rowNewsBinding.btnShare.setOnClickListener {
            val array = Gson().fromJson(news?.viewIn, JsonArray::class.java)
            val ob = JsonObject()
            ob.addProperty("section", "community")
            ob.addProperty("_id", currentUser?.planetCode + "@" + currentUser?.parentCode)
            ob.addProperty("sharedDate", Calendar.getInstance().timeInMillis)
            ob.addProperty("teamName", teamName)
            array.add(ob)
            Log.d("AdapterNews", "Added teamName to viewIn: $teamName") //test
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }
            news?.viewIn = Gson().toJson(array)
            mRealm.commitTransaction()
            Utilities.toast(context, context.getString(R.string.shared_to_community))
            viewHolder.rowNewsBinding.btnShare.visibility = View.GONE
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    internal inner class ViewHolderNews(val rowNewsBinding: RowNewsBinding) : RecyclerView.ViewHolder(rowNewsBinding.root) {
        private var adapterPosition = 0
        fun bind(position: Int) {
            adapterPosition = position
        }
    }
}
