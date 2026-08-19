package org.ole.planet.myplanet.ui.user

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.model.gamification.BadgeCategory
import org.ole.planet.myplanet.model.gamification.GamificationSummary
import org.ole.planet.myplanet.ui.viewer.ResourceViewerActivity
import org.ole.planet.myplanet.ui.viewer.ResourceViewerFragment
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.ImageUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.TimeUtils.getFormattedDateWithTime
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class AchievementFragment : BaseContainerFragment() {

    private val viewModel: AchievementViewModel by viewModels()
    private val gamificationViewModel: GamificationViewModel by viewModels()

    private var _binding: FragmentAchievementBinding? = null
    private val binding get() = _binding!!
    var user: UserEntity? = null
    var listener: OnHomeItemClickListener? = null
    private var achievementData: AchievementData? = null

    private lateinit var badgesAdapter: GamificationBadgesAdapter
    private lateinit var certificatesAdapter: CourseCertificatesAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnHomeItemClickListener) listener = context
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAchievementBinding.inflate(inflater, container, false)
        setupViews()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRealtimeSync()
        setupGamificationObservers()

        viewLifecycleOwner.lifecycleScope.launch {
            user = userRepository.getUserModel()
            setupUserData()
            val userId = user?.id ?: ""
            val userName = user?.name ?: ""
            gamificationViewModel.loadGamificationData(userId, userName)
            achievementData = loadAchievementDataAsync()
            updateAchievementUI()
        }
    }

    private fun setupViews() {
        binding.btnEdit.setOnClickListener {
            listener?.openCallFragment(EditAchievementFragment())
        }

        // Tab switcher
        binding.toggleTabs.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_tab_gamification -> {
                        binding.llGamificationHub.visibility = View.VISIBLE
                        binding.llPortfolio.visibility = View.GONE
                    }
                    R.id.btn_tab_portfolio -> {
                        binding.llGamificationHub.visibility = View.GONE
                        binding.llPortfolio.visibility = View.VISIBLE
                    }
                }
            }
        }

        // Setup Gamification Badges Recycler
        badgesAdapter = GamificationBadgesAdapter()
        binding.rvBadges.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBadges.adapter = badgesAdapter

        // Setup Course Certificates Recycler
        certificatesAdapter = CourseCertificatesAdapter { cert ->
            val dialog = CertificateDialogFragment.newInstance(cert)
            dialog.show(childFragmentManager, "CertificateDialog")
        }
        binding.rvCertificates.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCertificates.adapter = certificatesAdapter

        // Setup Category Filter Chips
        binding.chipGroupBadgeCategories.setOnCheckedStateChangeListener { _, checkedIds ->
            val category = when (checkedIds.firstOrNull()) {
                R.id.chip_cat_courses -> BadgeCategory.COURSES
                R.id.chip_cat_streaks -> BadgeCategory.STREAKS
                R.id.chip_cat_teams -> BadgeCategory.TEAMS
                R.id.chip_cat_resources -> BadgeCategory.RESOURCES
                R.id.chip_cat_exams -> BadgeCategory.EXAMS
                else -> BadgeCategory.ALL
            }
            gamificationViewModel.setCategory(category)
        }
    }

    private fun setupGamificationObservers() {
        collectWhenStarted(gamificationViewModel.gamificationSummary) { summary ->
            summary?.let { updateGamificationUI(it) }
        }

        collectWhenStarted(gamificationViewModel.filteredBadges) { badges ->
            badgesAdapter.submitList(badges)
        }
    }

    private fun updateGamificationUI(summary: GamificationSummary) {
        val streak = summary.streakInfo.currentStreak
        binding.tvStreakCount.text = if (streak == 1) {
            getString(R.string.day_streak, 1)
        } else {
            getString(R.string.days_streak, streak)
        }

        binding.tvStreakLongest.text = getString(R.string.longest_streak, summary.streakInfo.longestStreak)

        if (summary.streakInfo.isActiveToday) {
            binding.tvStreakActiveToday.text = getString(R.string.active_today)
            binding.tvStreakActiveToday.setBackgroundResource(R.drawable.bg_streak_pill_active)
            binding.tvStreakMessage.text = getString(R.string.streak_active_encouragement)
        } else {
            binding.tvStreakActiveToday.text = getString(R.string.not_active_today)
            binding.tvStreakActiveToday.setBackgroundResource(R.drawable.bg_streak_pill_inactive)
            binding.tvStreakMessage.text = getString(R.string.streak_encouragement)
        }

        binding.tvStatBadges.text = "${summary.unlockedBadgesCount}/${summary.totalBadgesCount}"
        binding.tvStatCourses.text = "${summary.completedCoursesCount}"
        binding.tvStatResources.text = "${summary.resourcesReadCount}"
        binding.tvStatTasks.text = "${summary.tasksCompletedCount}"

        binding.tvBadgesUnlockedStat.text = getString(
            R.string.badges_unlocked_stat,
            summary.unlockedBadgesCount,
            summary.totalBadgesCount
        )

        certificatesAdapter.submitList(summary.certificates)
        if (summary.certificates.isEmpty()) {
            binding.tvEmptyCertificates.visibility = View.VISIBLE
            binding.rvCertificates.visibility = View.GONE
        } else {
            binding.tvEmptyCertificates.visibility = View.GONE
            binding.rvCertificates.visibility = View.VISIBLE
        }
    }

    private fun setupUserData() {
        ImageUtils.loadProfileImage(user?.userImage, binding.imageView, 200)
        val fullName = listOfNotNull(user?.firstName, user?.middleName, user?.lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        binding.tvName.text = if (fullName.isBlank()) user?.name ?: "" else fullName
    }

    private fun setupRealtimeSync() {
        collectWhenStarted(viewModel.achievementUpdates) {
            refreshAchievementData()
            gamificationViewModel.refresh()
        }
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

    private fun createAchievementView(ob: JsonObject, resourcesMap: Map<String, MyLibrary>): View {
        val rowBinding = RowAchievementBinding.inflate(LayoutInflater.from(requireContext()))
        val desc = getString("description", ob)
        rowBinding.tvDescription.text = desc
        rowBinding.tvDate.text = try {
            val epochMillis = Instant.parse(getString("date", ob)).toEpochMilli()
            getFormattedDateWithTime(epochMillis)
        } catch (e: Exception) {
            getString("date", ob)
        }
        rowBinding.tvTitle.text = getString("title", ob)
        val link = getString("link", ob)
        if (link.isNotEmpty()) {
            rowBinding.tvLink.visibility = View.VISIBLE
            rowBinding.tvLink.text = link
        }

        val resourceIds = ob.getAsJsonArray("resources")?.mapNotNull {
            it.asJsonObject?.get("_id")?.asString
        } ?: emptyList()

        val libraries = resourceIds.mapNotNull { resourcesMap[it] }

        if (desc.isNotEmpty() && libraries.isNotEmpty()) {
            rowBinding.llRow.setOnClickListener { toggleDescription(rowBinding) }
            rowBinding.flexboxResources.removeAllViews()
            libraries.forEach { rowBinding.flexboxResources.addView(createResourceButton(it)) }
        } else {
            rowBinding.tvTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        }
        return rowBinding.root
    }

    private fun toggleDescription(rowBinding: RowAchievementBinding) {
        rowBinding.llDesc.visibility = if (rowBinding.llDesc.isGone) View.VISIBLE else View.GONE
        rowBinding.tvTitle.setCompoundDrawablesWithIntrinsicBounds(
            0, 0,
            if (rowBinding.llDesc.isGone) R.drawable.ic_down else R.drawable.ic_up, 0
        )
    }

    private fun createResourceButton(lib: MyLibrary): View {
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

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
