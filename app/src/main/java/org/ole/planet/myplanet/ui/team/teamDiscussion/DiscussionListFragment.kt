package org.ole.planet.myplanet.ui.team.teamDiscussion

import android.content.res.Configuration
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmResults
import io.realm.Sort
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentDiscussionListBinding
import org.ole.planet.myplanet.model.NewsItem
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNews.Companion.createNews
import org.ole.planet.myplanet.model.RealmTeamNotification
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.NewsRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.chat.ChatDetailFragment
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.ui.news.AdapterNewsItem
import org.ole.planet.myplanet.ui.news.AdapterNewsItem.OnNewsItemClickListener
import org.ole.planet.myplanet.ui.news.NewsActions
import org.ole.planet.myplanet.ui.news.ReplyActivity
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class DiscussionListFragment : BaseTeamFragment(), OnNewsItemClickListener {
    private var _binding: FragmentDiscussionListBinding? = null
    private val binding get() = _binding!!
    private var updatedNewsList: RealmResults<RealmNews>? = null
    
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    @Inject
    lateinit var sharedPrefManager: SharedPrefManager
    @Inject
    lateinit var newsRepository: NewsRepository
    private var filteredNewsList: List<RealmNews?> = listOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDiscussionListBinding.inflate(inflater, container, false)
        binding.addMessage.setOnClickListener {
            binding.llAddNews.visibility = if (binding.llAddNews.isVisible) {
                binding.etMessage.setText("")
                binding.tlMessage.error = null
                clearImages()
                View.GONE
            } else {
                View.VISIBLE
            }
            binding.addMessage.text = if (binding.llAddNews.isVisible) {
                getString(R.string.hide_new_message)
            } else {
                getString(R.string.add_message)
            }
        }

        binding.addNewsImage.setOnClickListener {
            llImage = binding.llImages
            val openFolderIntent = FileUtils.openOleFolder(requireContext())
            openFolderLauncher.launch(openFolderIntent)
        }

        binding.btnSubmit.setOnClickListener {
            val message = binding.etMessage.text.toString().trim { it <= ' ' }
            if (message.isEmpty()) {
                binding.tlMessage.error = getString(R.string.please_enter_message)
                return@setOnClickListener
            }
            binding.etMessage.setText(R.string.empty_text)
            val map = HashMap<String?, String>()
            map["viewInId"] = getEffectiveTeamId()
            map["viewInSection"] = "teams"
            map["message"] = message
            map["messageType"] = getEffectiveTeamType()
            map["messagePlanetCode"] = team?.teamPlanetCode ?: ""
            map["name"] = getEffectiveTeamName()

            user?.let { userModel ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        databaseService.executeTransactionAsync { realm ->
                            createNews(map, realm, userModel, imageList)
                        }
                        binding.rvDiscussion.post {
                            binding.rvDiscussion.smoothScrollToPosition(0)
                        }
                        binding.etMessage.text?.clear()
                        imageList.clear()
                        llImage?.removeAllViews()
                        binding.llAddNews.visibility = View.GONE
                        binding.tlMessage.error = null
                        binding.addMessage.text = getString(R.string.add_message)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        if (shouldQueryTeamFromRealm()) {
            team = try {
                mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            if (team == null) {
                try {
                    team = mRealm.where(RealmMyTeam::class.java).equalTo("teamId", teamId).findFirst()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        binding.addMessage.isVisible = false
        updatedNewsList = mRealm.where(RealmNews::class.java).isEmpty("replyTo").sort("time", Sort.DESCENDING).findAllAsync()

        updatedNewsList?.addChangeListener { results ->
            filteredNewsList = filterNewsList(results)
            val ids = filteredNewsList.mapNotNull { it?.id }
            viewLifecycleOwner.lifecycleScope.launch {
                val items = withContext(Dispatchers.IO) {
                    newsRepository.getNewsItemsByIds(ids)
                }
                setData(items)
            }
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val realmNewsList = news

        val count = realmNewsList.size
        mRealm.executeTransactionAsync { realm: Realm ->
            var notification = realm.where(RealmTeamNotification::class.java).equalTo("type", "chat").equalTo("parentId", getEffectiveTeamId()).findFirst()
            if (notification == null) {
                notification = realm.createObject(RealmTeamNotification::class.java, UUID.randomUUID().toString())
                notification.parentId = getEffectiveTeamId()
                notification.type = "chat"
            }
            notification?.lastCount = count
        }
        changeLayoutManager(resources.configuration.orientation, binding.rvDiscussion)

        val ids = realmNewsList.mapNotNull { it.id }
        viewLifecycleOwner.lifecycleScope.launch {
             val items = withContext(Dispatchers.IO) {
                 newsRepository.getNewsItemsByIds(ids)
             }
             showRecyclerView(items)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(isMemberFlow, teamFlow) { isMember, teamData ->
                    Pair(isMember, teamData?.isPublic == true)
                }.collectLatest { (isMember, isPublicTeamFromFlow) ->
                    val isGuest = user?.id?.startsWith("guest") == true
                    val isPublicTeam = isPublicTeamFromFlow || team?.isPublic == true
                    val canPost = !isGuest && (isMember || isPublicTeam)
                    binding.addMessage.isVisible = canPost
                    (binding.rvDiscussion.adapter as? AdapterNewsItem)?.setNonTeamMember(!isMember)
                }
            }
        }
    }

    override fun onNewsItemClick(news: NewsItem?) {
        val bundle = Bundle()
        bundle.putString("newsId", news?.newsId)
        bundle.putString("newsRev", news?.newsRev)
        bundle.putString("conversations", news?.conversations)

        val chatDetailFragment = ChatDetailFragment()
        chatDetailFragment.arguments = bundle

        NavigationHelper.replaceFragment(
            parentFragmentManager,
            R.id.fragment_container,
            chatDetailFragment,
            addToBackStack = true
        )
    }

    // Legacy override
    override fun onNewsItemClick(news: RealmNews?) {
        // No-op
    }

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    private fun filterNewsList(results: RealmResults<RealmNews>): List<RealmNews?> {
        val filteredList: MutableList<RealmNews?> = ArrayList()
        val effectiveTeamId = getEffectiveTeamId()

        for (news in results) {
            if (!TextUtils.isEmpty(news.viewableBy) && news.viewableBy.equals("teams", ignoreCase = true) && news.viewableId.equals(effectiveTeamId, ignoreCase = true)) {
                filteredList.add(news)
            } else if (!TextUtils.isEmpty(news.viewIn)) {
                val ar = GsonUtils.gson.fromJson(news.viewIn, JsonArray::class.java)
                for (e in ar) {
                    val ob = e.asJsonObject
                    if (ob["_id"].asString.equals(effectiveTeamId, ignoreCase = true)) {
                        filteredList.add(news)
                    }
                }
            }
        }
        return filteredList
    }

    private val news: List<RealmNews>
        get() {
            val realmNewsList: List<RealmNews> = mRealm.where(RealmNews::class.java).isEmpty("replyTo").sort("time", Sort.DESCENDING).findAll()
            val list: MutableList<RealmNews> = ArrayList()
            val effectiveTeamId = getEffectiveTeamId()

            for (news in realmNewsList) {
                if (!TextUtils.isEmpty(news.viewableBy) && news.viewableBy.equals("teams", ignoreCase = true) && news.viewableId.equals(effectiveTeamId, ignoreCase = true)) {
                    list.add(news)
                } else if (!TextUtils.isEmpty(news.viewIn)) {
                    val ar = GsonUtils.gson.fromJson(news.viewIn, JsonArray::class.java)
                    for (e in ar) {
                        val ob = e.asJsonObject
                        if (ob["_id"].asString.equals(effectiveTeamId, ignoreCase = true)) {
                            list.add(news)
                        }
                    }
                }
            }
            return list
        }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        changeLayoutManager(newConfig.orientation, binding.rvDiscussion)
    }

    private fun showRecyclerView(newsList: List<NewsItem>?) {
        val existingAdapter = binding.rvDiscussion.adapter
        if (existingAdapter == null) {
            val isLeader = isTeamLeader()
            val adapterNews = activity?.let {
                AdapterNewsItem(it, user, null, getEffectiveTeamName(), isLeader)
            }
            adapterNews?.sharedPrefManager = sharedPrefManager
            adapterNews?.setListener(this)
            if (!isMemberFlow.value) adapterNews?.setNonTeamMember(true)
            newsList?.let { adapterNews?.updateList(it) }
            binding.rvDiscussion.adapter = adapterNews
            adapterNews?.let {
                showNoData(binding.tvNodata, it.itemCount, "discussions")
            }
        } else {
            (existingAdapter as? AdapterNewsItem)?.let { adapter ->
                newsList?.let {
                    adapter.updateList(it)
                    showNoData(binding.tvNodata, adapter.itemCount, "discussions")
                }
            }
        }
    }

    fun setData(list: List<NewsItem>?) {
        showRecyclerView(list)
    }

    // Legacy
    override fun setData(list: List<RealmNews?>?) {
        // No-op
    }

    override fun onDestroyView() {
        updatedNewsList?.removeAllChangeListeners()
        updatedNewsList = null
        if (isRealmInitialized()) {
            mRealm.close()
        }
        _binding = null
        super.onDestroyView()
    }

    private fun shouldQueryTeamFromRealm(): Boolean {
        val hasDirectData = requireArguments().containsKey("teamName") &&
                requireArguments().containsKey("teamType") &&
                requireArguments().containsKey("teamId")
        return !hasDirectData
    }

    private fun isTeamLeader(): Boolean {
        if(teamId.isEmpty()) return false
        val team = mRealm.where(RealmMyTeam::class.java)
            .equalTo("teamId", teamId)
            .equalTo("isLeader", true)
            .findFirst()
        return team?.userId == user?.id
    }

    override fun onMemberSelected(news: NewsItem) {
        if (!isAdded) return
        val userId = news.userId
        if (userId != null) {
            val userModel = mRealm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
            val handler = profileDbHandler
            val fragment = NewsActions.showMemberDetails(userModel, handler) ?: return
            NavigationHelper.replaceFragment(
                requireActivity().supportFragmentManager,
                R.id.fragment_container,
                fragment,
                addToBackStack = true
            )
        }
    }

    override fun onDelete(news: NewsItem) {
        val realmNews = mRealm.where(RealmNews::class.java).equalTo("id", news.id).findFirst()
        if(realmNews != null) {
             NewsActions.deletePost(mRealm, realmNews, mutableListOf(), getEffectiveTeamName(), null)
             // Manually refresh via updatedNewsList listener?
             // Deleting from Realm triggers RealmChangeListener in `updatedNewsList`.
             // Which calls `setData` via mapper.
             // So UI updates automatically.
        }
    }

    override fun onEdit(news: NewsItem, holder: androidx.recyclerview.widget.RecyclerView.ViewHolder) {
         val user = profileDbHandler.userModel
         NewsActions.showEditAlert(requireContext(), mRealm, news.id, true, user, null, holder) { _, _, _ ->
             // Update logic... Realm change should trigger update.
         }
    }

    override fun onReply(news: NewsItem, holder: androidx.recyclerview.widget.RecyclerView.ViewHolder) {
         val user = profileDbHandler.userModel
         NewsActions.showEditAlert(requireContext(), mRealm, news.id, false, user, null, holder) { _, _, _ ->
             // Update logic... Realm change should trigger update.
         }
    }

    override fun onShare(news: NewsItem) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setTitle(R.string.share_with_community)
            .setMessage(R.string.confirm_share_community)
            .setPositiveButton(R.string.yes) { _, _ ->
                val realmNews = mRealm.where(RealmNews::class.java).equalTo("id", news.id).findFirst()
                if (realmNews != null) {
                    val array = GsonUtils.gson.fromJson(realmNews.viewIn, JsonArray::class.java)
                    val firstElement = array.get(0)
                    val obj = firstElement.asJsonObject
                    if (!obj.has("name")) {
                        obj.addProperty("name", getEffectiveTeamName())
                    }
                    val ob = JsonObject()
                    ob.addProperty("section", "community")
                    val user = profileDbHandler.userModel
                    ob.addProperty("_id", user?.planetCode + "@" + user?.parentCode)
                    ob.addProperty("sharedDate", Calendar.getInstance().timeInMillis)
                    array.add(ob)

                    if (!mRealm.isInTransaction) {
                        mRealm.beginTransaction()
                    }
                    realmNews.sharedBy = user?.id
                    realmNews.viewIn = GsonUtils.gson.toJson(array)
                    mRealm.commitTransaction()
                    Utilities.toast(context, getString(R.string.shared_to_community))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onAddLabel(news: NewsItem, label: String) {
        val newsId = news.id
        mRealm.executeTransactionAsync({ transactionRealm ->
             val managedNews = transactionRealm.where(RealmNews::class.java)
                 .equalTo("id", newsId)
                 .findFirst()
             if (managedNews != null) {
                 var managedLabels = managedNews.labels
                 if (managedLabels == null) {
                     managedLabels = io.realm.RealmList()
                     managedNews.labels = managedLabels
                 }
                 if (!managedLabels.contains(label)) {
                     managedLabels.add(label)
                 }
             }
        }, {
             Utilities.toast(context, getString(R.string.label_added))
        })
    }

    override fun onRemoveLabel(news: NewsItem, label: String) {
        val newsId = news.id
        mRealm.executeTransactionAsync({ transactionRealm ->
             val managedNews = transactionRealm.where(RealmNews::class.java)
                 .equalTo("id", newsId)
                 .findFirst()
             managedNews?.labels?.remove(label)
        })
    }

    override fun showReply(news: NewsItem?, fromLogin: Boolean, nonTeamMember: Boolean) {
        // BaseNewsFragment uses replyActivityLauncher which calls adapterNews (legacy).
        // Since we use AdapterNewsItem, we should launch ReplyActivity manually and refresh manually if needed.
        // But DiscussionListFragment relies on RealmChangeListener.
        // If ReplyActivity modifies Realm, DiscussionListFragment updates automatically.
        // So just launching activity is enough.
        if (news != null) {
            val intent = android.content.Intent(activity, ReplyActivity::class.java).putExtra("id", news.id)
                .putExtra("fromLogin", fromLogin)
                .putExtra("nonTeamMember", nonTeamMember)
            startActivity(intent)
        }
    }

    override fun addImage(llImage: ViewGroup?) {
        super.addImage(llImage)
    }

    override fun getCurrentImageList(): RealmList<String>? {
        return super.getCurrentImageList()
    }

    override fun onDataChanged() {
        // Triggered by adapter if needed, but we rely on Realm listener.
    }
}
