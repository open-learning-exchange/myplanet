package org.ole.planet.myplanet.ui.news

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.text.TextUtils
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
import androidx.appcompat.app.AppCompatActivity
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
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.ui.courses.CourseStepFragment.Companion.prependBaseUrlToImages
import org.ole.planet.myplanet.ui.team.teamMember.MemberDetailFragment
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import kotlin.toString

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
    private var currentZoomDialog: Dialog? = null
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
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        rowNewsBinding = RowNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        sharedPreferences = SharedPrefManager(context)
        user = UserProfileDbHandler(context).userModel
        settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ViewHolderNews(rowNewsBinding)
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderNews) {
            holder.bind(position)
            val news = getNews(holder, position)

            if (news?.isValid == true) {
                val viewHolder = holder as ViewHolderNews
                val sharedTeamName = extractSharedTeamName(news)

                resetViews(viewHolder)

                val userModel = configureUser(viewHolder, news)
                showShareButton(viewHolder, news)

                setMessageAndDate(viewHolder, news, sharedTeamName)

                configureEditDeleteButtons(viewHolder, news)

                loadImage(viewHolder, news)
                showReplyButton(viewHolder, news, position)
                addLabels(viewHolder, news)
                showChips(viewHolder, news)

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
        val activity = it.context as AppCompatActivity
        val userName = if ("${userModel?.firstName} ${userModel?.lastName}".trim().isBlank()) {
            userModel?.name
        } else {
            "${userModel?.firstName} ${userModel?.lastName}".trim()
        }
        val fragment = MemberDetailFragment.newInstance(
            userName.toString(),
            userModel?.email.toString(),
            userModel?.dob.toString().substringBefore("T"),
            userModel?.language.toString(),
            userModel?.phoneNumber.toString(),
            profileDbHandler.getOfflineVisits(userModel).toString(),
            profileDbHandler.getLastVisit(userModel!!),
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
        val isGuest = user?.id?.startsWith("guest") == true
        if (listener == null || fromLogin || isGuest) {
            viewHolder.rowNewsBinding.btnReply.visibility = View.GONE
        } else {
            viewHolder.rowNewsBinding.btnReply.visibility = if (nonTeamMember) View.GONE else View.VISIBLE
            viewHolder.rowNewsBinding.btnReply.setOnClickListener { showEditAlert(finalNews?.id, false) }
        }
        val replies: List<RealmNews> = mRealm.where(RealmNews::class.java)
            .sort("time", Sort.DESCENDING)
            .equalTo("replyTo", finalNews?.id, Case.INSENSITIVE)
            .findAll()
        viewHolder.rowNewsBinding.btnShowReply.text = String.format("(%d)", replies.size)
        viewHolder.rowNewsBinding.btnShowReply.setTextColor(context.getColor(R.color.daynight_textColor))
        viewHolder.rowNewsBinding.btnShowReply.visibility = if (replies.isNotEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        if (position == 0 && parentNews != null) {
            viewHolder.rowNewsBinding.btnShowReply.visibility = View.GONE
        }
        if (listener == null || fromLogin) {
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
