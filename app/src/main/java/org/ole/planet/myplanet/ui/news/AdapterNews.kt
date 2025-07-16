package org.ole.planet.myplanet.ui.news

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Build
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
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
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNewsBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.ui.news.ChatHandler
import org.ole.planet.myplanet.ui.news.NewsPermissionChecker
import org.ole.planet.myplanet.ui.news.ImageLoader
import org.ole.planet.myplanet.ui.news.NewsViewBinder

class AdapterNews(var context: Context, private val list: MutableList<RealmNews?>, private var currentUser: RealmUserModel?, private val parentNews: RealmNews?, private val teamName: String = "", private val teamId: String? = null) : RecyclerView.Adapter<RecyclerView.ViewHolder?>() {
    private lateinit var rowNewsBinding: RowNewsBinding
    private var listener: OnNewsItemClickListener? = null
    private var imageList: RealmList<String>? = null
    lateinit var mRealm: Realm
    private var fromLogin = false
    private var nonTeamMember = false
    private var sharedPreferences: SharedPrefManager? = null
    private var recyclerView: RecyclerView? = null
    var user: RealmUserModel? = null
    private var labelManager: NewsLabelManager? = null
    private lateinit var permissionChecker: NewsPermissionChecker
    private lateinit var imageLoader: ImageLoader
    private lateinit var chatHandler: ChatHandler
    private lateinit var viewBinder: NewsViewBinder
    private val profileDbHandler = UserProfileDbHandler(context)
    lateinit var settings: SharedPreferences
    private val leadersList: List<RealmUserModel> by lazy {
        val raw = settings.getString("communityLeaders", "") ?: ""
        RealmUserModel.parseLeadersJson(raw)
    }

    private fun updateHelpers() {
        if (::mRealm.isInitialized) {
            permissionChecker = NewsPermissionChecker(mRealm, currentUser, user, fromLogin, nonTeamMember, teamId)
            imageLoader = ImageLoader(context, mRealm)
            chatHandler = ChatHandler(context, listener, user)
            viewBinder = NewsViewBinder(mRealm, currentUser, profileDbHandler)
        }
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
        updateHelpers()
    }

    fun setNonTeamMember(nonTeamMember: Boolean) {
        this.nonTeamMember = nonTeamMember
        updateHelpers()
    }

    fun setListener(listener: OnNewsItemClickListener?) {
        this.listener = listener
        updateHelpers()
    }

    fun setmRealm(mRealm: Realm?) {
        if (mRealm != null) {
            this.mRealm = mRealm
            labelManager = NewsLabelManager(context, this.mRealm, currentUser)
            updateHelpers()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        rowNewsBinding = RowNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        sharedPreferences = SharedPrefManager(context)
        user = UserProfileDbHandler(context).userModel
        settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (::mRealm.isInitialized) {
            if (labelManager == null) labelManager = NewsLabelManager(context, mRealm, currentUser)
        }
        updateHelpers()
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

                val userModel = viewBinder.configureUser(viewHolder.rowNewsBinding, news)
                showHideButtons(news, viewHolder)
                showShareButton(viewHolder, news)

                viewBinder.setMessageAndDate(viewHolder.rowNewsBinding, news, sharedTeamName, teamName)

                configureEditDeleteButtons(viewHolder, news)

                imageLoader.loadImage(viewHolder.rowNewsBinding, news)
                showReplyButton(viewHolder, news, position)
                labelManager?.setupAddLabelMenu(viewHolder.rowNewsBinding, news)
                news.let { labelManager?.showChips(viewHolder.rowNewsBinding, it) }

                chatHandler.handleChat(viewHolder.rowNewsBinding, news)

                val currentLeader = getCurrentLeader(userModel, news)
                viewBinder.setMemberClickListeners(viewHolder.rowNewsBinding, userModel, currentLeader, fromLogin)
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


    private fun configureEditDeleteButtons(holder: ViewHolderNews, news: RealmNews) {
        if (news.sharedBy == currentUser?._id && !fromLogin && !nonTeamMember && teamName.isEmpty()) {
            holder.rowNewsBinding.imgDelete.visibility = View.VISIBLE
        }

        if (permissionChecker.canDelete(news)) {
            holder.rowNewsBinding.imgDelete.setOnClickListener {
                AlertDialog.Builder(context, R.style.AlertDialogTheme)
                    .setMessage(R.string.delete_record)
                    .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                        NewsActions.deletePost(context, mRealm, news, list, teamName)
                        notifyDataSetChanged()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }

        if (permissionChecker.canEdit(news)) {
            holder.rowNewsBinding.imgEdit.setOnClickListener {
                NewsActions.showEditAlert(context, mRealm, news.id, true, currentUser, imageList, listener)
            }
            holder.rowNewsBinding.btnAddLabel.visibility = if (fromLogin || nonTeamMember) View.GONE else View.VISIBLE
        } else {
            holder.rowNewsBinding.imgEdit.visibility = View.GONE
            holder.rowNewsBinding.btnAddLabel.visibility = View.GONE
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

    private fun View.setVisibility(condition: Boolean) {
        visibility = if (condition) View.VISIBLE else View.GONE
    }

    fun isTeamLeader(): Boolean {
        if(teamId==null)return false
        val team = mRealm.where(RealmMyTeam::class.java)
            .equalTo("teamId", teamId)
            .equalTo("isLeader", true)
            .findFirst()
        return team?.userId == currentUser?._id
    }

    private fun getReplies(finalNews: RealmNews?): List<RealmNews> = mRealm.where(RealmNews::class.java)
        .sort("time", Sort.DESCENDING)
        .equalTo("replyTo", finalNews?.id, Case.INSENSITIVE)
        .findAll()

    private fun updateReplyCount(viewHolder: ViewHolderNews, replies: List<RealmNews>, position: Int) {
        with(viewHolder.rowNewsBinding) {
            btnShowReply.text = String.format(Locale.getDefault(),"(%d)", replies.size)
            btnShowReply.setTextColor(context.getColor(R.color.daynight_textColor))
            val visible = replies.isNotEmpty() && !(position == 0 && parentNews != null) && permissionChecker.canReply()
            btnShowReply.visibility = if (visible) View.VISIBLE else View.GONE
        }
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

    private fun showHideButtons(news: RealmNews?, holder: RecyclerView.ViewHolder) {
        val viewHolder = holder as ViewHolderNews
        with(viewHolder.rowNewsBinding) {
            imgEdit.setVisibility(permissionChecker.canEdit(news))
            imgDelete.setVisibility(permissionChecker.canDelete(news))
            btnAddLabel.setVisibility(permissionChecker.canAddLabel(news))
            llEditDelete.setVisibility(permissionChecker.canEdit(news) || permissionChecker.canDelete(news))
        }
    }

    private fun shouldShowReplyButton(): Boolean = permissionChecker.canReply()

    private fun showReplyButton(holder: RecyclerView.ViewHolder, finalNews: RealmNews?, position: Int) {
        val viewHolder = holder as ViewHolderNews
        if (shouldShowReplyButton()) {
            viewHolder.rowNewsBinding.btnReply.visibility = if (nonTeamMember) View.GONE else View.VISIBLE
            viewHolder.rowNewsBinding.btnReply.setOnClickListener {
                NewsActions.showEditAlert(context, mRealm, finalNews?.id, false, currentUser, imageList, listener)
            }
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

        viewHolder.rowNewsBinding.btnShare.setVisibility(permissionChecker.canShare(news))

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