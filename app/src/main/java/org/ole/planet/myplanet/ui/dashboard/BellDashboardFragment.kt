package org.ole.planet.myplanet.ui.dashboard

import android.animation.ObjectAnimator
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseDashboardFragment
import org.ole.planet.myplanet.databinding.FragmentHomeBellBinding
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.ui.courses.CoursesFragment
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment
import org.ole.planet.myplanet.ui.life.LifeFragment
import org.ole.planet.myplanet.ui.resources.ResourcesFragment
import org.ole.planet.myplanet.ui.submissions.SubmissionsAdapter
import org.ole.planet.myplanet.ui.submissions.SubmissionsFragment
import org.ole.planet.myplanet.ui.teams.TeamDetailFragment
import org.ole.planet.myplanet.ui.teams.TeamFragment
import org.ole.planet.myplanet.utils.DialogUtils.guestDialog

@AndroidEntryPoint
class BellDashboardFragment : BaseDashboardFragment() {
    private var _binding: FragmentHomeBellBinding? = null
    private val binding get() = _binding!!
    private var networkStatusJob: Job? = null
    private val viewModel: BellDashboardViewModel by viewModels()
    var user: RealmUser? = null
    private var surveyReminderJob: Job? = null
    private var surveyListDialog: AlertDialog? = null
    private var isCoursesExpanded = false
    private var isLibraryExpanded = false
    private var isTeamsExpanded = false
    private var isLifeExpanded = false
    private var collapseCurrentSection: (() -> Unit)? = null

    @Inject
    lateinit var serverUrlMapper: ServerUrlMapper

    companion object {
        private const val PREF_SURVEY_REMINDERS = "survey_reminders"
        private const val KEY_LAST_SURVEY_DIALOG_SHOWN = "last_survey_dialog_shown"
        private val SURVEY_DIALOG_INTERVAL_MS = TimeUnit.HOURS.toMillis(1)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBellBinding.inflate(inflater, container, false)
        val view = binding.root
        declareElements()
        onLoaded(view)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView(view)
        setupNetworkStatusMonitoring()
        (activity as DashboardActivity?)?.supportActionBar?.hide()
        observeCompletedCourses()
        observeCoursePreviewItems()
        observeLibraryPreviewItems()
        observeTeamPreviewItems()
        observeLifePreviewItems()
        observeLastVisitedCourse()
        setGreetingBasedOnTime()
        viewLifecycleOwner.lifecycleScope.launch {
            val wasUserNull = user == null
            user = profileDbHandler.getUserModel()
            user?.id?.let {
                viewModel.loadCompletedCourses(it)
                viewModel.loadCoursePreviewItems(it)
                viewModel.loadLibraryPreviewItems(it)
                viewModel.loadTeamPreviewItems(it)
                viewModel.loadLifePreviewItems(it)
                viewModel.loadLastVisitedCourse(it)
            }
            if (wasUserNull && (user?.id?.startsWith("guest") != true) && !DashboardActivity.isFromNotificationAction) {
                checkPendingSurveys()
            }
            if (user?.id?.startsWith("guest") == false && TextUtils.isEmpty(user?.key)) {
                syncKeyId()
            }
        }
    }

    private fun setupNetworkStatusMonitoring() {
        networkStatusJob?.cancel()
        networkStatusJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.networkStatus.collect { status ->
                    updateNetworkIndicator(status)
                }
            }
        }
    }

    private fun setNetworkIndicatorColor(colorRes: Int) {
        if (isAdded && view?.isAttachedToWindow == true) {
            val color = ContextCompat.getColor(requireContext(), colorRes)
            binding.cardProfileBell.imageView.borderColor = color
        }
    }

    private suspend fun isServerReachable(mapping: ServerUrlMapper.UrlMapping): Boolean {
        val primaryAvailable = viewModel.checkServerConnection(mapping.primaryUrl)

        if (primaryAvailable) {
            return true
        }

        mapping.alternativeUrl?.let {
            val alternativeAvailable = viewModel.checkServerConnection(it)
            return alternativeAvailable
        }

        return false
    }

    private suspend fun handleConnectingState() {
        setNetworkIndicatorColor(R.color.md_yellow_600)
        val updateUrl = prefData.getServerUrl().ifEmpty { return }
        val mapping = serverUrlMapper.processUrl(updateUrl)
        try {
            val reachable = isServerReachable(mapping)
            setNetworkIndicatorColor(if (reachable) R.color.green else R.color.md_yellow_600)
        } catch (e: Exception) {
            e.printStackTrace()
            setNetworkIndicatorColor(R.color.md_yellow_600)
        }
    }

    private suspend fun updateNetworkIndicator(status: NetworkStatus) {
        if (!isAdded) return
        context ?: return

        when (status) {
            is NetworkStatus.Disconnected -> {
                setNetworkIndicatorColor(R.color.md_red_700)
                updateSyncChip(R.drawable.bg_chip_offline, R.drawable.dot_offline, R.string.offline, R.color.chip_offline_ink)
            }
            is NetworkStatus.Connecting -> {
                handleConnectingState()
                updateSyncChip(R.drawable.bg_chip_connecting, R.drawable.dot_connecting, R.string.connecting, R.color.chip_connecting_ink)
            }
            is NetworkStatus.Connected -> {
                setNetworkIndicatorColor(R.color.green)
                updateSyncChip(R.drawable.bg_chip_synced, R.drawable.dot_synced, R.string.online, R.color.chip_synced_ink)
            }
        }
    }

    private fun updateSyncChip(bgRes: Int, dotRes: Int, labelRes: Int, textColorRes: Int) {
        binding.cardProfileBell.chipSync?.setBackgroundResource(bgRes)
        binding.cardProfileBell.dotSync?.setBackgroundResource(dotRes)
        binding.cardProfileBell.txtSyncState?.text = getString(labelRes)
        binding.cardProfileBell.txtSyncState?.setTextColor(ContextCompat.getColor(requireContext(), textColorRes))
    }

    private fun checkPendingSurveys() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (checkScheduledReminders()) return@launch

            val preferences = requireActivity().getSharedPreferences(PREF_SURVEY_REMINDERS, 0)
            val lastShown = preferences.getLong(KEY_LAST_SURVEY_DIALOG_SHOWN, 0L)
            if (System.currentTimeMillis() - lastShown < SURVEY_DIALOG_INTERVAL_MS) return@launch

            val pendingSurveys = submissionsRepository.getUniquePendingSurveys(user?.id)
            if (pendingSurveys.isNotEmpty()) {
                val surveyIds = pendingSurveys.joinToString(",") { it.id.toString() }
                if (preferences.contains("reminder_time_$surveyIds")) return@launch
                val title = getString(
                    R.string.surveys_to_complete,
                    pendingSurveys.size,
                    if (pendingSurveys.size > 1) "surveys" else "survey"
                )
                val surveyTitles = submissionsRepository.getSurveyTitlesFromSubmissions(pendingSurveys)
                showSurveyListDialog(pendingSurveys, title, surveyTitles)
            } else {
                checkScheduledReminders()
            }
        }
    }

    private fun showRemindLaterDialog(pendingSurveys: List<RealmSubmission>,previousDialog: AlertDialog) {
        val dialogView = LayoutInflater.from(requireActivity()).inflate(R.layout.dialog_remind_later, null)
        val radioGroup: RadioGroup = dialogView.findViewById(R.id.radioGroupRemindOptions)
        val numberPicker: NumberPicker = dialogView.findViewById(R.id.numberPickerTime)
        val unitTextView: TextView = dialogView.findViewById(R.id.textViewTimeUnit)

        numberPicker.minValue = 1
        numberPicker.maxValue = 60
        numberPicker.wrapSelectorWheel = false

        radioGroup.check(R.id.radioButtonMinutes)
        unitTextView.text = getString(R.string.minutes)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioButtonMinutes -> {
                    numberPicker.maxValue = 60
                    unitTextView.text = getString(R.string.minutes)
                }
                R.id.radioButtonHours -> {
                    numberPicker.maxValue = 24
                    unitTextView.text = getString(R.string.hours)
                }
                R.id.radioButtonDays -> {
                    numberPicker.maxValue = 30
                    unitTextView.text = getString(R.string.days)
                }
            }
        }

        AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
            .setTitle(getString(R.string.remind_me_later))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.set_reminder)) { dialog, _ ->
                val value = numberPicker.value
                val timeUnit = when (radioGroup.checkedRadioButtonId) {
                    R.id.radioButtonMinutes -> TimeUnit.MINUTES
                    R.id.radioButtonHours -> TimeUnit.HOURS
                    R.id.radioButtonDays -> TimeUnit.DAYS
                    else -> TimeUnit.MINUTES
                }

                scheduleReminder(pendingSurveys, value, timeUnit)
                previousDialog.dismiss()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
            .window?.setBackgroundDrawableResource(R.color.card_bg)
    }

    private fun scheduleReminder(pendingSurveys: List<RealmSubmission>, value: Int, timeUnit: TimeUnit) {
        val currentTime = System.currentTimeMillis()
        val reminderTime = currentTime + timeUnit.toMillis(value.toLong())

        val surveyIds = pendingSurveys.joinToString(",") { it.id.toString() }
        val preferences = requireActivity().getSharedPreferences(PREF_SURVEY_REMINDERS, 0)
        preferences.edit {
            putLong("reminder_time_$surveyIds", reminderTime)
                .putString("reminder_surveys_$surveyIds", surveyIds)
        }

        startReminderCheck()
    }

    private fun startReminderCheck() {
        surveyReminderJob?.cancel()
        surveyReminderJob = lifecycleScope.launch {
            while (isActive) {
                checkScheduledReminders()
                delay(60000)
            }
        }
    }

    private suspend fun checkScheduledReminders(): Boolean {
        val currentTime = System.currentTimeMillis()

        val (remindersToShow, remindersToRemove, preferences) = withContext(Dispatchers.IO) {
            val prefs = requireActivity().getSharedPreferences(PREF_SURVEY_REMINDERS, 0)
            val toShow = mutableListOf<String>()
            val toRemove = mutableListOf<String>()
            for (entry in prefs.all) {
                if (entry.key.startsWith("reminder_time_")) {
                    val surveyIds = entry.key.removePrefix("reminder_time_")
                    val reminderTime = prefs.getLong(entry.key, 0)
                    if (reminderTime <= currentTime) {
                        toShow.add(surveyIds)
                        toRemove.add(surveyIds)
                    }
                }
            }
            Triple(toShow, toRemove, prefs)
        }

        for (surveyIds in remindersToShow) {
            val surveyIdList = surveyIds.split(",").filter { it.isNotBlank() }
            if (surveyIdList.isEmpty()) {
                continue
            }
            val submissions = submissionsRepository.getSubmissionsByIds(surveyIdList)
            val submissionsById = submissions.associateBy { it.id }
            val pendingSurveys = surveyIdList.mapNotNull { submissionsById[it] }
                .filter { it.status == "pending" }

            if (pendingSurveys.isNotEmpty()) {
                showPendingSurveysReminder(pendingSurveys)
            }
        }

        preferences.edit {
            for (surveyIds in remindersToRemove) {
                remove("reminder_time_$surveyIds")
                remove("reminder_surveys_$surveyIds")
            }
        }

        return remindersToShow.isNotEmpty()

    }

    private fun showPendingSurveysReminder(pendingSurveys: List<RealmSubmission>) {
        if (pendingSurveys.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            val title = getString(
                R.string.reminder_surveys_to_complete,
                pendingSurveys.size,
                if (pendingSurveys.size > 1) "surveys" else "survey"
            )
            val surveyTitles = submissionsRepository.getSurveyTitlesFromSubmissions(pendingSurveys)
            showSurveyListDialog(pendingSurveys, title, surveyTitles, dismissOnNeutral = true)
        }
    }

    private fun showSurveyListDialog(
        pendingSurveys: List<RealmSubmission>,
        title: String,
        surveyTitles: List<String>,
        dismissOnNeutral: Boolean = false
    ) {
        val dialogView = LayoutInflater.from(requireActivity()).inflate(R.layout.dialog_survey_list, null)
        val recyclerView: RecyclerView = dialogView.findViewById(R.id.recyclerViewSurveys)
        recyclerView.layoutManager = LinearLayoutManager(requireActivity())

        requireActivity().getSharedPreferences(PREF_SURVEY_REMINDERS, 0)
            .edit { putLong(KEY_LAST_SURVEY_DIALOG_SHOWN, System.currentTimeMillis()) }

        surveyListDialog?.dismiss()
        surveyListDialog = AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                homeItemClickListener?.openCallFragment(SubmissionsFragment.newInstance("survey"))
                dialog.dismiss()
            }
            .setNeutralButton(getString(R.string.remind_later)) { _, _ -> }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
            .create()

        val dialog = surveyListDialog ?: return
        val adapter = DashboardSurveysAdapter({ position ->
            val selectedSurvey = pendingSurveys[position]
            SubmissionsAdapter.openSurvey(homeItemClickListener, selectedSurvey.id, true, false, "")
        }, dialog)
        recyclerView.adapter = adapter
        adapter.submitList(surveyTitles)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.color.card_bg)

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            showRemindLaterDialog(pendingSurveys, dialog)
            if (dismissOnNeutral) dialog.dismiss()
        }
    }

    private fun observeCompletedCourses() {
        binding.cardProfileBell.progressBarBadges?.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.completedCourses.collectLatest { courses ->
                    if (courses.isNotEmpty()) {
                        showBadges(courses)
                        binding.cardProfileBell.progressBarBadges?.visibility = View.GONE
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            delay(2000)
            if (binding.cardProfileBell.progressBarBadges?.visibility == View.VISIBLE) {
                binding.cardProfileBell.progressBarBadges?.visibility = View.GONE
            }
        }
    }


    private fun showBadges(completedCourses: List<CourseCompletion>) {
        binding.cardProfileBell.llBadges.removeAllViews()
        if (completedCourses.isEmpty()) {
            binding.cardProfileBell.llBadges.visibility = View.GONE
            return
        }
        binding.cardProfileBell.llBadges.visibility = View.VISIBLE
        completedCourses.forEachIndexed { index, course ->
            val rootView = requireActivity().findViewById<ViewGroup>(android.R.id.content)
            val star = LayoutInflater.from(activity).inflate(R.layout.image_start, rootView, false) as ImageView
            setColor(course.courseId, star)
            binding.cardProfileBell.llBadges.addView(star)
            star.contentDescription = "${getString(R.string.completed_course)} ${course.courseTitle}"
            star.setOnClickListener {
                openCourse(course, index)
            }
        }
    }

    private fun openCourse(course: CourseCompletion, position: Int) {
        if (homeItemClickListener != null) {
            val f: Fragment = TakeCourseFragment()
            val b = Bundle()
            b.putString("id", course.courseId)
            b.putInt("position", position)
            f.arguments = b
            homeItemClickListener?.openCallFragment(f)
        }
    }

    private fun setColor(courseId: String?, star: ImageView) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (courseId != null && coursesRepository.isCourseCertified(courseId)) {
                star.setColorFilter(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
            } else {
                star.setColorFilter(ContextCompat.getColor(requireContext(), R.color.md_blue_grey_300))
            }
        }
    }

    private fun observeLibraryPreviewItems() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.libraryPreviewItems.collectLatest { items ->
                    val subline = if (items.isEmpty()) "" else "${items.size} recent"
                    binding.homeCardLibrary.txtLibrarySubline.text = subline
                    populatePreviewRows(
                        container = binding.homeCardLibrary.containerLibraryPreviews,
                        items = items.take(3).map { PreviewRow(it.title, it.subline, R.drawable.library, R.color.cat_library_accent, R.drawable.bg_category_library) },
                        onClickItem = { homeItemClickListener?.openMyFragment(ResourcesFragment()) }
                    )
                    setupSectionExpandCollapse(
                        card = binding.homeCardLibrary.root as? MaterialCardView ?: return@collectLatest,
                        header = binding.homeCardLibrary.myLibraryImageButton,
                        chevron = binding.homeCardLibrary.chevronLibrary,
                        container = binding.homeCardLibrary.containerLibraryPreviews,
                        openBtn = binding.homeCardLibrary.btnOpenLibrary,
                        accentColorRes = R.color.cat_library_accent,
                        hasItems = items.isNotEmpty(),
                        isExpandedProvider = { isLibraryExpanded },
                        setExpanded = { isLibraryExpanded = it },
                        onOpenAll = {
                            if (user?.id?.startsWith("guest") == true) guestDialog(requireContext(), profileDbHandler)
                            else homeItemClickListener?.openMyFragment(ResourcesFragment())
                        },
                        onHeaderFallback = {
                            if (user?.id?.startsWith("guest") == true) guestDialog(requireContext(), profileDbHandler)
                            else homeItemClickListener?.openMyFragment(ResourcesFragment())
                        }
                    )
                }
            }
        }
    }

    private fun observeTeamPreviewItems() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.teamPreviewItems.collectLatest { items ->
                    val subline = if (items.isEmpty()) "" else "${items.size} active"
                    binding.homeCardTeams.txtTeamsSubline.text = subline
                    populatePreviewRows(
                        container = binding.homeCardTeams.containerTeamsPreviews,
                        items = items.take(3).map { PreviewRow(it.name, it.teamType.replaceFirstChar { c -> c.uppercase() }, R.drawable.team, R.color.cat_teams_accent, R.drawable.bg_category_teams) },
                        onClickItem = { homeItemClickListener?.openMyFragment(
                            org.ole.planet.myplanet.ui.teams.TeamFragment().apply {
                                arguments = Bundle().apply { putBoolean("fromDashboard", true) }
                            }
                        )}
                    )
                    setupSectionExpandCollapse(
                        card = binding.homeCardTeams.root as? MaterialCardView ?: return@collectLatest,
                        header = binding.homeCardTeams.llHomeTeam,
                        chevron = binding.homeCardTeams.chevronTeams,
                        container = binding.homeCardTeams.containerTeamsPreviews,
                        openBtn = binding.homeCardTeams.btnOpenTeams,
                        accentColorRes = R.color.cat_teams_accent,
                        hasItems = items.isNotEmpty(),
                        isExpandedProvider = { isTeamsExpanded },
                        setExpanded = { isTeamsExpanded = it },
                        onOpenAll = {
                            homeItemClickListener?.openMyFragment(
                                org.ole.planet.myplanet.ui.teams.TeamFragment().apply {
                                    arguments = Bundle().apply { putBoolean("fromDashboard", true) }
                                }
                            )
                        },
                        onHeaderFallback = {
                            homeItemClickListener?.openMyFragment(
                                org.ole.planet.myplanet.ui.teams.TeamFragment().apply {
                                    arguments = Bundle().apply { putBoolean("fromDashboard", true) }
                                }
                            )
                        }
                    )
                }
            }
        }
    }

    private fun observeLifePreviewItems() {
        val lifeIconMap = mapOf(
            "ic_myhealth" to R.drawable.ic_myhealth,
            "my_achievement" to R.drawable.my_achievement,
            "ic_submissions" to R.drawable.ic_submissions,
            "ic_my_survey" to R.drawable.ic_my_survey,
            "ic_references" to R.drawable.ic_references,
            "ic_calendar" to R.drawable.ic_calendar,
            "ic_mypersonals" to R.drawable.ic_mypersonals
        )
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.lifePreviewItems.collectLatest { items ->
                    val subline = if (items.isEmpty()) "" else "${items.size} categories"
                    binding.homeCardMyLife.txtLifeSubline.text = subline
                    populatePreviewRows(
                        container = binding.homeCardMyLife.containerLifePreviews,
                        items = items.take(3).map { l ->
                            PreviewRow(l.title, "", lifeIconMap[l.imageId] ?: R.drawable.ic_myhealth, R.color.cat_life_accent, R.drawable.bg_category_life)
                        },
                        onClickItem = { homeItemClickListener?.openCallFragment(LifeFragment()) }
                    )
                    setupSectionExpandCollapse(
                        card = binding.homeCardMyLife.root as? MaterialCardView ?: return@collectLatest,
                        header = binding.homeCardMyLife.myLifeImageButton,
                        chevron = binding.homeCardMyLife.chevronLife,
                        container = binding.homeCardMyLife.containerLifePreviews,
                        openBtn = binding.homeCardMyLife.btnOpenLife,
                        accentColorRes = R.color.cat_life_accent,
                        hasItems = items.isNotEmpty(),
                        isExpandedProvider = { isLifeExpanded },
                        setExpanded = { isLifeExpanded = it },
                        onOpenAll = { homeItemClickListener?.openCallFragment(LifeFragment()) },
                        onHeaderFallback = { homeItemClickListener?.openCallFragment(LifeFragment()) }
                    )
                }
            }
        }
    }

    private data class PreviewRow(
        val title: String,
        val subline: String,
        val iconRes: Int,
        val iconTintRes: Int,
        val bgRes: Int
    )

    private fun populatePreviewRows(
        container: android.widget.LinearLayout,
        items: List<PreviewRow>,
        onClickItem: () -> Unit
    ) {
        while (container.childCount > 1) container.removeViewAt(1)
        for (item in items) {
            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_dashboard_preview_row, container, false)
            val iconBg = row.findViewById<android.widget.FrameLayout>(R.id.preview_icon_bg)
            val icon = row.findViewById<ImageView>(R.id.preview_icon)
            val title = row.findViewById<TextView>(R.id.txt_preview_title)
            val subline = row.findViewById<TextView>(R.id.txt_preview_subline)
            iconBg.setBackgroundResource(item.bgRes)
            icon.setImageResource(item.iconRes)
            icon.setColorFilter(ContextCompat.getColor(requireContext(), item.iconTintRes))
            title.text = item.title
            if (item.subline.isNotBlank()) {
                subline.text = item.subline
                subline.setTextColor(ContextCompat.getColor(requireContext(), item.iconTintRes))
                subline.visibility = View.VISIBLE
            } else {
                subline.visibility = View.GONE
            }
            row.setOnClickListener { onClickItem() }
            container.addView(row)
        }
    }

    private fun setupSectionExpandCollapse(
        card: MaterialCardView,
        header: View,
        chevron: ImageView,
        container: android.widget.LinearLayout,
        openBtn: TextView,
        accentColorRes: Int,
        hasItems: Boolean,
        isExpandedProvider: () -> Boolean,
        setExpanded: (Boolean) -> Unit,
        onOpenAll: () -> Unit,
        onHeaderFallback: () -> Unit
    ) {
        val accentColor = ContextCompat.getColor(requireContext(), accentColorRes)
        val defaultStroke = ContextCompat.getColor(requireContext(), R.color.dash_line)

        fun collapse() {
            setExpanded(false)
            ObjectAnimator.ofFloat(chevron, "rotation", chevron.rotation, 0f).setDuration(200).start()
            container.visibility = View.GONE
            openBtn.visibility = View.GONE
            card.strokeColor = defaultStroke
        }

        if (!hasItems) {
            header.setOnClickListener { onHeaderFallback() }
            return
        }

        header.setOnClickListener {
            if (isExpandedProvider()) {
                collapse()
                collapseCurrentSection = null
            } else {
                collapseCurrentSection?.invoke()
                setExpanded(true)
                ObjectAnimator.ofFloat(chevron, "rotation", chevron.rotation, 180f).setDuration(200).start()
                container.visibility = View.VISIBLE
                openBtn.visibility = View.VISIBLE
                card.strokeColor = accentColor
                collapseCurrentSection = ::collapse
            }
        }

        openBtn.setOnClickListener { onOpenAll() }
    }

    private fun observeLastVisitedCourse() {
        binding.homeContinue.root.visibility = View.GONE
        binding.txtYourLearning.visibility = View.GONE
        binding.dashTitle.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.lastVisitedCourse.collectLatest { info ->
                    val visible = if (info == null) View.GONE else View.VISIBLE
                    binding.homeContinue.root.visibility = visible
                    binding.txtYourLearning.visibility = visible
                    binding.dashTitle.visibility = visible
                    if (info == null) return@collectLatest
                    binding.homeContinue.txtContinueTitle.text = info.courseTitle
                    val metaText = when {
                        info.progressPercent >= 100 -> getString(R.string.course_completed_label)
                        info.progressPercent > 0 -> "${info.progressPercent}% ${getString(R.string.complete_label)}"
                        else -> getString(R.string.not_started_label)
                    }
                    binding.homeContinue.txtContinueMeta.text = metaText
                    binding.homeContinue.progressContinue.progress = info.progressPercent
                    binding.homeContinue.txtContinuePercent.text = "${info.progressPercent}%"
                    binding.homeContinue.root.setOnClickListener {
                        val f = TakeCourseFragment()
                        f.arguments = Bundle().apply { putString("id", info.courseId) }
                        homeItemClickListener?.openCallFragment(f)
                    }
                }
            }
        }
    }

    private fun observeCoursePreviewItems() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.coursePreviewItems.collectLatest { items ->
                    val inProgress = items.count { it.progressPercent in 1..99 }
                    val toStart = items.count { it.progressPercent == 0 }
                    binding.homeCardCourses.txtCoursesSubline.text = buildString {
                        if (inProgress > 0) append("$inProgress in progress")
                        if (inProgress > 0 && toStart > 0) append(" · ")
                        if (toStart > 0) append("$toStart to start")
                    }
                    populateCoursePreviewRows(items.take(3))
                    setupCoursesExpandCollapse(items.isNotEmpty())
                }
            }
        }
    }

    private fun populateCoursePreviewRows(items: List<CoursePreviewItem>) {
        val container = binding.homeCardCourses.containerCoursePreviews
        // Remove all but the first child (the divider View)
        while (container.childCount > 1) container.removeViewAt(1)

        for (item in items) {
            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_dashboard_course_preview, container, false)
            row.findViewById<TextView>(R.id.txt_course_preview_title).text = item.courseTitle
            row.findViewById<TextView>(R.id.txt_course_preview_percent).text =
                "${item.progressPercent}%"
            row.findViewById<ProgressBar>(R.id.progress_course_preview).progress = item.progressPercent
            row.setOnClickListener {
                val f = org.ole.planet.myplanet.ui.courses.TakeCourseFragment()
                f.arguments = Bundle().apply { putString("id", item.courseId) }
                homeItemClickListener?.openCallFragment(f)
            }
            container.addView(row)
        }
    }

    private fun setupCoursesExpandCollapse(hasItems: Boolean) {
        setupSectionExpandCollapse(
            card = binding.homeCardCourses.root as? MaterialCardView ?: return,
            header = binding.homeCardCourses.myCoursesImageButton,
            chevron = binding.homeCardCourses.chevronCourses,
            container = binding.homeCardCourses.containerCoursePreviews,
            openBtn = binding.homeCardCourses.btnOpenCourses,
            accentColorRes = R.color.cat_courses_accent,
            hasItems = hasItems,
            isExpandedProvider = { isCoursesExpanded },
            setExpanded = { isCoursesExpanded = it },
            onOpenAll = {
                if (user?.id?.startsWith("guest") == true) guestDialog(requireContext(), profileDbHandler)
                else homeItemClickListener?.openMyFragment(CoursesFragment())
            },
            onHeaderFallback = {
                if (user?.id?.startsWith("guest") == true) guestDialog(requireContext(), profileDbHandler)
                else homeItemClickListener?.openMyFragment(CoursesFragment())
            }
        )
    }

    private fun setGreetingBasedOnTime() {
        val timeOfDay = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        binding.cardProfileBell.txtGreeting?.text = when {
            timeOfDay < 12 -> getString(R.string.good_morning)
            timeOfDay < 16 -> getString(R.string.good_afternoon)
            timeOfDay < 21 -> getString(R.string.good_evening)
            else -> getString(R.string.good_night)
        }
    }

    private fun declareElements() {
        // Library, Courses, Teams, MyLife header clicks are wired in their observe* methods
        binding.fabMyActivity.setOnClickListener { openHelperFragment(ActivitiesFragment()) }
    }

    private fun openHelperFragment(f: Fragment) {
        val b = Bundle()
        b.putBoolean("isMyCourseLib", true)
        f.arguments = b
        homeItemClickListener?.openCallFragment(f)
    }

    override fun onResume() {
        super.onResume()
        user?.let { u ->
            if (u.id?.startsWith("guest") != true && !DashboardActivity.isFromNotificationAction) {
                checkPendingSurveys()
            }
        }
    }

    override fun onPause() {
        surveyListDialog?.dismiss()
        surveyListDialog = null
        super.onPause()
    }

    override fun onDestroyView() {
        surveyListDialog?.dismiss()
        surveyListDialog = null
        networkStatusJob?.cancel()
        surveyReminderJob?.cancel()
        super.onDestroyView()
        _binding = null
    }

    override fun handleClick(id: String?, title: String?, f: Fragment, v: TextView) {
        if (f is TeamDetailFragment) {
            v.text = title
            v.setOnClickListener {
                lifecycleScope.launch {
                    val teamObject = id?.let { viewModel.getTeamById(it) }
                    val optimizedFragment = TeamDetailFragment.newInstance(
                        teamId = id ?: "",
                        teamName = title ?: "",
                        teamType = teamObject?.type ?: "",
                        isMyTeam = true
                    )
                    prefData.setTeamName(title)
                    homeItemClickListener?.openCallFragment(optimizedFragment)
                }
            }
        } else {
            super.handleClick(id, title, f, v)
        }
    }
}
