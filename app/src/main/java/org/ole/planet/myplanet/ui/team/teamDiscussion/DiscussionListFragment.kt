package org.ole.planet.myplanet.ui.team.teamDiscussion

import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.JsonArray
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertInputBinding
import org.ole.planet.myplanet.databinding.FragmentDiscussionListBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNews.Companion.createNews
import org.ole.planet.myplanet.model.RealmTeamNotification
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.chat.ChatDetailFragment
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.ui.news.AdapterNews
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.showBetaFeature
import org.ole.planet.myplanet.utilities.FileUtils.openOleFolder
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class DiscussionListFragment : BaseTeamFragment() {
    private lateinit var fragmentDiscussionListBinding: FragmentDiscussionListBinding
    private var updatedNewsList: RealmResults<RealmNews>? = null
    
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    private var filteredNewsList: List<RealmNews?> = listOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentDiscussionListBinding = FragmentDiscussionListBinding.inflate(inflater, container, false)
        fragmentDiscussionListBinding.addMessage.setOnClickListener { showAddMessage() }

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
        if (user?.id?.startsWith("guest") == true) {
            fragmentDiscussionListBinding.addMessage.visibility = View.GONE
        }else if(isMember()) {
            fragmentDiscussionListBinding.addMessage.visibility = View.VISIBLE
        } else if(team?.isPublic == true && !isMember()) {
            fragmentDiscussionListBinding.addMessage.visibility = View.VISIBLE
        }
        updatedNewsList = mRealm.where(RealmNews::class.java).isEmpty("replyTo").sort("time", Sort.DESCENDING).findAllAsync()

        updatedNewsList?.addChangeListener { results ->
            filteredNewsList = filterNewsList(results)
            setData(filteredNewsList)
        }
        return fragmentDiscussionListBinding.root
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
        changeLayoutManager(resources.configuration.orientation, fragmentDiscussionListBinding.rvDiscussion)
        showRecyclerView(realmNewsList)
    }

    override fun onNewsItemClick(news: RealmNews?) {
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
                val ar = Gson().fromJson(news.viewIn, JsonArray::class.java)
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
                    val ar = Gson().fromJson(news.viewIn, JsonArray::class.java)
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
        changeLayoutManager(newConfig.orientation, fragmentDiscussionListBinding.rvDiscussion)
    }

    private fun showRecyclerView(realmNewsList: List<RealmNews?>?) {
        val existingAdapter = fragmentDiscussionListBinding.rvDiscussion.adapter
        if (existingAdapter == null) {
            val adapterNews = activity?.let {
                realmNewsList?.let { list ->
                    AdapterNews(it, list.toMutableList(), user, null, getEffectiveTeamName(), teamId, userProfileDbHandler)
                }
            }
            adapterNews?.setmRealm(mRealm)
            adapterNews?.setListener(this)
            if (!isMember()) adapterNews?.setNonTeamMember(true)
            fragmentDiscussionListBinding.rvDiscussion.adapter = adapterNews
            adapterNews?.let {
                showNoData(fragmentDiscussionListBinding.tvNodata, it.itemCount, "discussions")
            }
        } else {
            (existingAdapter as? AdapterNews)?.let { adapter ->
                realmNewsList?.let {
                    adapter.updateList(it)
                    showNoData(fragmentDiscussionListBinding.tvNodata, adapter.itemCount, "discussions")
                }
            }
        }
    }

    private fun showAddMessage() {
        val binding = AlertInputBinding.inflate(layoutInflater)
        val layout = binding.tlInput
        binding.addNewsImage.setOnClickListener {
            llImage = binding.llImage
            val openFolderIntent = openOleFolder()
            openFolderLauncher.launch(openFolderIntent)
        }
        binding.llImage.visibility = if (showBetaFeature(Constants.KEY_NEWSADDIMAGE, requireContext())) View.VISIBLE else View.GONE
        layout.hint = getString(R.string.enter_message)
        layout.editText?.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.daynight_textColor))
        binding.custMsg.text = getString(R.string.add_message)

        val dialog = AlertDialog.Builder(requireActivity(), R.style.CustomAlertDialog)
            .setView(binding.root)
            .setPositiveButton(getString(R.string.save)) { _: DialogInterface?, _: Int ->
                val msg = "${layout.editText?.text}".trim { it <= ' ' }
                if (msg.isEmpty()) {
                    Utilities.toast(activity, getString(R.string.message_is_required))
                    return@setPositiveButton
                }
                val map = HashMap<String?, String>()
                map["viewInId"] = getEffectiveTeamId()
                map["viewInSection"] = "teams"
                map["message"] = msg
                map["messageType"] = getEffectiveTeamType()
                map["messagePlanetCode"] = team?.teamPlanetCode ?: ""
                map["name"] = getEffectiveTeamName()
                
                lifecycleScope.launch(Dispatchers.IO) {
                    user?.let { userModel ->
                        databaseService.withRealmAsync { realm ->
                            createNews(map, realm, userModel, imageList)
                        }
                        withContext(Dispatchers.Main) {
                            fragmentDiscussionListBinding.rvDiscussion.adapter?.notifyDataSetChanged()
                            setData(news)
                            fragmentDiscussionListBinding.rvDiscussion.scrollToPosition(0)
                        }
                    }
                }
                
                layout.editText?.text?.clear()
                imageList.clear()
                llImage?.removeAllViews()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                layout.editText?.text?.clear()
                imageList.clear()
                llImage?.removeAllViews()
                dialog.dismiss()
            }
            .create()

        dialog.show()
    }

    override fun setData(list: List<RealmNews?>?) {
        showRecyclerView(list)
    }

    private fun shouldQueryTeamFromRealm(): Boolean {
        val hasDirectData = requireArguments().containsKey("teamName") &&
                requireArguments().containsKey("teamType") &&
                requireArguments().containsKey("teamId")
        return !hasDirectData
    }

    companion object {
        fun newInstance(teamId: String, teamName: String, teamType: String): DiscussionListFragment {
            val fragment = DiscussionListFragment()
            val args = Bundle().apply {
                putString("teamId", teamId)
                putString("teamName", teamName)
                putString("teamType", teamType)
                putString("id", teamId)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
