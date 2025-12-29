package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentHomeBellBinding
import org.ole.planet.myplanet.model.RealmCertification
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.sync.ServerUrlMapper
import org.ole.planet.myplanet.ui.courses.CoursesFragment
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment
import org.ole.planet.myplanet.ui.mylife.LifeFragment
import org.ole.planet.myplanet.ui.resources.ResourcesFragment
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission
import org.ole.planet.myplanet.ui.submission.MySubmissionFragment
import org.ole.planet.myplanet.ui.survey.SurveyTitlesAdapter
import org.ole.planet.myplanet.ui.team.TeamFragment
import org.ole.planet.myplanet.utilities.DialogUtils.guestDialog

class BellDashboardFragment : BaseDashboardFragment() {
    private var _binding: FragmentHomeBellBinding? = null
    private val binding get() = _binding!!
    private var networkStatusJob: Job? = null
    private val viewModel: BellDashboardViewModel by viewModels()
    var user: RealmUserModel? = null
    private var surveyReminderJob: Job? = null
    private var surveyListDialog: AlertDialog? = null

    companion object {
        private const val PREF_SURVEY_REMINDERS = "survey_reminders"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBellBinding.inflate(inflater, container, false)
        val view = binding.root
        declareElements()
        onLoaded(view)
        user = profileDbHandler?.userModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView(view)
        binding.cardProfileBell.txtCommunityName.text = model?.planetCode
        setupNetworkStatusMonitoring()
        (activity as DashboardActivity?)?.supportActionBar?.hide()
        observeCompletedCourses()
        if((user?.id?.startsWith("guest") != true) && !DashboardActivity.isFromNotificationAction) {
            checkPendingSurveys()
        }
        if (model?.id?.startsWith("guest") == false && TextUtils.isEmpty(model?.key)) {
            syncKeyId()
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
        val updateUrl = settings.getString("serverURL", "") ?: return
        val mapping = ServerUrlMapper().processUrl(updateUrl)
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
            is NetworkStatus.Disconnected -> setNetworkIndicatorColor(R.color.md_red_700)
            is NetworkStatus.Connecting -> handleConnectingState()
            is NetworkStatus.Connected -> setNetworkIndicatorColor(R.color.green)
        }
    }

    private fun checkPendingSurveys() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (checkScheduledReminders()) {
                return@launch
            }
            val pendingSurveys = submissionRepository.getUniquePendingSurveys(user?.id)

            if (pendingSurveys.isNotEmpty()) {
                val surveyIds = pendingSurveys.joinToString(",") { it.id.toString() }
                val preferences = requireActivity().getSharedPreferences(PREF_SURVEY_REMINDERS, 0)
                if (preferences.contains("reminder_time_$surveyIds")) {
                    return@launch
                }
                val title = getString(
                    R.string.surveys_to_complete,
                    pendingSurveys.size,
                    if (pendingSurveys.size > 1) "surveys" else "survey"
                )
                val surveyTitles = submissionRepository.getSurveyTitlesFromSubmissions(pendingSurveys)
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
        preferences.edit()
            .putLong("reminder_time_$surveyIds", reminderTime)
            .putString("reminder_surveys_$surveyIds", surveyIds)
            .apply()

        val unitString = when (timeUnit) {
            TimeUnit.MINUTES -> resources.getQuantityString(R.plurals.minutes, value, value)
            TimeUnit.HOURS -> resources.getQuantityString(R.plurals.hours, value, value)
            TimeUnit.DAYS -> resources.getQuantityString(R.plurals.days, value, value)
            else -> "$value ${timeUnit.name.lowercase()}"
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
        val preferences = requireActivity().getSharedPreferences(PREF_SURVEY_REMINDERS, 0)
        val currentTime = System.currentTimeMillis()

        val remindersToShow = mutableListOf<String>()
        val remindersToRemove = mutableListOf<String>()

        for (entry in preferences.all) {
            if (entry.key.startsWith("reminder_time_")) {
                val surveyIds = entry.key.removePrefix("reminder_time_")
                val reminderTime = preferences.getLong(entry.key, 0)

                if (reminderTime <= currentTime) {
                    remindersToShow.add(surveyIds)
                    remindersToRemove.add(surveyIds)
                }
            }
        }

        for (surveyIds in remindersToShow) {
            val surveyIdList = surveyIds.split(",").filter { it.isNotBlank() }
            if (surveyIdList.isEmpty()) {
                continue
            }
            val submissions = submissionRepository.getSubmissionsByIds(surveyIdList)
            val submissionsById = submissions.associateBy { it.id }
            val pendingSurveys = surveyIdList.mapNotNull { submissionsById[it] }

            if (pendingSurveys.isNotEmpty()) {
                showPendingSurveysReminder(pendingSurveys)
            }
        }

        val editor = preferences.edit()
        for (surveyIds in remindersToRemove) {
            editor.remove("reminder_time_$surveyIds")
            editor.remove("reminder_surveys_$surveyIds")
        }
        editor.apply()

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
            val surveyTitles = submissionRepository.getSurveyTitlesFromSubmissions(pendingSurveys)
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

        surveyListDialog?.dismiss()
        surveyListDialog = AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                homeItemClickListener?.openCallFragment(MySubmissionFragment.newInstance("survey"))
                dialog.dismiss()
            }
            .setNeutralButton(getString(R.string.remind_later)) { _, _ -> }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
            .create()

        val adapter = SurveyTitlesAdapter({ position ->
            val selectedSurvey = pendingSurveys[position].id
            AdapterMySubmission.openSurvey(homeItemClickListener, selectedSurvey, true, false, "")
        }, surveyListDialog!!)
        recyclerView.adapter = adapter
        adapter.submitList(surveyTitles)
        surveyListDialog?.show()
        surveyListDialog?.window?.setBackgroundDrawableResource(R.color.card_bg)

        surveyListDialog?.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            showRemindLaterDialog(pendingSurveys, surveyListDialog!!)
            if (dismissOnNeutral) surveyListDialog?.dismiss()
        }
    }

    private fun observeCompletedCourses() {
        binding.cardProfileBell.progressBarBadges?.visibility = View.VISIBLE
        viewModel.loadCompletedCourses(user?.id)

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
        if (isRealmInitialized() && RealmCertification.isCourseCertified(mRealm, courseId)) {
            star.setColorFilter(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
        } else {
            star.setColorFilter(ContextCompat.getColor(requireContext(), R.color.md_blue_grey_300))
        }
    }

    private fun declareElements() {
        binding.homeCardTeams.llHomeTeam.setOnClickListener {
            val fragment = TeamFragment().apply {
                arguments = Bundle().apply {
                    putBoolean("fromDashboard", true)
                }
            }
            homeItemClickListener?.openMyFragment(fragment)
        }
        binding.homeCardLibrary.myLibraryImageButton.setOnClickListener {
            if (user?.id?.startsWith("guest") == true) {
                guestDialog(requireContext(), profileDbHandler)
            } else {
                homeItemClickListener?.openMyFragment(ResourcesFragment())
            }
        }
        binding.homeCardCourses.myCoursesImageButton.setOnClickListener {
            if (user?.id?.startsWith("guest") == true) {
                guestDialog(requireContext(), profileDbHandler)
            } else {
                homeItemClickListener?.openMyFragment(CoursesFragment())
            }
        }
        binding.fabMyActivity.setOnClickListener { openHelperFragment(MyActivityFragment()) }
        binding.homeCardMyLife.myLifeImageButton.setOnClickListener { homeItemClickListener?.openCallFragment(LifeFragment()) }
    }

    private fun openHelperFragment(f: Fragment) {
        val b = Bundle()
        b.putBoolean("isMyCourseLib", true)
        f.arguments = b
        homeItemClickListener?.openCallFragment(f)
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
}
