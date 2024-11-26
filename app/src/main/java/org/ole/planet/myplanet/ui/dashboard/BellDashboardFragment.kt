package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
import kotlinx.coroutines.flow.filter
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentHomeBellBinding
import org.ole.planet.myplanet.model.RealmCertification
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.courses.CoursesFragment
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment
import org.ole.planet.myplanet.ui.feedback.FeedbackListFragment
import org.ole.planet.myplanet.ui.mylife.LifeFragment
import org.ole.planet.myplanet.ui.resources.ResourcesFragment
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission
import org.ole.planet.myplanet.ui.submission.MySubmissionFragment
import org.ole.planet.myplanet.ui.sync.ProcessUserDataActivity
import org.ole.planet.myplanet.ui.team.TeamFragment
import org.ole.planet.myplanet.utilities.DialogUtils.guestDialog
import org.ole.planet.myplanet.utilities.TimeUtils
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

class BellDashboardFragment : BaseDashboardFragment() {
    private lateinit var fragmentHomeBellBinding: FragmentHomeBellBinding
    private var networkStatusJob: Job? = null
    private val serverCheckCache = ConcurrentHashMap<String, Long>()
    private val cacheDuration = 5 * 60 * 1000L
    private val serverCheckTimeout = 3000L
    private val viewModel: BellDashboardViewModel by viewModels()
    var user: RealmUserModel? = null

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
        checkPendingSurveys()
    }

    private fun getOptimizedServerUrls(): List<String> {
        val primaryServer = settings?.getString("serverURL", "")
        val lastReachableServer = settings?.getString("last_reachable_server", "")

        return listOfNotNull(
            lastReachableServer,
            primaryServer
        ) + listOf(
            "http://example.com/server1",
            "http://35.231.161.29",
            "https://example.com/server3",
            "http://example.com/server4",
            "https://example.com/server5",
            "https://example.com/server6",
            "http://example.com/server7",
            "https://example.com/server8",
            "https://example.com/server9"
        ).filterNot { it == lastReachableServer || it == primaryServer }
    }

    private suspend fun checkServerWithCache(url: String): Boolean {
        val currentTime = System.currentTimeMillis()
        return serverCheckCache[url]?.let { lastChecked ->
            if (currentTime - lastChecked < cacheDuration) {
                true
            } else {
                viewModel.checkServerConnection(url).also {
                    serverCheckCache[url] = currentTime
                }
            }
        } ?: viewModel.checkServerConnection(url).also {
            serverCheckCache[url] = currentTime
        }
    }

    private fun setupNetworkStatusMonitoring() {
        networkStatusJob?.cancel()
        networkStatusJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            var lastEmittedStatus: NetworkStatus? = null
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.networkStatus
                    .filter { status ->
                        status != lastEmittedStatus.also {
                            lastEmittedStatus = status
                        }
                    }
                    .collect { status ->
                        withContext(Dispatchers.Main) {
                            updateNetworkIndicator(status)
                        }
                    }
            }
        }
    }

    private suspend fun updateNetworkIndicator(status: NetworkStatus) = coroutineScope {
        // Validate fragment and context
        if (!isAdded || context == null) return@coroutineScope

        // Get optimized server URLs
        val serverUrls = getOptimizedServerUrls()

        // Set initial color to yellow while checking
        fragmentHomeBellBinding.cardProfileBell.imageView.borderColor =
            ContextCompat.getColor(requireContext(), R.color.md_yellow_600)

        // Find reachable server with timeout
        val reachableServer = try {
            withTimeout(serverCheckTimeout) {
                serverUrls.firstNotNullOfOrNull { url ->
                    async {
                        if (checkServerWithCache(url)) url else null
                    }.await()
                }
            }
        } catch (e: TimeoutCancellationException) {
            e.printStackTrace()
            null
        }

        // Update last reachable server in settings
        reachableServer?.let {
            editor?.putString("last_reachable_server", it)?.apply()
        }

        // Determine color based on server reachability
        if (isAdded && view?.isAttachedToWindow == true) {
            val colorRes = when {
                reachableServer != null -> {
                    val processedUrl = ProcessUserDataActivity.setUrlParts(reachableServer, settings?.getString("serverPin", "") ?: "")
                    if (processedUrl.isNotEmpty()) R.color.green else R.color.md_red_700
                }
                status is NetworkStatus.Disconnected -> R.color.md_red_700
                else -> R.color.md_yellow_600
            }

            fragmentHomeBellBinding.cardProfileBell.imageView.borderColor = ContextCompat.getColor(requireContext(), colorRes)
        }
    }

    private fun checkPendingSurveys() {
        val pendingSurveys = getPendingSurveys(user?.id, mRealm)

        if (pendingSurveys.isNotEmpty()) {
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
                .create()

            val adapter = SurveyAdapter(surveyTitles, { position ->
                val selectedSurvey = pendingSurveys[position].id
                AdapterMySubmission.openSurvey(homeItemClickListener, selectedSurvey, true)
            }, alertDialog)

            recyclerView.adapter = adapter
            alertDialog.show()
            alertDialog.window?.setBackgroundDrawableResource(R.color.card_bg)
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
        fragmentHomeBellBinding.homeCardTeams.llHomeTeam.setOnClickListener { homeItemClickListener?.openCallFragment(TeamFragment()) }
        fragmentHomeBellBinding.homeCardLibrary.myLibraryImageButton.setOnClickListener {
            if (user?.id?.startsWith("guest") == true) {
                guestDialog(requireContext())
            } else {
                openHelperFragment(ResourcesFragment())
            }
        }
        fragmentHomeBellBinding.homeCardCourses.myCoursesImageButton.setOnClickListener {
            if (user?.id?.startsWith("guest") == true) {
                guestDialog(requireContext())
            } else {
                openHelperFragment(CoursesFragment())
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
        super.onDestroyView()
    }
}
