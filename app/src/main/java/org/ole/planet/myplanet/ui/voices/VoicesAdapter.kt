package org.ole.planet.myplanet.ui.voices

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.github.chrisbanes.photoview.PhotoView
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmList
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowVoicesBinding
import org.ole.planet.myplanet.model.ChatMessage
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.RealmVoices
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.chat.ChatAdapter
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.ImageUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Markdown.prependBaseUrlToImages
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.makeExpandable

class VoicesAdapter(var context: Context, private var currentUser: RealmUserModel?, private val parentVoice: RealmVoices?, private val teamName: String = "", private val teamId: String? = null, private val userProfileDbHandler: UserProfileDbHandler, private val scope: CoroutineScope, private val userRepository: UserRepository, private val voicesRepository: VoicesRepository, private val teamsRepository: TeamsRepository) : ListAdapter<RealmVoices?, RecyclerView.ViewHolder?>(
    DiffUtils.itemCallback(
        areItemsTheSame = { oldItem, newItem ->
            if (oldItem === newItem) return@itemCallback true
            if (oldItem == null || newItem == null) return@itemCallback oldItem == newItem

            try {
                val oId = oldItem.takeIf { it.isValid }?.id
                val nId = newItem.takeIf { it.isValid }?.id
                oId != null && oId == nId
            } catch (e: Exception) {
                false
            }
        },
        areContentsTheSame = { oldItem, newItem ->
            if (oldItem === newItem) return@itemCallback true
            if (oldItem == null || newItem == null) return@itemCallback oldItem == newItem

            try {
                if (!oldItem.isValid || !newItem.isValid) return@itemCallback false

                oldItem.id == newItem.id && oldItem.time == newItem.time &&
                        oldItem.isEdited == newItem.isEdited && oldItem.message == newItem.message &&
                        oldItem.userName == newItem.userName && oldItem.userId == newItem.userId &&
                        oldItem.sharedBy == newItem.sharedBy
            } catch (e: Exception) {
                false
            }
        }
    )
) {
    private var listener: OnVoicesItemClickListener? = null
    @Inject
    lateinit var sharedPrefManager: SharedPrefManager
    private var imageList: RealmList<String>? = null
    lateinit var mRealm: Realm
    private var fromLogin = false
    private var nonTeamMember = false
    private var recyclerView: RecyclerView? = null
    var user: RealmUserModel? = null
    private var labelManager: VoicesLabelManager? = null
    private val profileDbHandler = userProfileDbHandler
    lateinit var settings: SharedPreferences
    private val userCache = mutableMapOf<String, RealmUserModel?>()
    private val fetchingUserIds = mutableSetOf<String>()
    private val leadersList: List<RealmUserModel> by lazy {
        val raw = settings.getString("communityLeaders", "") ?: ""
        RealmUserModel.parseLeadersJson(raw)
    }
    private var _isTeamLeader: Boolean? = null

    init {
        fetchTeamLeaderStatus()
    }

    private fun fetchTeamLeaderStatus() {
        if (teamId == null) {
            _isTeamLeader = false
            return
        }
        scope.launch {
            val isLeader = withTimeoutOrNull(2000) {
                teamsRepository.isTeamLeader(teamId, currentUser?._id)
            }
            _isTeamLeader = isLeader
        }
    }

    fun setImageList(imageList: RealmList<String>?) {
        this.imageList = imageList
    }

    fun addItem(voice: RealmVoices?) {
        val currentList = currentList.toMutableList()
        currentList.add(0, voice)
        submitListSafely(currentList) {
            recyclerView?.post {
                recyclerView?.scrollToPosition(0)
                recyclerView?.smoothScrollToPosition(0)
            }
        }
    }

    fun setFromLogin(fromLogin: Boolean) {
        this.fromLogin = fromLogin
    }

    fun setNonTeamMember(nonTeamMember: Boolean) {
        if (this.nonTeamMember != nonTeamMember) {
            this.nonTeamMember = nonTeamMember
            notifyItemRangeChanged(0, itemCount)
        }
    }

    fun setListener(listener: OnVoicesItemClickListener?) {
        this.listener = listener
    }

    fun setmRealm(mRealm: Realm?) {
        if (mRealm != null) {
            this.mRealm = mRealm
            labelManager = VoicesLabelManager(context, this.mRealm)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = RowVoicesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        user = userProfileDbHandler.userModel
        settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (::mRealm.isInitialized) {
            if (labelManager == null) labelManager = VoicesLabelManager(context, mRealm)
        }
        return ViewHolderVoices(binding)
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderVoices) {
            holder.bind(position)
            val voice = getVoice(holder, position)

            if (voice?.isValid == true) {
                val viewHolder = holder
                val sharedTeamName = extractSharedTeamName(voice)
                resetViews(viewHolder)
                updateReplyCount(viewHolder, voice, position)
                val userModel = configureUser(viewHolder, voice)
                showShareButton(viewHolder, voice)
                setMessageAndDate(viewHolder, voice, sharedTeamName)
                configureEditDeleteButtons(viewHolder, voice)
                loadImage(viewHolder.binding, voice)
                showReplyButton(viewHolder, voice, position)
                val canManageLabels = canAddLabel(voice)
                labelManager?.setupAddLabelMenu(viewHolder.binding, voice, canManageLabels)
                voice.let { labelManager?.showChips(viewHolder.binding, it, canManageLabels) }
                handleChat(viewHolder, voice)
                val currentLeader = getCurrentLeader(userModel, voice)
                setMemberClickListeners(viewHolder, userModel, currentLeader)
            }
        }
    }

    fun updateReplyBadge(voiceId: String?) {
        if (voiceId.isNullOrEmpty()) return
        val index = if (parentVoice != null) {
            when {
                parentVoice.id == voiceId -> 0
                else -> currentList.indexOfFirst { it?.id == voiceId }.let { if (it != -1) it + 1 else -1 }
            }
        } else {
            currentList.indexOfFirst { it?.id == voiceId }
        }
        if (index >= 0) {
            notifyItemChanged(index)
        }
    }

    private fun extractSharedTeamName(voice: RealmVoices): String {
        if (!TextUtils.isEmpty(voice.viewIn)) {
            val ar = JsonUtils.gson.fromJson(voice.viewIn, JsonArray::class.java)
            if (ar.size() > 1) {
                val ob = ar[0].asJsonObject
                if (ob.has("name") && !ob.get("name").isJsonNull) {
                    return ob.get("name").asString
                }
            }
        }
        return ""
    }

    private fun resetViews(holder: ViewHolderVoices) {
        with(holder.binding) {
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
            llNewsImages.visibility = View.GONE
            llNewsImages.removeAllViews()
            recyclerGchat.visibility = View.GONE
            sharedChat.visibility = View.GONE
        }
    }

    private fun configureUser(holder: ViewHolderVoices, voice: RealmVoices): RealmUserModel? {
        val userId = voice.userId
        if (userId.isNullOrEmpty()) return null

        if (userCache.containsKey(userId)) {
            val userModel = userCache[userId]
            val userFullName = userModel?.getFullNameWithMiddleName()?.trim()
            if (userModel != null && currentUser != null) {
                holder.binding.tvName.text =
                    if (userFullName.isNullOrEmpty()) voice.userName else userFullName
                ImageUtils.loadImage(userModel.userImage, holder.binding.imgUser)
                showHideButtons(voice, holder)
            } else {
                holder.binding.tvName.text = voice.userName
                ImageUtils.loadImage(null, holder.binding.imgUser)
            }
            return userModel
        } else {
            holder.binding.tvName.text = voice.userName
            ImageUtils.loadImage(null, holder.binding.imgUser)
            if (!fetchingUserIds.contains(userId)) {
                fetchingUserIds.add(userId)
                scope.launch {
                    val userModel = userRepository.getUserById(userId)
                    userCache[userId] = userModel
                    fetchingUserIds.remove(userId)
                    withContext(Dispatchers.Main) {
                        currentList.forEachIndexed { index, item ->
                            if (item?.userId == userId) {
                                notifyItemChanged(index)
                            }
                        }
                    }
                }
            }
            return null
        }
    }

    private fun setMessageAndDate(holder: ViewHolderVoices, voice: RealmVoices, sharedTeamName: String) {
        val markdownContentWithLocalPaths = prependBaseUrlToImages(
            voice.message,
            "file://" + context.getExternalFilesDir(null) + "/ole/",
            600,
            350
        )
        setMarkdownText(holder.binding.tvMessage, markdownContentWithLocalPaths)
        val fulltext = holder.binding.tvMessage.text
        holder.binding.tvMessage.makeExpandable(
            fullText = fulltext,
            collapsedMaxLines = 6
        )
        holder.binding.tvDate.text =
            if (sharedTeamName.isEmpty() || teamName.isNotEmpty()) {
                formatDate(voice.time)
            } else {
                "${formatDate(voice.time)} | Shared from $sharedTeamName"
            }
        holder.binding.tvEdited.visibility = if (voice.isEdited) View.VISIBLE else View.GONE
    }

    private fun configureEditDeleteButtons(holder: ViewHolderVoices, voice: RealmVoices) {
        if (voice.sharedBy == currentUser?._id && !fromLogin && !nonTeamMember && teamName.isEmpty()) {
            holder.binding.imgDelete.visibility = View.VISIBLE
        }

        if (voice.userId == currentUser?._id || voice.sharedBy == currentUser?._id) {
            holder.binding.imgDelete.setOnClickListener {
                AlertDialog.Builder(context, R.style.AlertDialogTheme)
                    .setMessage(R.string.delete_record)
                    .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                        val currentList = currentList.toMutableList()
                        val pos = holder.adapterPosition
                        val adjustedPos = if (parentVoice != null && pos > 0) pos - 1 else pos
                        if (adjustedPos >= 0 && adjustedPos < currentList.size) {
                            currentList.removeAt(adjustedPos)
                            submitListSafely(currentList)
                        }
                        VoicesActions.deletePost(mRealm, voice, currentList.toMutableList(), teamName, listener)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }

        if (voice.userId == currentUser?._id) {
            holder.binding.imgEdit.setOnClickListener {
                VoicesActions.showEditAlert(
                    context,
                    mRealm,
                    voice.id,
                    true,
                    currentUser,
                    listener,
                    holder,
                ) { holder, updatedVoice, position ->
                    showReplyButton(holder, updatedVoice, position)
                    notifyItemChanged(position)
                }
            }
        } else {
            holder.binding.imgEdit.visibility = View.GONE
        }
    }

    private fun handleChat(holder: ViewHolderVoices, voice: RealmVoices) {
        if (voice.voicesId?.isNotEmpty() == true) {
            val conversations = JsonUtils.gson.fromJson(voice.conversations, Array<Conversation>::class.java).toList()
            val chatAdapter = ChatAdapter(context, holder.binding.recyclerGchat, holder.itemView.findViewTreeLifecycleOwner()?.lifecycleScope)

            if (user?.id?.startsWith("guest") == false) {
                chatAdapter.setOnChatItemClickListener(object : ChatAdapter.OnChatItemClickListener {
                    override fun onChatItemClick(position: Int, chatItem: ChatMessage) {
                        listener?.onVoicesItemClick(voice)
                    }
                })
            }

            val messages = mutableListOf<ChatMessage>()
            for (conversation in conversations) {
                val query = conversation.query
                val response = conversation.response
                if (query != null) {
                    messages.add(ChatMessage(query, ChatMessage.QUERY))
                }
                if (response != null) {
                    messages.add(ChatMessage(response, ChatMessage.RESPONSE, ChatMessage.RESPONSE_SOURCE_SHARED_VIEW_MODEL))
                }
            }
            chatAdapter.submitList(messages)

            holder.binding.recyclerGchat.adapter = chatAdapter
            holder.binding.recyclerGchat.layoutManager = LinearLayoutManager(context)
            holder.binding.recyclerGchat.visibility = View.VISIBLE
            holder.binding.sharedChat.visibility = View.VISIBLE
        } else {
            holder.binding.recyclerGchat.visibility = View.GONE
            holder.binding.sharedChat.visibility = View.GONE
        }
    }

    private fun getCurrentLeader(userModel: RealmUserModel?, voice: RealmVoices): RealmUserModel? {
        if (userModel == null) {
            for (leader in leadersList) {
                if (leader.name == voice.userName) {
                    return leader
                }
            }
        }
        return null
    }

    fun updateList(newList: List<RealmVoices?>) {
        submitListSafely(newList)
    }

    fun refreshCurrentItems() {
        submitListSafely(currentList.toList())
    }

    private fun submitListSafely(list: List<RealmVoices?>, commitCallback: Runnable? = null) {
        userCache.clear()
        val detachedList = list.map { voice ->
            if (voice?.isValid == true && ::mRealm.isInitialized) {
                try {
                    mRealm.copyFromRealm(voice)
                } catch (e: Exception) {
                    voice
                }
            } else {
                voice
            }
        }
        submitList(detachedList, commitCallback)
    }

    private fun setMemberClickListeners(holder: ViewHolderVoices, userModel: RealmUserModel?, currentLeader: RealmUserModel?) {
        if (!fromLogin) {
            holder.binding.imgUser.setOnClickListener {
                val model = userModel ?: currentLeader
                listener?.onMemberSelected(model)
            }
            holder.binding.tvName.setOnClickListener {
                val model = userModel ?: currentLeader
                listener?.onMemberSelected(model)
            }
        }
    }

    private fun isGuestUser() = user?.id?.startsWith("guest") == true

    private fun isOwner(voice: RealmVoices?): Boolean =
        voice?.userId == currentUser?._id

    private fun isSharedByCurrentUser(voice: RealmVoices?): Boolean =
        voice?.sharedBy == currentUser?._id

    private fun isAdmin(): Boolean =
        currentUser?.level.equals("admin", ignoreCase = true)

    private fun isLoggedInAndMember(): Boolean =
        !fromLogin && !nonTeamMember

    private fun canEdit(voice: RealmVoices?): Boolean =
        isLoggedInAndMember() && (isOwner(voice) || isAdmin() || isTeamLeader())

    private fun canDelete(voice: RealmVoices?): Boolean =
        isLoggedInAndMember() && (isOwner(voice) || isSharedByCurrentUser(voice) || isAdmin() || isTeamLeader())

    private fun canReply(): Boolean =
        isLoggedInAndMember() && !isGuestUser()

    private fun canAddLabel(voice: RealmVoices?): Boolean =
        isLoggedInAndMember() && (isOwner(voice) || isTeamLeader())

    private fun canShare(voice: RealmVoices?): Boolean =
        isLoggedInAndMember() && !voice?.isCommunityVoice!! && !isGuestUser()

    private fun View.setVisibility(condition: Boolean) {
        visibility = if (condition) View.VISIBLE else View.GONE
    }

    fun isTeamLeader(): Boolean {
        return _isTeamLeader ?: false
    }

    fun invalidateTeamLeaderCache() {
        _isTeamLeader = null
        fetchTeamLeaderStatus()
    }

    private fun updateReplyCount(viewHolder: ViewHolderVoices, voice: RealmVoices?, position: Int) {
        viewHolder.job?.cancel()
        viewHolder.job = scope.launch {
            try {
                val replies = voicesRepository.getReplies(voice?.id)
                withContext(Dispatchers.Main) {
                    with(viewHolder.binding) {
                        btnShowReply.text = String.format(Locale.getDefault(), "(%d)", replies.size)
                        btnShowReply.setTextColor(context.getColor(R.color.daynight_textColor))
                        val visible = replies.isNotEmpty() && !(position == 0 && parentVoice != null) && canReply()
                        btnShowReply.visibility = if (visible) View.VISIBLE else View.GONE
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getVoice(holder: RecyclerView.ViewHolder, position: Int): RealmVoices? {
        val voice: RealmVoices? = if (parentVoice != null) {
            if (position == 0) {
                (holder.itemView as CardView).setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_blue_50))
                parentVoice
            } else {
                (holder.itemView as CardView).setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_white_1000))
                getItem(position - 1)
            }
        } else {
            (holder.itemView as CardView).setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_white_1000))
            getItem(position)
        }
        return voice
    }

    private fun showHideButtons(voice: RealmVoices?, holder: RecyclerView.ViewHolder) {
        val viewHolder = holder as ViewHolderVoices
        with(viewHolder.binding) {
            imgEdit.setVisibility(canEdit(voice))
            imgDelete.setVisibility(canDelete(voice))
            btnAddLabel.setVisibility(canAddLabel(voice))
            llEditDelete.setVisibility(canEdit(voice) || canDelete(voice))
        }
    }

    private fun shouldShowReplyButton(): Boolean = canReply()

    private fun showReplyButton(holder: RecyclerView.ViewHolder, finalVoice: RealmVoices?, position: Int) {
        val viewHolder = holder as ViewHolderVoices
        if (shouldShowReplyButton()) {
            viewHolder.binding.btnReply.visibility = if (nonTeamMember) View.GONE else View.VISIBLE
            viewHolder.binding.btnReply.setOnClickListener {
                VoicesActions.showEditAlert(
                    context,
                    mRealm,
                    finalVoice?.id,
                    false,
                    currentUser,
                    listener,
                    viewHolder,
                ) { holder, voice, i -> showReplyButton(holder, voice, i) }
            }
        } else {
            viewHolder.binding.btnReply.visibility = View.GONE
        }

        updateReplyCount(viewHolder, finalVoice, position)

        viewHolder.binding.btnShowReply.setOnClickListener {
            sharedPrefManager.setRepliedVoicesId(finalVoice?.id)
            listener?.showReply(finalVoice, fromLogin, nonTeamMember)
        }
    }

    override fun getItemCount(): Int {
        return if (parentVoice == null) super.getItemCount() else super.getItemCount() + 1
    }

    interface OnVoicesItemClickListener {
        fun showReply(voice: RealmVoices?, fromLogin: Boolean, nonTeamMember: Boolean)
        fun addImage(llImage: ViewGroup?)
        fun onVoicesItemClick(voice: RealmVoices?)
        fun clearImages()
        fun onDataChanged()
        fun onMemberSelected(userModel: RealmUserModel?)
        fun getCurrentImageList(): RealmList<String>?
    }

    private fun showShareButton(holder: RecyclerView.ViewHolder, voice: RealmVoices?) {
        val viewHolder = holder as ViewHolderVoices

        viewHolder.binding.btnShare.setVisibility(canShare(voice))

        viewHolder.binding.btnShare.setOnClickListener {
            AlertDialog.Builder(context, R.style.AlertDialogTheme)
                .setTitle(R.string.share_with_community)
                .setMessage(R.string.confirm_share_community)
                .setPositiveButton(R.string.yes) { _, _ ->
                     val voiceId = voice?.id
                     val userId = currentUser?.id
                     val planetCode = currentUser?.planetCode ?: ""
                     val parentCode = currentUser?.parentCode ?: ""

                     if (voiceId != null && userId != null) {
                         scope.launch {
                             val result = voicesRepository.shareVoiceToCommunity(voiceId, userId, planetCode, parentCode, teamName)
                             withContext(Dispatchers.Main) {
                                 if (result.isSuccess) {
                                     Utilities.toast(context, context.getString(R.string.shared_to_community))
                                     viewHolder.binding.btnShare.visibility = View.GONE
                                 } else {
                                     Utilities.toast(context, "Failed to share voice")
                                 }
                             }
                         }
                     }
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

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is ViewHolderVoices) {
            holder.job?.cancel()
        }
    }

    private fun loadImage(binding: RowVoicesBinding, voice: RealmVoices?) {
        binding.imgVoice.visibility = View.GONE
        binding.llVoicesImages.visibility = View.GONE
        binding.llVoicesImages.removeAllViews()

        val imageUrls = voice?.imageUrls
        if (!imageUrls.isNullOrEmpty()) {
            try {
                if (imageUrls.size == 1) {
                    val imgObject = JsonUtils.gson.fromJson(imageUrls[0], JsonObject::class.java)
                    val path = JsonUtils.getString("imageUrl", imgObject)
                    loadSingleImage(binding, path)
                } else {
                    binding.llNewsImages.visibility = View.VISIBLE
                    for (imageUrl in imageUrls) {
                        val imgObject = JsonUtils.gson.fromJson(imageUrl, JsonObject::class.java)
                        val path = JsonUtils.getString("imageUrl", imgObject)
                        addImageToContainer(binding, path)
                    }
                }
                return
            } catch (_: Exception) {
            }
        }

        voice?.imagesArray?.let { imagesArray ->
            if (imagesArray.size() > 0) {
                if (imagesArray.size() == 1) {
                    val ob = imagesArray[0]?.asJsonObject
                    val resourceId = JsonUtils.getString("resourceId", ob)
                    loadLibraryImage(binding, resourceId)
                } else {
                    binding.llNewsImages.visibility = View.VISIBLE
                    for (i in 0 until imagesArray.size()) {
                        val ob = imagesArray[i]?.asJsonObject
                        val resourceId = JsonUtils.getString("resourceId", ob)
                        addLibraryImageToContainer(binding, resourceId)
                    }
                }
            }
        }
    }

    private fun loadSingleImage(binding: RowVoicesBinding, path: String?) {
        if (path == null) return
        val request = Glide.with(binding.imgVoice.context)
        val file = File(path)
        val target = if (path.lowercase(Locale.getDefault()).endsWith(".gif")) {
            request.asGif().load(file).error(request.asGif().load(path))
        } else {
            request.load(file).error(request.load(path))
        }
        target.diskCacheStrategy(DiskCacheStrategy.ALL).fitCenter().placeholder(R.drawable.ic_loading)
            .error(R.drawable.ic_loading)
            .into(binding.imgVoice)
        binding.imgVoice.visibility = View.VISIBLE
        binding.imgVoice.setOnClickListener {
            showZoomableImage(it.context, path)
        }
    }

    private fun addImageToContainer(binding: RowVoicesBinding, path: String?) {
        if (path == null) return
        val imageView = ImageView(context)
        val size = (100 * context.resources.displayMetrics.density).toInt()
        val margin = (4 * context.resources.displayMetrics.density).toInt()
        val params = ViewGroup.MarginLayoutParams(size, size)
        params.setMargins(margin, margin, margin, margin)
        imageView.layoutParams = params
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP

        val request = Glide.with(context)
        val file = File(path)
        val target = if (path.lowercase(Locale.getDefault()).endsWith(".gif")) {
            request.asGif().load(file).error(request.asGif().load(path))
        } else {
            request.load(file).error(request.load(path))
        }
        target.diskCacheStrategy(DiskCacheStrategy.ALL).fitCenter().placeholder(R.drawable.ic_loading)
            .error(R.drawable.ic_loading)
            .into(imageView)

        imageView.setOnClickListener {
            showZoomableImage(context, path)
        }

        binding.llVoicesImages.addView(imageView)
    }

    private fun loadLibraryImage(binding: RowVoicesBinding, resourceId: String?) {
        if (resourceId == null) return
        scope.launch {
            val library = voicesRepository.getLibraryResource(resourceId)
            withContext(Dispatchers.Main) {
                val basePath = context.getExternalFilesDir(null)
                if (library != null && basePath != null) {
                    val imageFile = File(basePath, "ole/${library.id}/${library.resourceLocalAddress}")
                    val request = Glide.with(binding.imgVoice.context)
                    val isGif = library.resourceLocalAddress?.lowercase(Locale.getDefault()).endsWith(".gif") == true
                    val target = if (isGif) {
                        request.asGif().load(imageFile)
                    } else {
                        request.load(imageFile)
                    }
                    target.diskCacheStrategy(DiskCacheStrategy.ALL).fitCenter().placeholder(R.drawable.ic_loading)
                        .error(R.drawable.ic_loading)
                        .into(binding.imgVoice)
                    binding.imgVoice.visibility = View.VISIBLE
                    binding.imgVoice.setOnClickListener {
                        showZoomableImage(it.context, imageFile.toString())
                    }
                }
            }
        }
    }

    private fun addLibraryImageToContainer(binding: RowVoicesBinding, resourceId: String?) {
        if (resourceId == null) return
        scope.launch {
            val library = voicesRepository.getLibraryResource(resourceId)
            withContext(Dispatchers.Main) {
                val basePath = context.getExternalFilesDir(null)
                if (library != null && basePath != null) {
                    val imageFile = File(basePath, "ole/${library.id}/${library.resourceLocalAddress}")
                    val imageView = ImageView(context)
                    val size = (100 * context.resources.displayMetrics.density).toInt()
                    val margin = (4 * context.resources.displayMetrics.density).toInt()
                    val params = ViewGroup.MarginLayoutParams(size, size)
                    params.setMargins(margin, margin, margin, margin)
                    imageView.layoutParams = params
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP

                    val request = Glide.with(context)
                    val isGif = library.resourceLocalAddress?.lowercase(Locale.getDefault()).endsWith(".gif") == true
                    val target = if (isGif) {
                        request.asGif().load(imageFile)
                    } else {
                        request.load(imageFile)
                    }
                    target.diskCacheStrategy(DiskCacheStrategy.ALL).fitCenter().placeholder(R.drawable.ic_loading)
                        .error(R.drawable.ic_loading)
                        .into(imageView)

                    imageView.setOnClickListener {
                        showZoomableImage(context, imageFile.toString())
                    }

                    binding.llVoicesImages.addView(imageView)
                }
            }
        }
    }

    private fun showZoomableImage(context: Context, imageUrl: String) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_zoomable_image, null)
        val photoView = view.findViewById<PhotoView>(R.id.photoView)
        val closeButton = view.findViewById<ImageView>(R.id.closeButton)

        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(Color.BLACK.toDrawable())

        val request = Glide.with(photoView.context)
        val file = File(imageUrl)
        val target = if (imageUrl.lowercase(Locale.getDefault()).endsWith(".gif")) {
            request.asGif().load(file).error(request.asGif().load(imageUrl))
        } else {
            request.load(file).error(request.load(imageUrl))
        }
        target.diskCacheStrategy(DiskCacheStrategy.ALL).fitCenter().error(R.drawable.ic_loading).into(photoView)

        closeButton.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    internal inner class ViewHolderVoices(val binding: RowVoicesBinding) : RecyclerView.ViewHolder(binding.root) {
        var job: kotlinx.coroutines.Job? = null
        private var adapterPosition = 0
        fun bind(position: Int) {
            adapterPosition = position
        }
    }
}
