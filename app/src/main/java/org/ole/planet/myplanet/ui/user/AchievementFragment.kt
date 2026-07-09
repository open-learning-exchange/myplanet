package org.ole.planet.myplanet.ui.user

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.time.Instant
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.FragmentAchievementBinding
import org.ole.planet.myplanet.databinding.LayoutButtonPrimaryBinding
import org.ole.planet.myplanet.databinding.RowAchievementBinding
import org.ole.planet.myplanet.model.AchievementData
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.ui.viewer.ResourceViewerActivity
import org.ole.planet.myplanet.ui.viewer.ResourceViewerFragment
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.TimeUtils.getFormattedDateWithTime
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class AchievementFragment : BaseContainerFragment() {

    private val viewModel: AchievementViewModel by viewModels()

    private var _binding: FragmentAchievementBinding? = null
    private val binding get() = _binding!!
    var user: RealmUser? = null
    var listener: OnHomeItemClickListener? = null
    private var achievementData: AchievementData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnHomeItemClickListener) listener = context
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAchievementBinding.inflate(inflater, container, false)
        binding.btnEdit.setOnClickListener {
            if (listener != null) listener?.openCallFragment(EditAchievementFragment())
        }
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun refreshAchievementData() {
        if (!isAdded || requireActivity().isFinishing) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                achievementData = loadAchievementDataAsync()
                updateAchievementUI()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun loadAchievementDataAsync(): AchievementData {
        val uId = user?.id ?: return AchievementData()
        val pCode = user?.planetCode ?: return AchievementData()
        return userRepository.getAchievementData(uId, pCode)
    }

    private fun updateAchievementUI() {
        achievementData?.let {
            setupAchievementHeader(it)
            populateAchievements(it)
            setupReferences(it)
            setupCv(it)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRealtimeSync()
        viewLifecycleOwner.lifecycleScope.launch {
            user = profileDbHandler.getUserModel()
            setupUserData()
            achievementData = loadAchievementDataAsync()
            updateAchievementUI()
        }
    }

    private fun setupUserData() {
        if (!TextUtils.isEmpty(user?.userImage)) {
            Glide.with(requireActivity())
                .load(user?.userImage)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(200, 200)
                .circleCrop()
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(binding.imageView)
        } else {
            binding.imageView.setImageResource(R.drawable.profile)
        }
        val fullName = listOfNotNull(user?.firstName, user?.middleName, user?.lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        binding.tvName.text = if (fullName.isBlank()) user?.name ?: "" else fullName
    }

    private fun setupRealtimeSync() {
        collectWhenStarted(viewModel.dataUpdateFlow) { update ->
            if (update.table == "achievements" && update.shouldRefreshUI) {
                refreshAchievementData()
            }
        }
    }

    private fun setupAchievementHeader(a: AchievementData) {
        binding.tvGoals.text = a.goals.ifBlank { getString(R.string.no_goal_added) }
        binding.tvPurpose.text = a.purpose.ifBlank { getString(R.string.no_purpose_added) }
        binding.tvAchievementHeader.text =
            a.achievementsHeader.ifBlank { getString(R.string.no_achievement_added) }
    }

    private fun populateAchievements(data: AchievementData) {
        binding.llAchievement.removeAllViews()
        val resourcesMap = data.achievementResources.mapNotNull { resource ->
            resource.id?.let { id -> id to resource }
        }.toMap()
        data.achievements.forEach { json ->
            val element = JsonUtils.gson.fromJson(json, JsonElement::class.java)
            val view = if (element is JsonObject) createAchievementView(element, resourcesMap) else null
            view?.let {
                if (it.parent != null) {
                    (it.parent as ViewGroup).removeView(it)
                }
                binding.llAchievement.addView(it)
            }
        }
    }

    private fun createAchievementView(ob: JsonObject, resourcesMap: Map<String, RealmMyLibrary>): View {
        val binding = RowAchievementBinding.inflate(LayoutInflater.from(requireContext()))
        val desc = getString("description", ob)
        binding.tvDescription.text = desc
        binding.tvDate.text = try {
            val epochMillis = Instant.parse(getString("date", ob)).toEpochMilli()
            getFormattedDateWithTime(epochMillis)
        } catch (e: Exception) {
            getString("date", ob)
        }
        binding.tvTitle.text = getString("title", ob)
        val link = getString("link", ob)
        if (link.isNotEmpty()) {
            binding.tvLink.visibility = View.VISIBLE
            binding.tvLink.text = link
        }

        val resourceIds = ob.getAsJsonArray("resources")?.mapNotNull {
            it.asJsonObject?.get("_id")?.asString
        } ?: emptyList()

        val libraries = resourceIds.mapNotNull { resourcesMap[it] }

        if (desc.isNotEmpty() && libraries.isNotEmpty()) {
            binding.llRow.setOnClickListener { toggleDescription(binding) }
            binding.flexboxResources.removeAllViews()
            libraries.forEach { binding.flexboxResources.addView(createResourceButton(it)) }
        } else {
            binding.tvTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        }
        return binding.root
    }

    private fun toggleDescription(binding: RowAchievementBinding) {
        binding.llDesc.visibility = if (binding.llDesc.isGone) View.VISIBLE else View.GONE
        binding.tvTitle.setCompoundDrawablesWithIntrinsicBounds(
            0, 0,
            if (binding.llDesc.isGone) R.drawable.ic_down else R.drawable.ic_up, 0
        )
    }

    private fun createResourceButton(lib: RealmMyLibrary): View {
        val btnBinding = LayoutButtonPrimaryBinding.inflate(LayoutInflater.from(requireContext()))
        btnBinding.root.text = lib.title
        btnBinding.root.setCompoundDrawablesWithIntrinsicBounds(
            0, 0,
            if (lib.isResourceOffline()) R.drawable.ic_eye else R.drawable.ic_download, 0
        )
        btnBinding.root.setOnClickListener {
            if (lib.isResourceOffline()) {
                openResource(lib)
            } else {
                lifecycleScope.launch {
                    resourcesRepository.downloadResources(listOf(lib))
                }
            }
        }
        return btnBinding.root
    }

    private fun setupCv(data: AchievementData) {
        val cvFilename = data.resumeFileName
        if (cvFilename.isEmpty()) {
            binding.cvCard.visibility = View.GONE
            return
        }
        val cvFile = File(FileUtils.getOlePath(requireContext()) + "cv/$cvFilename")
        if (!cvFile.exists()) {
            binding.cvCard.visibility = View.GONE
            return
        }
        binding.cvCard.visibility = View.VISIBLE
        binding.btnViewCv.setOnClickListener {
            val intent = Intent(requireContext(), ResourceViewerActivity::class.java)
            intent.putExtra("TOUCHED_FILE", "cv/$cvFilename")
            intent.putExtra("resourceType", ResourceViewerFragment.ResourceType.PDF.name)
            startActivity(intent)
        }
    }

    private fun setupReferences(data: AchievementData) {
        binding.rvOtherInfo.layoutManager = LinearLayoutManager(requireContext())
        val hasReferences = data.references.isNotEmpty()
        binding.rvOtherInfo.visibility = if (hasReferences) View.VISIBLE else View.GONE
        binding.tvReferencesHeader.visibility = if (hasReferences) View.GONE else View.VISIBLE

        if (binding.rvOtherInfo.adapter == null) {
            binding.rvOtherInfo.adapter = AchievementsAdapter(data.references)
        } else {
            (binding.rvOtherInfo.adapter as AchievementsAdapter).submitJsonList(data.references)
        }
    }


}
