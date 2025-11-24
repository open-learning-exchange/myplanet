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
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
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
import org.ole.planet.myplanet.model.NewsMapper
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNews.Companion.createNews
import org.ole.planet.myplanet.model.RealmTeamNotification
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.chat.ChatDetailFragment
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.ui.news.AdapterNews
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.GsonUtils

@AndroidEntryPoint
class DiscussionListFragment : BaseTeamFragment() {
    private var _binding: FragmentDiscussionListBinding? = null
    private val binding get() = _binding!!
    private var updatedNewsList: RealmResults<RealmNews>? = null
    
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    private var filteredNewsList: List<NewsItem> = listOf()

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
            val filtered = filterNewsList(results)
            // Need to detach from Realm to pass to background thread
            val detached = mRealm.copyFromRealm(filtered)

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val mapped = databaseService.withRealm { realm ->
                    NewsMapper.map(realm, detached, user, requireContext())
                }
                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        filteredNewsList = mapped
                        setData(mapped)
                    }
                }
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

        // Initial show - map synchronously or async?
        // realmNewsList is detached copy in `news` property getter? No, check getter.
        // `news` getter queries and returns list.

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
             // detach inside getter? getter returns List<RealmNews>.
             // Checking getter logic below. It returns list.
             // But if it returns managed objects, we can't map in IO.
             // Getter uses findAll(), which returns managed objects.
             // Then it adds to ArrayList. The objects in ArrayList are managed.

             // So we should detach in Main then map in IO.
             // But `news` property is accessed on Main in `onViewCreated`.
             // I'll refactor `showRecyclerView` to handle mapping.

             // Actually, `showRecyclerView` is called with `realmNewsList`.
             // I'll do the mapping here.

             // Wait, `news` getter performs query on `mRealm`. `mRealm` is Main thread.
             // So `realmNewsList` are managed Main thread objects.
        }

        // Current implementation of `showRecyclerView` calls `adapterNews.updateList`.
        // I'll make `showRecyclerView` accept `List<NewsItem>`.
        // But here I have `realmNewsList`.

        // I will map it asynchronously.
        val detachedNews = mRealm.copyFromRealm(realmNewsList)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
             val mapped = databaseService.withRealm { realm ->
                 NewsMapper.map(realm, detachedNews, user, requireContext())
             }
             withContext(Dispatchers.Main) {
                 showRecyclerView(mapped)
             }
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
                    (binding.rvDiscussion.adapter as? AdapterNews)?.setNonTeamMember(!isMember)
                }
            }
        }
    }

    override fun onNewsItemClick(news: NewsItem?) {
        val bundle = Bundle()
        bundle.putString("newsId", news?.id)
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

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    private fun filterNewsList(results: RealmResults<RealmNews>): List<RealmNews> {
        val filteredList: MutableList<RealmNews> = ArrayList()
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
            val adapterNews = activity?.let {
                AdapterNews(it, user, null, getEffectiveTeamName(), teamId, userProfileDbHandler, databaseService)
            }
            adapterNews?.setmRealm(mRealm)
            adapterNews?.setListener(this)
            if (!isMemberFlow.value) adapterNews?.setNonTeamMember(true)
            newsList?.let { adapterNews?.updateList(it) }
            binding.rvDiscussion.adapter = adapterNews
            adapterNews?.let {
                showNoData(binding.tvNodata, it.itemCount, "discussions")
            }
        } else {
            (existingAdapter as? AdapterNews)?.let { adapter ->
                newsList?.let {
                    adapter.updateList(it)
                    showNoData(binding.tvNodata, adapter.itemCount, "discussions")
                }
            }
        }
    }

    override fun setData(list: List<NewsItem>?) {
        showRecyclerView(list)
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
}
