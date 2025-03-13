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
import io.realm.Case
import io.realm.Realm
import kotlinx.coroutines.*
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentHomeBellBinding
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.courses.CoursesFragment
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment
import org.ole.planet.myplanet.ui.feedback.FeedbackListFragment
import org.ole.planet.myplanet.ui.mylife.LifeFragment
import org.ole.planet.myplanet.ui.resources.ResourcesFragment
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission
import org.ole.planet.myplanet.ui.submission.MySubmissionFragment
import org.ole.planet.myplanet.ui.team.TeamFragment
import org.ole.planet.myplanet.utilities.DialogUtils.guestDialog
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.TimeUtils
import java.util.*
import java.util.concurrent.TimeUnit

class BellDashboardFragment : BaseDashboardFragment() {
    private lateinit var fragmentHomeBellBinding: FragmentHomeBellBinding
    private var networkStatusJob: Job? = null
    private val viewModel: BellDashboardViewModel by viewModels()
    var user: RealmUserModel? = null
    private var surveyReminderJob: Job? = null

    companion object {
        private const val PREF_SURVEY_REMINDERS = "survey_reminders"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentHomeBellBinding = FragmentHomeBellBinding.inflate(inflater, container, false)
        user = UserProfileDbHandler(requireContext()).userModel
        val view = fragmentHomeBellBinding.root
        initView(view)
        declareElements()
        onLoaded(view)
        return fragmentHomeBellBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentHomeBellBinding.cardProfileBell.txtDate.text = TimeUtils.formatDate(Date().time, "")
        fragmentHomeBellBinding.cardProfileBell.txtCommunityName.text = model?.planetCode
        setupNetworkStatusMonitoring()
        (activity as DashboardActivity?)?.supportActionBar?.hide()
        showBadges()
        if((user?.id?.startsWith("guest") != true)) {
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

    private suspend fun updateNetworkIndicator(status: NetworkStatus) {
        if (!isAdded) return
        val context = context ?: return

        when (status) {
            is NetworkStatus.Disconnected -> {
                fragmentHomeBellBinding.cardProfileBell.imageView.borderColor =
                    ContextCompat.getColor(context, R.color.md_red_700)
            }
            is NetworkStatus.Connecting -> {
                fragmentHomeBellBinding.cardProfileBell.imageView.borderColor =
                    ContextCompat.getColor(context, R.color.md_yellow_600)

                val updateUrl = settings?.getString("serverURL", "") ?: return
                val serverUrlMapper = ServerUrlMapper(requireContext())
                val mapping = serverUrlMapper.processUrl(updateUrl)

                val serverCheckPrimary = lifecycleScope.async(Dispatchers.IO) {
                    viewModel.checkServerConnection(mapping.primaryUrl)
                }
                val serverCheckAlternative = mapping.alternativeUrl?.let {
                    lifecycleScope.async(Dispatchers.IO) { viewModel.checkServerConnection(it) }
                }

                try {
                    val primaryAvailable = serverCheckPrimary.await()
                    val alternativeAvailable = serverCheckAlternative?.await() == true

                    val isServerReachable = primaryAvailable || alternativeAvailable

                    if (isAdded && view?.isAttachedToWindow == true) {
                        fragmentHomeBellBinding.cardProfileBell.imageView.borderColor =
                            ContextCompat.getColor(context, if (isServerReachable) R.color.green else R.color.md_yellow_600)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (isAdded && view?.isAttachedToWindow == true) {
                        fragmentHomeBellBinding.cardProfileBell.imageView.borderColor =
                            ContextCompat.getColor(context, R.color.md_yellow_600)
                    }
                }
            }
            is NetworkStatus.Connected -> {
                fragmentHomeBellBinding.cardProfileBell.imageView.borderColor = ContextCompat.getColor(context, R.color.green)
            }
        }
    }

    private fun checkPendingSurveys() {
        if (checkScheduledReminders()) {
            return
        }
        val pendingSurveys = getPendingSurveys(user?.id, mRealm)

        if (pendingSurveys.isNotEmpty()) {
            val surveyIds = pendingSurveys.joinToString(",") { it.id.toString() }
            val preferences = requireActivity().getSharedPreferences(PREF_SURVEY_REMINDERS, 0)
            if (preferences.contains("reminder_time_$surveyIds")) {
                return
            }
            val surveyTitles = getSurveyTitlesFromSubmissions(pendingSurveys, mRealm)

            val dialogView = LayoutInflater.from(requireActivity()).inflate(R.layout.dialog_survey_list, null)
            val recyclerView: RecyclerView = dialogView.findViewById(R.id.recyclerViewSurveys)
            recyclerView.layoutManager = LinearLayoutManager(requireActivity())

            val alertDialog = AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
                .setTitle(getString(R.string.surveys_to_complete, pendingSurveys.size, if (pendingSurveys.size > 1) "surveys" else "survey"))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                    homeItemClickListener?.openCallFragment(MySubmissionFragment.newInstance("survey"))
                    dialog.dismiss()
                }
                .setNeutralButton(getString(R.string.remind_later)) { _, _ ->
                }
                .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()

            val adapter = SurveyAdapter(surveyTitles, { position ->
                val selectedSurvey = pendingSurveys[position].id
                AdapterMySubmission.openSurvey(homeItemClickListener, selectedSurvey, true, false, "")
            }, alertDialog)

            recyclerView.adapter = adapter
            alertDialog.show()
            alertDialog.window?.setBackgroundDrawableResource(R.color.card_bg)

            alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                showRemindLaterDialog(pendingSurveys)
            }
        } else {
            checkScheduledReminders()
        }
    }

    private fun showRemindLaterDialog(pendingSurveys: List<RealmSubmission>) {
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

    private fun checkScheduledReminders(): Boolean {
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
            val surveyIdList = surveyIds.split(",")
            val pendingSurveys = surveyIdList.mapNotNull { id ->
                mRealm.where(RealmSubmission::class.java)
                    .equalTo("id", id)
                    .findFirst()
            }

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
        val surveyTitles = getSurveyTitlesFromSubmissions(pendingSurveys, mRealm)

        val dialogView = LayoutInflater.from(requireActivity()).inflate(R.layout.dialog_survey_list, null)
        val recyclerView: RecyclerView = dialogView.findViewById(R.id.recyclerViewSurveys)
        recyclerView.layoutManager = LinearLayoutManager(requireActivity())

        val alertDialog = AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
            .setTitle(getString(R.string.reminder_surveys_to_complete, pendingSurveys.size,
                if (pendingSurveys.size > 1) "surveys" else "survey"))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                homeItemClickListener?.openCallFragment(MySubmissionFragment.newInstance("survey"))
                dialog.dismiss()
            }
            .setNeutralButton(getString(R.string.remind_later)) { _, _ ->
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        val adapter = SurveyAdapter(surveyTitles, { position ->
            val selectedSurvey = pendingSurveys[position].id
            AdapterMySubmission.openSurvey(homeItemClickListener, selectedSurvey, true, false, "")
        }, alertDialog)

        recyclerView.adapter = adapter
        alertDialog.show()
        alertDialog.window?.setBackgroundDrawableResource(R.color.card_bg)

        alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            showRemindLaterDialog(pendingSurveys)
            alertDialog.dismiss()
        }
    }

    private fun getPendingSurveys(userId: String?, realm: Realm): List<RealmSubmission> {
        return realm.where(RealmSubmission::class.java)
            .equalTo("userId", userId)
            .equalTo("type", "survey")
            .equalTo("status", "pending", Case.INSENSITIVE)
            .findAll()
    }

    private fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>, realm: Realm): List<String> {
        val titles = mutableListOf<String>()
        submissions.forEach { submission ->
            val examId = submission.parentId?.split("@")?.firstOrNull() ?: ""
            val exam = realm.where(RealmStepExam::class.java)
                .equalTo("id", examId)
                .findFirst()
            exam?.name?.let { titles.add(it) }
        }
        return titles
    }

    private fun showBadges() {
        fragmentHomeBellBinding.cardProfileBell.llBadges.removeAllViews()
        val completedCourses = getCompletedCourses(mRealm, user?.id)
        completedCourses.forEachIndexed { index, course ->
            val rootView = requireActivity().findViewById<ViewGroup>(android.R.id.content)
            val star = LayoutInflater.from(activity).inflate(R.layout.image_start, rootView, false) as ImageView
            setColor(course.courseId, star)
            fragmentHomeBellBinding.cardProfileBell.llBadges.addView(star)
            star.contentDescription = "${getString(R.string.completed_course)} ${course.courseTitle}"
            star.setOnClickListener {
                openCourse(course, index)
            }
        }
    }

    private fun getCompletedCourses(realm: Realm, userId: String?): List<RealmMyCourse> {
        val myCourses = RealmMyCourse.getMyCourseByUserId(userId, realm.where(RealmMyCourse::class.java).findAll())
        val courseProgress = RealmCourseProgress.getCourseProgress(realm, userId)

        return myCourses.filter { course ->
            val progress = courseProgress[course.id]
            progress?.let {
                it.asJsonObject["current"].asInt == it.asJsonObject["max"].asInt
            } == true
        }
    }

    private fun openCourse(realmMyCourses: RealmMyCourse?, position: Int) {
        if (homeItemClickListener != null) {
            val f: Fragment = TakeCourseFragment()
            val b = Bundle()
            b.putString("id", realmMyCourses?.courseId)
            b.putInt("position", position)
            f.arguments = b
            homeItemClickListener?.openCallFragment(f)
        }
    }

    private fun setColor(courseId: String?, star: ImageView) {
        if (RealmCertification.isCourseCertified(mRealm, courseId)) {
            star.setColorFilter(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
        } else {
            star.setColorFilter(ContextCompat.getColor(requireContext(), R.color.md_blue_grey_300))
        }
    }

    private fun declareElements() {
        fragmentHomeBellBinding.homeCardTeams.llHomeTeam.setOnClickListener { homeItemClickListener?.openMyFragment(TeamFragment()) }
        fragmentHomeBellBinding.homeCardLibrary.myLibraryImageButton.setOnClickListener {
            if (user?.id?.startsWith("guest") == true) {
                guestDialog(requireContext())
            } else {
                homeItemClickListener?.openMyFragment(ResourcesFragment())
            }
        }
        fragmentHomeBellBinding.homeCardCourses.myCoursesImageButton.setOnClickListener {
            if (user?.id?.startsWith("guest") == true) {
                guestDialog(requireContext())
            } else {
                homeItemClickListener?.openMyFragment(CoursesFragment())
            }
        }
        fragmentHomeBellBinding.fabMyActivity.setOnClickListener { openHelperFragment(MyActivityFragment()) }
        fragmentHomeBellBinding.cardProfileBell.fabFeedback.setOnClickListener { openHelperFragment(FeedbackListFragment()) }
        fragmentHomeBellBinding.homeCardMyLife.myLifeImageButton.setOnClickListener { homeItemClickListener?.openCallFragment(LifeFragment()) }
    }

    private fun openHelperFragment(f: Fragment) {
        val b = Bundle()
        b.putBoolean("isMyCourseLib", true)
        f.arguments = b
        homeItemClickListener?.openCallFragment(f)
    }

    override fun onDestroyView() {
        networkStatusJob?.cancel()
        surveyReminderJob?.cancel()
        super.onDestroyView()
    }
}