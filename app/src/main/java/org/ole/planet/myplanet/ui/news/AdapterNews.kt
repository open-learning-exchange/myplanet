package org.ole.planet.myplanet.ui.news

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Build
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Case
import io.realm.Realm
import io.realm.RealmList
import io.realm.Sort
import java.util.Calendar
import java.util.Locale
import kotlin.toString
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNewsBinding
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNews.Companion.createNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.chat.ChatAdapter
import org.ole.planet.myplanet.ui.courses.CourseStepFragment.Companion.prependBaseUrlToImages
import org.ole.planet.myplanet.ui.team.teamMember.MemberDetailFragment
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.Constants.showBetaFeature
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.makeExpandable

class AdapterNews(var context: Context, private val list: MutableList<RealmNews?>, private var currentUser: RealmUserModel?, private val parentNews: RealmNews?, private val teamName: String = "") : RecyclerView.Adapter<RecyclerView.ViewHolder?>() {
    private lateinit var rowNewsBinding: RowNewsBinding
    private var listener: OnNewsItemClickListener? = null
    private var imageList: RealmList<String>? = null
    lateinit var mRealm: Realm
    private var fromLogin = false
    private var nonTeamMember = false
    private var sharedPreferences: SharedPrefManager? = null
    private var recyclerView: RecyclerView? = null
    var user: RealmUserModel? = null
    private var imageLoader: NewsImageLoader? = null
    private var labelManager: NewsLabelManager? = null
    private val profileDbHandler = UserProfileDbHandler(context)
    lateinit var settings: SharedPreferences
    private val leadersList: List<RealmUserModel> by lazy {
        val raw = settings.getString("communityLeaders", "") ?: ""
        RealmUserModel.parseLeadersJson(raw)
    }

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
            imageLoader = NewsImageLoader(context, this.mRealm)
            labelManager = NewsLabelManager(context, this.mRealm, currentUser)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        rowNewsBinding = RowNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        sharedPreferences = SharedPrefManager(context)
        user = UserProfileDbHandler(context).userModel
        settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (::mRealm.isInitialized) {
            if (imageLoader == null) imageLoader = NewsImageLoader(context, mRealm)
            if (labelManager == null) labelManager = NewsLabelManager(context, mRealm, currentUser)
        }
        return ViewHolderNews(rowNewsBinding)
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderNews) {
            holder.bind(position)
            val news = getNews(holder, position)

            if (news?.isValid == true) {
                val viewHolder = holder
                val sharedTeamName = extractSharedTeamName(news)

                resetViews(viewHolder)

                val userModel = configureUser(viewHolder, news)
                showShareButton(viewHolder, news)

                setMessageAndDate(viewHolder, news, sharedTeamName)

                configureEditDeleteButtons(viewHolder, news)

                imageLoader?.loadImage(viewHolder.rowNewsBinding, news)
                showReplyButton(viewHolder, news, position)
                labelManager?.setupAddLabelMenu(viewHolder.rowNewsBinding, news)
                news.let { labelManager?.showChips(viewHolder.rowNewsBinding, it) }

                handleChat(viewHolder, news)

                val currentLeader = getCurrentLeader(userModel, news)
                setMemberClickListeners(viewHolder, userModel, currentLeader)
            }
        }
    }

    private fun extractSharedTeamName(news: RealmNews): String {
        if (!TextUtils.isEmpty(news.viewIn)) {
            val ar = Gson().fromJson(news.viewIn, JsonArray::class.java)
            if (ar.size() > 1) {
                val ob = ar[0].asJsonObject
                if (ob.has("name") && !ob.get("name").isJsonNull) {
                    return ob.get("name").asString
                }
            }
        }
        return ""
    }

    private fun resetViews(holder: ViewHolderNews) {
        with(holder.rowNewsBinding) {
            tvName.text = ""
            imgUser.setImageResource(0)
            llEditDelete.visibility = View.GONE
            linearLayout51.visibility = View.VISIBLE
            tvMessage.text = ""
            tvDate.text = ""
            imgDelete.setOnClickListener(null)
            imgEdit.setOnClickListener(null)
            btnAddLabel.visibility = View.GONE
            imgEdit.visibility = View.GONE
            imgDelete.visibility = View.GONE
            btnReply.visibility = View.GONE
            imgNews.visibility = View.GONE
            recyclerGchat.visibility = View.GONE
            sharedChat.visibility = View.GONE
        }
    }

    private fun configureUser(holder: ViewHolderNews, news: RealmNews): RealmUserModel? {
        val userModel = mRealm.where(RealmUserModel::class.java)
            .equalTo("id", news.userId)
            .findFirst()
        val userFullName = userModel?.getFullNameWithMiddleName()?.trim()
        if (userModel != null && currentUser != null) {
            holder.rowNewsBinding.tvName.text =
                if (userFullName.isNullOrEmpty()) news.userName else userFullName
            Utilities.loadImage(userModel.userImage, holder.rowNewsBinding.imgUser)
            showHideButtons(userModel, holder)
        } else {
            holder.rowNewsBinding.tvName.text = news.userName
            Utilities.loadImage(null, holder.rowNewsBinding.imgUser)
        }
        return userModel
    }

    private fun setMessageAndDate(holder: ViewHolderNews, news: RealmNews, sharedTeamName: String) {
        val markdownContentWithLocalPaths = prependBaseUrlToImages(
            news.message,
            "file://" + MainApplication.context.getExternalFilesDir(null) + "/ole/"
        )
        setMarkdownText(holder.rowNewsBinding.tvMessage, markdownContentWithLocalPaths)
        val fulltext = holder.rowNewsBinding.tvMessage.text
        holder.rowNewsBinding.tvMessage.makeExpandable(
            fullText = fulltext,
            collapsedMaxLines = 6
        )
        holder.rowNewsBinding.tvDate.text =
            if (sharedTeamName.isEmpty() || teamName.isNotEmpty()) {
                formatDate(news.time)
            } else {
                "${formatDate(news.time)} | Shared from $sharedTeamName"
            }
        holder.rowNewsBinding.tvEdited.visibility = if (news.isEdited) View.VISIBLE else View.GONE
    }


    private fun configureEditDeleteButtons(holder: ViewHolderNews, news: RealmNews) {
        if (news.sharedBy == currentUser?._id && !fromLogin && !nonTeamMember && teamName.isEmpty()) {
            holder.rowNewsBinding.imgDelete.visibility = View.VISIBLE
        }

        if (news.userId == currentUser?._id || news.sharedBy == currentUser?._id) {
            holder.rowNewsBinding.imgDelete.setOnClickListener {
                AlertDialog.Builder(context, R.style.AlertDialogTheme)
                    .setMessage(R.string.delete_record)
                    .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                        deletePost(news, context)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }

        if (news.userId == currentUser?._id) {
            holder.rowNewsBinding.imgEdit.setOnClickListener { showEditAlert(news.id, true) }
            holder.rowNewsBinding.btnAddLabel.visibility = if (fromLogin || nonTeamMember) View.GONE else View.VISIBLE
        } else {
            holder.rowNewsBinding.imgEdit.visibility = View.GONE
            holder.rowNewsBinding.btnAddLabel.visibility = View.GONE
        }
        holder.rowNewsBinding.llEditDelete.visibility = if (fromLogin || nonTeamMember) View.GONE else View.VISIBLE
        holder.rowNewsBinding.btnReply.visibility = if (fromLogin || nonTeamMember) View.GONE else View.VISIBLE
    }

    private fun handleChat(holder: ViewHolderNews, news: RealmNews) {
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

    private fun getCurrentLeader(userModel: RealmUserModel?, news: RealmNews): RealmUserModel? {
        if (userModel == null) {
            for (leader in leadersList) {
                if (leader.name == news.userName) {
                    return leader
                }
            }
        }
        return null
    }

    private fun setMemberClickListeners(holder: ViewHolderNews, userModel: RealmUserModel?, currentLeader: RealmUserModel?) {
        if (!fromLogin) {
            holder.rowNewsBinding.imgUser.setOnClickListener {
                if (userModel == null) {
                    showMemberDetails(currentLeader, it)
                } else {
                    showMemberDetails(userModel, it)
                }
            }
            holder.rowNewsBinding.tvName.setOnClickListener {
                if (userModel == null) {
                    showMemberDetails(currentLeader, it)
                } else {
                    showMemberDetails(userModel, it)
                }
            }
        }
    }
    private fun showMemberDetails(userModel: RealmUserModel?, it: View){
        if (userModel == null) {
            return
        }
        val activity = it.context as AppCompatActivity
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
        val fm = activity.supportFragmentManager
        val tx = fm.beginTransaction()
        fm.findFragmentById(R.id.fragment_container)?.let { currentFragment ->
            tx.hide(currentFragment)
        }
        tx.add(R.id.fragment_container, fragment)
        tx.addToBackStack(null)
        tx.commit()
    }




    private fun isGuestUser() = user?.id?.startsWith("guest") == true

    private fun shouldShowReplyButton() = listener != null && !fromLogin && !isGuestUser()

    private fun getReplies(finalNews: RealmNews?): List<RealmNews> = mRealm.where(RealmNews::class.java)
        .sort("time", Sort.DESCENDING)
        .equalTo("replyTo", finalNews?.id, Case.INSENSITIVE)
        .findAll()

    private fun updateReplyCount(viewHolder: ViewHolderNews, replies: List<RealmNews>, position: Int) {
        with(viewHolder.rowNewsBinding) {
            btnShowReply.text = String.format(Locale.getDefault(),"(%d)", replies.size)
            btnShowReply.setTextColor(context.getColor(R.color.daynight_textColor))
            val visible = replies.isNotEmpty() && !(position == 0 && parentNews != null) && shouldShowReplyButton()
            btnShowReply.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    private fun showReplyButton(holder: RecyclerView.ViewHolder, finalNews: RealmNews?, position: Int) {
        val viewHolder = holder as ViewHolderNews
        if (shouldShowReplyButton()) {
            viewHolder.rowNewsBinding.btnReply.visibility = if (nonTeamMember) View.GONE else View.VISIBLE
            viewHolder.rowNewsBinding.btnReply.setOnClickListener { showEditAlert(finalNews?.id, false) }
        } else {
            viewHolder.rowNewsBinding.btnReply.visibility = View.GONE
        }

        val replies = getReplies(finalNews)
        updateReplyCount(viewHolder, replies, position)

        viewHolder.rowNewsBinding.btnShowReply.setOnClickListener {
            sharedPreferences?.setRepliedNewsId(finalNews?.id)
            listener?.showReply(finalNews, fromLogin, nonTeamMember)
        }
    }

    private data class EditDialogComponents(
        val view: View,
        val editText: EditText,
        val inputLayout: com.google.android.material.textfield.TextInputLayout,
        val imageLayout: LinearLayout
    )

    private fun createEditDialogComponents(): EditDialogComponents {
        val v = LayoutInflater.from(context).inflate(R.layout.alert_input, null)
        val tlInput = v.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tl_input)
        val et = v.findViewById<EditText>(R.id.et_input)
        v.findViewById<View>(R.id.ll_image).visibility =
            if (showBetaFeature(Constants.KEY_NEWSADDIMAGE, context)) View.VISIBLE else View.GONE
        val llImage = v.findViewById<LinearLayout>(R.id.ll_alert_image)
        v.findViewById<View>(R.id.add_news_image).setOnClickListener { listener?.addImage(llImage) }
        return EditDialogComponents(v, et, tlInput, llImage)
    }

    private fun handlePositiveButton(dialog: AlertDialog, isEdit: Boolean, components: EditDialogComponents, news: RealmNews?) {
        val s = components.editText.text.toString().trim()
        if (s.isEmpty()) {
            components.inputLayout.error = context.getString(R.string.please_enter_message)
            return
        }
        if (isEdit) {
            editPost(s, news)
        } else {
            postReply(s, news)
        }
        dialog.dismiss()
    }

    private fun showEditAlert(id: String?, isEdit: Boolean) {
        val components = createEditDialogComponents()
        val message = components.view.findViewById<TextView>(R.id.cust_msg)
        message.text = context.getString(if (isEdit) R.string.edit_post else R.string.reply)
        val icon = components.view.findViewById<ImageView>(R.id.alert_icon)
        icon.setImageResource(R.drawable.ic_edit)

        val news = mRealm.where(RealmNews::class.java).equalTo("id", id).findFirst()
        if (isEdit) {
            components.editText.setText(context.getString(R.string.message_placeholder, news?.message))
        }
        val dialog = AlertDialog.Builder(context, R.style.ReplyAlertDialog)
            .setView(components.view)
            .setPositiveButton(R.string.button_submit, null)
            .setNegativeButton(R.string.cancel) { d, _ ->
                listener?.clearImages()
                d.dismiss()
            }
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            handlePositiveButton(dialog, isEdit, components, news)
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
        map["viewIn"] = news?.viewIn ?: ""

        currentUser?.let { createNews(map, mRealm, it, imageList, true) }
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
        val ar = Gson().fromJson(news?.viewIn, JsonArray::class.java)
        if (!mRealm.isInTransaction) mRealm.beginTransaction()
        val position = list.indexOf(news)
        if (position != -1) {
            list.removeAt(position)
            notifyItemRemoved(position)
        }
        if(teamName.isNotEmpty() || ar.size() < 2){
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
        } else {
            val filtered = JsonArray().apply {
                ar.forEach { elem ->
                    if (!elem.asJsonObject.has("sharedDate")) {
                        add(elem)
                    }
                }
            }
            news?.viewIn = Gson().toJson(filtered)
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

    private fun showShareButton(holder: RecyclerView.ViewHolder, news: RealmNews?) {
        val viewHolder = holder as ViewHolderNews
        val isGuest = user?.id?.startsWith("guest") == true

        viewHolder.rowNewsBinding.btnShare.visibility = if (news?.isCommunityNews == true || fromLogin || nonTeamMember || isGuest) {
            View.GONE
        } else {
            View.VISIBLE
        }

        viewHolder.rowNewsBinding.btnShare.setOnClickListener {
            AlertDialog.Builder(context, R.style.AlertDialogTheme)
                .setTitle(R.string.share_with_community)
                .setMessage(R.string.confirm_share_community)
                .setPositiveButton(R.string.yes) { _, _ ->
                    val array = Gson().fromJson(news?.viewIn, JsonArray::class.java)
                    val firstElement = array.get(0)
                    val obj = firstElement.asJsonObject
                    if(!obj.has("name")){
                        obj.addProperty("name", teamName)
                    }
                    val ob = JsonObject()
                    ob.addProperty("section", "community")
                    ob.addProperty("_id", currentUser?.planetCode + "@" + currentUser?.parentCode)
                    ob.addProperty("sharedDate", Calendar.getInstance().timeInMillis)
                    array.add(ob)
                    if (!mRealm.isInTransaction) {
                        mRealm.beginTransaction()
                    }
                    news?.sharedBy = currentUser?.id
                    news?.viewIn = Gson().toJson(array)
                    mRealm.commitTransaction()
                    Utilities.toast(context, context.getString(R.string.shared_to_community))
                    viewHolder.rowNewsBinding.btnShare.visibility = View.GONE
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
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
