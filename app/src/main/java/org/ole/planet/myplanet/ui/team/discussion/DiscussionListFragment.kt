package org.ole.planet.myplanet.ui.team.discussion

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
import org.ole.planet.myplanet.databinding.FragmentDiscussionListBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmVoices
import org.ole.planet.myplanet.model.RealmVoices.Companion.createVoices
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.chat.ChatDetailFragment
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.ui.voices.VoicesAdapter
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.SharedPrefManager

@AndroidEntryPoint
class DiscussionListFragment : BaseTeamFragment() {
    private var _binding: FragmentDiscussionListBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var voicesRepository: VoicesRepository
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    @Inject
    lateinit var sharedPrefManager: SharedPrefManager

    private var filteredVoicesList: List<RealmVoices?> = listOf()

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
                            createVoices(map, realm, userModel, imageList)
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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        changeLayoutManager(resources.configuration.orientation, binding.rvDiscussion)

        viewLifecycleOwner.lifecycleScope.launch {
            val realmVoicesList = voicesRepository.getFilteredVoices(getEffectiveTeamId())
            val count = realmVoicesList.size
            voicesRepository.updateTeamNotification(getEffectiveTeamId(), count)
            showRecyclerView(realmVoicesList)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    voicesRepository.getDiscussionsByTeamIdFlow(getEffectiveTeamId()).collect {
                        setData(it)
                    }
                }
                combine(isMemberFlow, teamFlow) { isMember, teamData ->
                    Pair(isMember, teamData?.isPublic == true)
                }.collectLatest { (isMember, isPublicTeamFromFlow) ->
                    val isGuest = user?.id?.startsWith("guest") == true
                    val isPublicTeam = isPublicTeamFromFlow || team?.isPublic == true
                    val canPost = !isGuest && (isMember || isPublicTeam)
                    binding.addMessage.isVisible = canPost
                    (binding.rvDiscussion.adapter as? VoicesAdapter)?.setNonTeamMember(!isMember)
                }
            }
        }
    }

    override fun onVoicesItemClick(voices: RealmVoices?) {
        val bundle = Bundle()
        bundle.putString("voicesId", voices?.id)
        bundle.putString("voicesRev", voices?._rev)
        bundle.putString("conversations", voices?.conversations)

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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        changeLayoutManager(newConfig.orientation, binding.rvDiscussion)
    }

    private fun showRecyclerView(realmVoicesList: List<RealmVoices?>?) {
        val existingAdapter = binding.rvDiscussion.adapter
        if (existingAdapter == null) {
            val adapterVoices = activity?.let {
                VoicesAdapter(it, user, null, getEffectiveTeamName(), teamId, userProfileDbHandler, viewLifecycleOwner.lifecycleScope, userRepository, voicesRepository, teamsRepository)
            }
            adapterVoices?.sharedPrefManager = sharedPrefManager
            adapterVoices?.setmRealm(mRealm)
            adapterVoices?.setListener(this)
            if (!isMemberFlow.value) adapterVoices?.setNonTeamMember(true)
            realmVoicesList?.let { adapterVoices?.updateList(it) }
            binding.rvDiscussion.adapter = adapterVoices
            adapterVoices?.let {
                showNoData(binding.tvNodata, it.itemCount, "discussions")
            }
        } else {
            (existingAdapter as? VoicesAdapter)?.let { adapter ->
                realmVoicesList?.let {
                    adapter.updateList(it)
                    showNoData(binding.tvNodata, adapter.itemCount, "discussions")
                }
            }
        }
    }

    override fun setData(list: List<RealmVoices?>?) {
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