package org.ole.planet.myplanet.ui.teams.voices

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseTeamFragment
import org.ole.planet.myplanet.databinding.FragmentDiscussionListBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.VoicesLabelManager
import org.ole.planet.myplanet.ui.chat.ChatDetailFragment
import org.ole.planet.myplanet.ui.voices.VoicesAdapter
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.ui.components.FragmentNavigator

@AndroidEntryPoint
class TeamsVoicesFragment : BaseTeamFragment() {
    private var _binding: FragmentDiscussionListBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var voicesRepository: VoicesRepository
    @Inject
    lateinit var userSessionManager: UserSessionManager
    @Inject
    lateinit var sharedPrefManager: SharedPrefManager

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
                    val success = voicesRepository.createTeamNews(map, userModel, imageList)
                    if (success) {
                        binding.rvDiscussion.post {
                            binding.rvDiscussion.smoothScrollToPosition(0)
                        }
                        binding.etMessage.text?.clear()
                        imageList.clear()
                        llImage?.removeAllViews()
                        binding.llAddNews.visibility = View.GONE
                        binding.tlMessage.error = null
                        binding.addMessage.text = getString(R.string.add_message)
                    }
                }
            }
        }

        if (shouldQueryTeamFromRealm()) {
            viewLifecycleOwner.lifecycleScope.launch {
                team = teamsRepository.getTeamByIdOrTeamId(teamId)
                updateCanPostMessage(team, isMemberFlow.value)
            }
        } else {
            updateCanPostMessage(team, isMemberFlow.value)
        }
        binding.addMessage.isVisible = false
        return binding.root
    }

    private fun updateCanPostMessage(team: RealmMyTeam?, isMember: Boolean) {
        val isGuest = user?.id?.startsWith("guest") == true
        val isPublicTeam = team?.isPublic == true
        val canPost = !isGuest && (isMember || isPublicTeam)
        binding.addMessage.isVisible = canPost
        (binding.rvDiscussion.adapter as? VoicesAdapter)?.setNonTeamMember(!isMember)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        changeLayoutManager(resources.configuration.orientation, binding.rvDiscussion)

        viewLifecycleOwner.lifecycleScope.launch {
            val realmNewsList = voicesRepository.getFilteredNews(getEffectiveTeamId())
            val count = realmNewsList.size
            voicesRepository.updateTeamNotification(getEffectiveTeamId(), count)
            showRecyclerView(realmNewsList)

            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    voicesRepository.getDiscussionsByTeamIdFlow(getEffectiveTeamId()).collect {
                        setData(it)
                    }
                }
                combine(isMemberFlow, teamFlow) { isMember, teamData ->
                    Pair(isMember, teamData)
                }.collectLatest { (isMember, teamData) ->
                    updateCanPostMessage(teamData, isMember)
                }
            }
        }
    }

    override fun onNewsItemClick(news: RealmNews?) {
        val bundle = Bundle()
        bundle.putString("newsId", news?.newsId)
        bundle.putString("newsRev", news?.newsRev)
        bundle.putString("conversations", news?.conversations)

        val chatDetailFragment = ChatDetailFragment()
        chatDetailFragment.arguments = bundle

        FragmentNavigator.replaceFragment(
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        changeLayoutManager(newConfig.orientation, binding.rvDiscussion)
    }

    private fun showRecyclerView(realmNewsList: List<RealmNews?>?) {
        val existingAdapter = binding.rvDiscussion.adapter
        if (existingAdapter == null) {
            val labelManager = VoicesLabelManager(requireActivity(), voicesRepository, viewLifecycleOwner.lifecycleScope)
            val adapterNews = activity?.let {
                VoicesAdapter(
                    context = it,
                    currentUser = user,
                    parentNews = null,
                    teamName = getEffectiveTeamName(),
                    teamId = teamId,
                    userSessionManager = userSessionManager,
                    scope = viewLifecycleOwner.lifecycleScope,
                    isTeamLeaderFn = { teamsRepository.isTeamLeader(teamId, user?._id) },
                    getUserFn = { userId -> userRepository.getUserById(userId) },
                    getReplyCountFn = { newsId -> voicesRepository.getReplies(newsId).size },
                    deletePostFn = { newsId -> voicesRepository.deletePost(newsId, getEffectiveTeamName()) },
                    shareNewsFn = { newsId, userId, planetCode, parentCode, teamName ->
                        voicesRepository.shareNewsToCommunity(newsId, userId, planetCode, parentCode, teamName)
                    },
                    getLibraryResourceFn = { resourceId -> voicesRepository.getLibraryResource(resourceId) },
                    labelManager = labelManager
                )
            }
            adapterNews?.sharedPrefManager = sharedPrefManager
            adapterNews?.setListener(this)
            if (!isMemberFlow.value) adapterNews?.setNonTeamMember(true)
            realmNewsList?.let { adapterNews?.updateList(it) }
            binding.rvDiscussion.adapter = adapterNews
            adapterNews?.let {
                showNoData(binding.tvNodata, it.itemCount, "discussions")
            }
        } else {
            (existingAdapter as? VoicesAdapter)?.let { adapter ->
                realmNewsList?.let {
                    adapter.updateList(it)
                    showNoData(binding.tvNodata, adapter.itemCount, "discussions")
                }
            }
        }
    }

    override fun setData(list: List<RealmNews?>?) {
        showRecyclerView(list)
    }

    override fun onDestroyView() {
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
