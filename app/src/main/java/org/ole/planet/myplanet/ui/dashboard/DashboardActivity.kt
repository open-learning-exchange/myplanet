package org.ole.planet.myplanet.ui.dashboard

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuItemCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.mikepenz.materialdrawer.AccountHeader
import com.mikepenz.materialdrawer.AccountHeaderBuilder
import com.mikepenz.materialdrawer.Drawer
import com.mikepenz.materialdrawer.DrawerBuilder
import com.mikepenz.materialdrawer.holder.DimenHolder
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.Nameable
import io.realm.Case
import io.realm.RealmChangeListener
import io.realm.RealmObject
import io.realm.RealmResults
import org.json.JSONArray
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.base.BaseResourceFragment
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.ActivityDashboardBinding
import org.ole.planet.myplanet.databinding.CustomTabBinding
import org.ole.planet.myplanet.datamanager.Service
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserChallengeActions
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.SettingActivity
import org.ole.planet.myplanet.ui.chat.ChatHistoryListFragment
import org.ole.planet.myplanet.ui.community.CommunityTabFragment
import org.ole.planet.myplanet.ui.courses.CoursesFragment
import org.ole.planet.myplanet.ui.courses.MyProgressFragment
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment
import org.ole.planet.myplanet.ui.dashboard.notification.NotificationListener
import org.ole.planet.myplanet.ui.dashboard.notification.NotificationsFragment
import org.ole.planet.myplanet.ui.feedback.FeedbackListFragment
import org.ole.planet.myplanet.ui.resources.ResourceDetailFragment
import org.ole.planet.myplanet.ui.resources.ResourcesFragment
import org.ole.planet.myplanet.ui.survey.SendSurveyFragment
import org.ole.planet.myplanet.ui.survey.SurveyFragment
import org.ole.planet.myplanet.ui.sync.DashboardElementActivity
import org.ole.planet.myplanet.ui.team.TeamFragment
import org.ole.planet.myplanet.ui.userprofile.BecomeMemberActivity
import org.ole.planet.myplanet.utilities.BottomNavigationViewHelper.disableShiftMode
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.showBetaFeature
import org.ole.planet.myplanet.utilities.DialogUtils.guestDialog
import org.ole.planet.myplanet.utilities.FileUtils.totalAvailableMemoryRatio
import org.ole.planet.myplanet.utilities.KeyboardUtils.setupUI
import org.ole.planet.myplanet.utilities.LocaleHelper
import org.ole.planet.myplanet.utilities.MarkdownDialog
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities.toast
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.UUID
import kotlin.math.ceil

class DashboardActivity : DashboardElementActivity(), OnHomeItemClickListener, NavigationBarView.OnItemSelectedListener, NotificationListener {
    private lateinit var activityDashboardBinding: ActivityDashboardBinding
    private var headerResult: AccountHeader? = null
    var user: RealmUserModel? = null
    var result: Drawer? = null
    private var menul: TabLayout.Tab? = null
    private var menuh: TabLayout.Tab? = null
    private var menuc: TabLayout.Tab? = null
    private var menue: TabLayout.Tab? = null
    private var menuco: TabLayout.Tab? = null
    private var menut: TabLayout.Tab? = null
    private var tl: TabLayout? = null
    private var dl: DrawerLayout? = null
    private val realmListeners = mutableListOf<RealmListener>()

    private interface RealmListener {
        fun removeListener()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkUser()
        activityDashboardBinding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(activityDashboardBinding.root)
        setupUI(activityDashboardBinding.activityDashboardParentLayout, this@DashboardActivity)
        setSupportActionBar(activityDashboardBinding.myToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setTitle(R.string.app_project_name)
        activityDashboardBinding.myToolbar.setTitleTextColor(Color.WHITE)
        activityDashboardBinding.myToolbar.setSubtitleTextColor(Color.WHITE)
        navigationView = activityDashboardBinding.topBarNavigation
        disableShiftMode(navigationView)
        activityDashboardBinding.appBarBell.bellToolbar.inflateMenu(R.menu.menu_bell_dashboard)
        service = Service(this)
        tl = findViewById(R.id.tab_layout)
        activityDashboardBinding.root.viewTreeObserver.addOnGlobalLayoutListener {
            topBarVisible()
        }
        try {
            val userProfileModel = profileDbHandler.userModel
            if (userProfileModel != null) {
                var name: String? = userProfileModel.getFullName()
                if (name.isNullOrBlank()) {
                    name = profileDbHandler.userModel?.name
                }
                activityDashboardBinding.appBarBell.appTitleName.text = getString(R.string.planet_name, name)
            } else {
                activityDashboardBinding.appBarBell.appTitleName.text = getString(R.string.app_project_name)
            }
        } catch (err: Exception) {
            throw RuntimeException(err)
        }
        activityDashboardBinding.appBarBell.ivSetting.setOnClickListener {
            startActivity(Intent(this, SettingActivity::class.java))
        }
        if ((user != null) && user?.rolesList?.isEmpty() == true && !user?.userAdmin!!) {
            navigationView.visibility = View.GONE
            openCallFragment(InactiveDashboardFragment(), "Dashboard")
            return
        }
        navigationView.setOnItemSelectedListener(this)
        navigationView.visibility = if (UserProfileDbHandler(this).userModel?.isShowTopbar == true) {
            View.VISIBLE
        } else {
            View.GONE
        }
        headerResult = accountHeader
        createDrawer()
        if (!(user?.id?.startsWith("guest") == true && profileDbHandler.offlineVisits >= 3) && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            result?.openDrawer()
        }
        result?.stickyFooter?.setPadding(0, 0, 0, 0)
        result?.actionBarDrawerToggle?.isDrawerIndicatorEnabled = true
        dl = result?.drawerLayout
        topbarSetting()
        if (intent != null && intent.hasExtra("fragmentToOpen")) {
            val fragmentToOpen = intent.getStringExtra("fragmentToOpen")
            if (("feedbackList" == fragmentToOpen)) {
                openMyFragment(FeedbackListFragment())
            }
        } else {
            openCallFragment(BellDashboardFragment())
            activityDashboardBinding.appBarBell.bellToolbar.visibility = View.VISIBLE
        }

        activityDashboardBinding.appBarBell.ivSync.setOnClickListener {
            logSyncInSharedPrefs()
        }

        activityDashboardBinding.appBarBell.imgLogo.setOnClickListener { result?.openDrawer() }
        activityDashboardBinding.appBarBell.bellToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_chat -> {
                    if (user?.id?.startsWith("guest") == false) {
                        openCallFragment(
                            ChatHistoryListFragment(),
                            ChatHistoryListFragment::class.java.simpleName
                        )
                    } else {
                        guestDialog(this)
                    }
                }
                R.id.menu_goOnline -> wifiStatusSwitch()
                R.id.action_sync -> logSyncInSharedPrefs()
                R.id.action_feedback -> {
                    if (user?.id?.startsWith("guest") == false) {
                        openCallFragment(
                            FeedbackListFragment(),
                            FeedbackListFragment::class.java.simpleName
                        )
                    } else {
                        guestDialog(this)
                    }
                }
                R.id.action_settings -> startActivity(Intent(this@DashboardActivity, SettingActivity::class.java))
                R.id.action_disclaimer -> openCallFragment(DisclaimerFragment(), DisclaimerFragment::class.java.simpleName)
                R.id.action_about -> openCallFragment(AboutFragment(), AboutFragment::class.java.simpleName)
                R.id.action_logout -> logout()
                else -> {}
            }
            true
        }
        menuh = tl?.getTabAt(0)
        menul = tl?.getTabAt(1)
        menuc = tl?.getTabAt(2)
        menut = tl?.getTabAt(3)
        menue = tl?.getTabAt(4)
        menuco = tl?.getTabAt(5)
        hideWifi()
        setupRealmListeners()
        checkAndCreateNewNotifications()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (result != null && result?.isDrawerOpen == true) {
                    result?.closeDrawer()
                } else {
                    if (supportFragmentManager.backStackEntryCount > 1) {
                        supportFragmentManager.popBackStack()
                    } else {
                        if (!doubleBackToExitPressedOnce) {
                            doubleBackToExitPressedOnce = true
                            toast(MainApplication.context, getString(R.string.press_back_again_to_exit))
                            Handler(Looper.getMainLooper()).postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
                        } else {
                            val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                            if (fragment is BaseContainerFragment) {
                                fragment.handleBackPressed()
                            }
                            finish()
                        }
                    }
                }
            }
        })

        val startTime = 1730419200000
        val endTime = 1734307200000

        val commVoiceResults = mRealm.where(RealmNews::class.java)
            .equalTo("userId", user?.id)
            .greaterThanOrEqualTo("time", startTime)
            .lessThanOrEqualTo("time", endTime)
            .findAll()

        val commVoice = commVoiceResults.filter { realmNews ->
            realmNews.viewIn?.let { viewInStr ->
                try {
                    val viewInArray = JSONArray(viewInStr)
                    for (i in 0 until viewInArray.length()) {
                        val viewInObj = viewInArray.getJSONObject(i)
                        if (viewInObj.optString("section") == "community") {
                            return@filter true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            false
        }

        val allCommVoiceResults = mRealm.where(RealmNews::class.java)
            .greaterThanOrEqualTo("time", startTime)
            .lessThanOrEqualTo("time", endTime)
            .findAll()

        val allCommVoice = allCommVoiceResults.filter { realmNews ->
            realmNews.viewIn?.let { viewInStr ->
                try {
                    val viewInArray = JSONArray(viewInStr)
                    for (i in 0 until viewInArray.length()) {
                        val viewInObj = viewInArray.getJSONObject(i)
                        if (viewInObj.optString("section") == "community") {
                            return@filter true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            false
        }

        fun getDateFromTimestamp(timestamp: Long): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd")
            return dateFormat.format(Date(timestamp))
        }

        val uniqueDates = commVoice
            .map { getDateFromTimestamp(it.time) }
            .distinct()

        val allUniqueDates = allCommVoice
            .map { getDateFromTimestamp(it.time) }
            .distinct()

        val courseData = MyProgressFragment.fetchCourseData(mRealm, user?.id)

        val courseId = "4e6b78800b6ad18b4e8b0e1e38a98cac"
        val progress = MyProgressFragment.getCourseProgress(courseData, courseId)

        val hasUnfinishedSurvey = mRealm.where(RealmStepExam::class.java)
            .equalTo("courseId", courseId)
            .equalTo("type", "survey")
            .findAll()
            .any { survey -> !TakeCourseFragment.existsSubmission(mRealm, survey.id, "survey") }

        val validUrls = listOf(
            "https://${BuildConfig.PLANET_GUATEMALA_URL}",
            "http://${BuildConfig.PLANET_XELA_URL}",
            "http://${BuildConfig.PLANET_URIUR_URL}",
            "http://${BuildConfig.PLANET_SANPABLO_URL}",
            "http://${BuildConfig.PLANET_EMBAKASI_URL}",
            "https://${BuildConfig.PLANET_VI_URL}"
        )

        val today = LocalDate.now()
        if (user?.id?.startsWith("guest") == false) {
            val endDate = LocalDate.of(2025, 1, 16)
            if (today.isAfter(LocalDate.of(2024, 11, 30)) && today.isBefore(endDate)) {
                if (settings.getString("serverURL", "") in validUrls) {
                    val course = mRealm.where(RealmMyCourse::class.java)
                        .equalTo("courseId", courseId)
                        .findFirst()
                    val courseName = course?.courseTitle

                    if (progress != null) {
                        val max = progress.get("max").asInt
                        val current = progress.get("current").asInt
                        val courseStatus = if (current == max) {
                            getString(R.string.course_completed, courseName)
                        } else {
                            getString(R.string.course_in_progress, courseName, current, max)
                        }
                        challengeDialog(uniqueDates.size, courseStatus, allUniqueDates.size, hasUnfinishedSurvey)
                    } else {
                        challengeDialog(uniqueDates.size, getString(R.string.course_not_started, courseName), allUniqueDates.size, hasUnfinishedSurvey)
                    }
                }
            }
        }
    }

    fun challengeDialog (voiceCount: Int, courseStatus: String, allVoiceCount: Int, hasUnfinishedSurvey: Boolean) {
        val voiceTaskDone = if (voiceCount >= 5) "✅" else "[ ]"
        val prereqsMet = courseStatus.contains("terminado", ignoreCase = true) && voiceCount >= 5
        var hasValidSync = false
        val syncTaskDone = if (prereqsMet) {
            hasValidSync = mRealm.where(RealmUserChallengeActions::class.java)
                .equalTo("userId", user?.id)
                .equalTo("actionType", "sync")
                .count() > 0

            if (hasValidSync) "✅" else "[ ]"
        } else "[ ]"
        val courseTaskDone = if (courseStatus.contains("terminado", ignoreCase = true)) "✅ $courseStatus" else "[ ] $courseStatus"

        val isCompleted = syncTaskDone.startsWith("✅") && voiceTaskDone.startsWith("✅") && courseTaskDone.startsWith("✅")

        val hasShownCongrats = settings.getBoolean("has_shown_congrats", false)

        if (isCompleted && hasShownCongrats) return

        if (isCompleted && !hasShownCongrats) {
            editor.putBoolean("has_shown_congrats", true).apply()
            val markdownContent = """
        ${getString(R.string.community_earnings, calculateCommunityProgress(allVoiceCount, hasUnfinishedSurvey))}
        ${getString(R.string.your_earnings, calculateIndividualProgress(voiceCount, hasUnfinishedSurvey))}
        ### ${getString(R.string.congratulations)} <br/>
    """.trimIndent()
            MarkdownDialog.newInstance(markdownContent, courseStatus, voiceCount, allVoiceCount, hasUnfinishedSurvey).show(supportFragmentManager, "markdown_dialog")
        } else {
            val cappedVoiceCount = minOf(voiceCount, 5)
            val voicesText = if (cappedVoiceCount > 0) {
                "$cappedVoiceCount ${getString(R.string.daily_voices)}"
            } else {
                ""
            }
            val markdownContent = """
        ${getString(R.string.community_earnings, calculateCommunityProgress(allVoiceCount, hasUnfinishedSurvey))}
        ${getString(R.string.your_earnings, calculateIndividualProgress(voiceCount, hasUnfinishedSurvey))}
        ### ${getString(R.string.per_survey, courseTaskDone)} <br/>
        ### ${getString(R.string.share_opinion)} $voicesText <br/>
        ### ${getString(R.string.remember_sync)} <br/>
    """.trimIndent()
            MarkdownDialog.newInstance(markdownContent, courseStatus, voiceCount, allVoiceCount, hasUnfinishedSurvey)
                .show(supportFragmentManager, "markdown_dialog")
        }
    }

    private fun calculateIndividualProgress(voiceCount: Int, hasUnfinishedSurvey: Boolean): Int {
        val earnedDollarsVoice = minOf(voiceCount, 5) * 2
        val earnedDollarsSurvey = if (!hasUnfinishedSurvey) 1 else 0
        val total = earnedDollarsVoice + earnedDollarsSurvey
        return total.coerceAtMost(500)
    }

    private fun calculateCommunityProgress (allVoiceCount: Int, hasUnfinishedSurvey: Boolean): Int {
        val earnedDollarsVoice = minOf(allVoiceCount, 5) * 2
        val earnedDollarsSurvey = if (!hasUnfinishedSurvey) 1 else 0
        val total = earnedDollarsVoice + earnedDollarsSurvey
        return total.coerceAtMost(11)
    }

    private fun setupRealmListeners() {
        setupListener {
            mRealm.where(RealmMyLibrary::class.java).findAllAsync()
        }

        setupListener {
            mRealm.where(RealmSubmission::class.java)
                .equalTo("userId", user?.id)
                .equalTo("type", "survey")
                .equalTo("status", "pending", Case.INSENSITIVE)
                .findAllAsync()
        }

        setupListener {
            mRealm.where(RealmTeamTask::class.java)
                .notEqualTo("status", "archived")
                .equalTo("completed", false)
                .equalTo("assignee", user?.id)
                .findAllAsync()
        }
    }

    private inline fun <reified T : RealmObject> setupListener(crossinline query: () -> RealmResults<T>) {
        val results = query()
        val listener = RealmChangeListener<RealmResults<T>> { _ ->
            checkAndCreateNewNotifications()
        }
        results.addChangeListener(listener)
        realmListeners.add(object : RealmListener {
            override fun removeListener() {
                results.removeChangeListener(listener)
            }
        })
    }

    private fun checkAndCreateNewNotifications() {
        if (mRealm.isInTransaction) {
            createNotifications()
        } else {
            mRealm.executeTransaction {
                createNotifications()
            }
        }

        updateNotificationBadge(getUnreadNotificationsSize()) {
            openNotificationsList(user?.id ?: "")
        }
    }

    private fun createNotifications() {
        updateResourceNotification()

        val pendingSurveys = getPendingSurveys(user?.id)
        val surveyTitles = getSurveyTitlesFromSubmissions(pendingSurveys)
        surveyTitles.forEach { title ->
            createNotificationIfNotExists("survey", "$title", title)
        }

        val tasks = mRealm.where(RealmTeamTask::class.java)
            .notEqualTo("status", "archived")
            .equalTo("completed", false)
            .equalTo("assignee", user?.id)
            .findAll()
        tasks.forEach { task ->
            createNotificationIfNotExists("task","${task.title} ${formatDate(task.deadline)}", task.id)
        }

        val storageRatio = totalAvailableMemoryRatio
        createNotificationIfNotExists("storage", "$storageRatio" , "storage")
    }

    private fun updateResourceNotification() {
        val resourceCount = BaseResourceFragment.getLibraryList(mRealm, user?.id).size
        if (resourceCount > 0) {
            val existingNotification = mRealm.where(RealmNotification::class.java)
                .equalTo("userId", user?.id)
                .equalTo("type", "resource")
                .findFirst()

            if (existingNotification != null) {
                existingNotification.message = "$resourceCount"
                existingNotification.relatedId = "$resourceCount"
            } else {
                createNotificationIfNotExists("resource", "$resourceCount", "$resourceCount")
            }
        } else {
            mRealm.where(RealmNotification::class.java)
                .equalTo("userId", user?.id)
                .equalTo("type", "resource")
                .findFirst()?.deleteFromRealm()
        }
    }

    private fun openNotificationsList(userId: String) {
        val fragment = NotificationsFragment().apply {
            arguments = Bundle().apply {
                putString("userId", userId)
            }
            setNotificationUpdateListener(this@DashboardActivity)
        }
        openCallFragment(fragment)
    }

    override fun onNotificationCountUpdated(unreadCount: Int) {
        updateNotificationBadge(unreadCount) {
            openNotificationsList(user?.id ?: "")
        }
    }

    private fun createNotificationIfNotExists(type: String, message: String, relatedId: String?) {
        val existingNotification = mRealm.where(RealmNotification::class.java)
            .equalTo("userId", user?.id)
            .equalTo("type", type)
            .equalTo("relatedId", relatedId)
            .findFirst()

        if (existingNotification == null) {
            mRealm.createObject(RealmNotification::class.java, "${UUID.randomUUID()}").apply {
                this.userId = user?.id ?: ""
                this.type = type
                this.message = message
                this.relatedId = relatedId
                this.createdAt = Date()
            }
        }
    }

    private fun getPendingSurveys(userId: String?): List<RealmSubmission> {
        return mRealm.where(RealmSubmission::class.java)
            .equalTo("userId", userId)
            .equalTo("type", "survey")
            .equalTo("status", "pending", Case.INSENSITIVE)
            .findAll()
    }

    private fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>): List<String> {
        val titles = mutableListOf<String>()
        submissions.forEach { submission ->
            val examId = submission.parentId?.split("@")?.firstOrNull() ?: ""
            val exam = mRealm.where(RealmStepExam::class.java)
                .equalTo("id", examId)
                .findFirst()
            exam?.name?.let { titles.add(it) }
        }
        return titles
    }

    private fun updateNotificationBadge(count: Int, onClickListener: View.OnClickListener) {
        val menuItem = activityDashboardBinding.appBarBell.bellToolbar.menu.findItem(R.id.action_notifications)
        val actionView = MenuItemCompat.getActionView(menuItem)
        val smsCountTxt = actionView.findViewById<TextView>(R.id.notification_badge)
        smsCountTxt.text = "$count"
        smsCountTxt.visibility = if (count > 0) View.VISIBLE else View.GONE
        actionView.setOnClickListener(onClickListener)
    }

    private fun getUnreadNotificationsSize(): Int {
        return mRealm.where(RealmNotification::class.java)
            .equalTo("userId", user?.id)
            .equalTo("isRead", false)
            .count()
            .toInt()
    }

    fun refreshChatHistoryList() {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (fragment is ChatHistoryListFragment) {
            fragment.refreshChatHistoryList()
        }
    }

    private fun hideWifi() {
        val navMenu = activityDashboardBinding.appBarBell.bellToolbar.menu
        navMenu.findItem(R.id.menu_goOnline)
            .setVisible((showBetaFeature(Constants.KEY_SYNC, this)))
    }

    private fun checkUser() {
        user = UserProfileDbHandler(this).userModel
        if (user == null) {
            toast(this, getString(R.string.session_expired))
            logout()
            return
        }
        if (user?.id?.startsWith("guest") == true && profileDbHandler.offlineVisits >= 3) {
            val builder = AlertDialog.Builder(this, R.style.AlertDialogTheme)
            builder.setTitle(getString(R.string.become_a_member))
            builder.setMessage(getString(R.string.trial_period_ended))
            builder.setCancelable(false)
            builder.setPositiveButton(getString(R.string.become_a_member), null)
            builder.setNegativeButton(getString(R.string.menu_logout), null)
            val dialog = builder.create()
            dialog.show()
            val becomeMember = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val logout = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            becomeMember.contentDescription = getString(R.string.confirm_membership)
            logout.contentDescription = getString(R.string.menu_logout)
            becomeMember.setOnClickListener {
                val guest = true
                val intent = Intent(this, BecomeMemberActivity::class.java)
                intent.putExtra("username", profileDbHandler.userModel?.name)
                intent.putExtra("guest", guest)
                setResult(RESULT_OK, intent)
                startActivity(intent)
            }
            logout.setOnClickListener {
                dialog.dismiss()
                logout()
            }
        }
    }

    private fun topBarVisible(){
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)

        tabLayout.visibility = if (isLandscape) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun topbarSetting() {
        UITheme()
        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)
        tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                onClickTabItems(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                onClickTabItems(tab.position)
            }
        })
        for (i in 0 until tabLayout.tabCount) {
            val customTabBinding = CustomTabBinding.inflate(LayoutInflater.from(this))
            val title = customTabBinding.title
            val icon = customTabBinding.icon
            title.text = tabLayout.getTabAt(i)?.text
            icon.setImageResource(R.drawable.ic_home)
            icon.setImageDrawable(tabLayout.getTabAt(i)?.icon)
            tabLayout.getTabAt(i)?.setCustomView(customTabBinding.root)
        }
        tabLayout.isTabIndicatorFullWidth = false
    }

    private fun UITheme() {
        activityDashboardBinding.appBarBell.bellToolbar.visibility = View.VISIBLE
        activityDashboardBinding.myToolbar.visibility = View.GONE
        navigationView.visibility = View.GONE
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (user?.rolesList?.isEmpty() == true) {
            menu.findItem(R.id.action_setting).isEnabled = false
        }
        return super.onPrepareOptionsMenu(menu)
    }

    private val accountHeader: AccountHeader
        get() {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val density = displayMetrics.density

            var paddingVerticalPx = screenHeight * 0.15
            var paddingHorizontalPx = screenWidth * 0.15
            if(screenWidth > screenHeight){ //sizing for tablets
                paddingVerticalPx = screenHeight * 0.05
                paddingHorizontalPx = screenWidth * 0.05
            }

            val paddingVerticalDp = (paddingVerticalPx / density).toInt()
            val paddingHorizontalDp = (paddingHorizontalPx / density).toInt()
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusBarHeight = if (resourceId > 0) {
                resources.getDimensionPixelSize(resourceId)
            } else {
                ceil(25 * density).toInt()
            }

            val header = AccountHeaderBuilder()
                .withActivity(this@DashboardActivity)
                .withTextColor(ContextCompat.getColor(this, R.color.bg_white))
                .withHeaderBackground(R.drawable.ole_logo)
                .withDividerBelowHeader(false)
                .withTranslucentStatusBar(false)
                .withHeightDp(paddingVerticalDp + 20 * 2 + (statusBarHeight / density).toInt())
                .build()
            val headerBackground = header.headerBackgroundView
            headerBackground.setPadding(
                paddingHorizontalDp, paddingVerticalDp + statusBarHeight + 25,
                paddingHorizontalDp, paddingVerticalDp + 50
            )

            val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_NO ||
                (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM && currentNightMode == Configuration.UI_MODE_NIGHT_NO)) {
                headerBackground.setColorFilter(
                    ContextCompat.getColor(this, R.color.md_white_1000),
                    PorterDuff.Mode.SRC_IN
                )
            }
            return header
        }

    private fun createDrawer() {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            ceil(25 * resources.displayMetrics.density).toInt()
        }

        val headerHeight = 160 + (statusBarHeight / resources.displayMetrics.density).toInt()
        val dimenHolder = DimenHolder.fromDp(headerHeight)

        result = headerResult?.let {
            DrawerBuilder().withActivity(this).withFullscreen(false).withTranslucentStatusBar(false)
                .withSliderBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
                .withToolbar(activityDashboardBinding.myToolbar)
                .withAccountHeader(it).withHeaderHeight(dimenHolder)
                .addDrawerItems(*drawerItems).addStickyDrawerItems(*drawerItemsFooter)
                .withOnDrawerItemClickListener { _: View?, _: Int, drawerItem: IDrawerItem<*, *>? ->
                    if (drawerItem != null) {
                        result?.setSelection(drawerItem.identifier, false)
                        menuAction((drawerItem as Nameable<*>).name.textRes)
                    }
                    false
                }.withDrawerWidthDp(200).build()
        }
    }

    private fun menuAction(selectedMenuId: Int) {
        when (selectedMenuId) {
            R.string.menu_myplanet -> openCallFragment(BellDashboardFragment())
            R.string.menu_library -> openCallFragment(ResourcesFragment())
            R.string.menu_meetups -> {}
            R.string.menu_surveys -> openCallFragment(SurveyFragment())
            R.string.menu_courses -> openCallFragment(CoursesFragment())
            R.string.menu_community -> openCallFragment(CommunityTabFragment())
            R.string.txt_myLibrary -> {
                if (user?.id?.startsWith("guest") == true) {
                    guestDialog(this)
                } else {
                    openMyFragment(ResourcesFragment())
                }
            }
            R.string.team -> openMyFragment(TeamFragment())
            R.string.txt_myCourses -> {
                if (user?.id?.startsWith("guest") == true) {
                    guestDialog(this)
                } else {
                    openMyFragment(CoursesFragment())
                }
            }
            R.string.enterprises -> openEnterpriseFragment()
            R.string.menu_logout -> logout()
            else -> openCallFragment(BellDashboardFragment())
        }
    }

    override fun openMyFragment(f: Fragment) {
        val b = Bundle()
        b.putBoolean("isMyCourseLib", true)
        f.arguments = b
        val fragmentName = f::class.java.simpleName
        val tag = "My$fragmentName"
        when (tag) {
            "MyCoursesFragment" -> result?.setSelection(2, false)
            "MyResourcesFragment" -> result?.setSelection(1, false)
            else -> {
                result?.setSelection(0, false)
            }
        }
        openCallFragment(f, tag)
    }

    override fun onDestroy() {
        super.onDestroy()
        profileDbHandler.onDestroy()

        realmListeners.forEach { it.removeListener() }
        realmListeners.clear()
    }

    override fun openCallFragment(f: Fragment) {
        val tag = f::class.java.simpleName
        openCallFragment(f,tag)
    }

    override fun openLibraryDetailFragment(library: RealmMyLibrary?) {
        val f: Fragment = ResourceDetailFragment()
        val b = Bundle()
        b.putString("libraryId", library?.resourceId)
        f.arguments = b
        openCallFragment(f)
    }

    override fun sendSurvey(current: RealmStepExam?) {
        val f = SendSurveyFragment()
        val b = Bundle()
        b.putString("surveyId", current?.id)
        f.arguments = b
        f.show(supportFragmentManager, "")
    }

    private val drawerItems: Array<IDrawerItem<*, *>>
        get() {
            val menuImageList = ArrayList<Drawable>()
            ResourcesCompat.getDrawable(resources, R.drawable.myplanet, null)?.let { menuImageList.add(it) }
            ResourcesCompat.getDrawable(resources, R.drawable.ourlibrary, null)?.let { menuImageList.add(it) }
            ResourcesCompat.getDrawable(resources, R.drawable.ourcourses, null)?.let { menuImageList.add(it) }
            ResourcesCompat.getDrawable(resources, R.drawable.mylibrary, null)?.let { menuImageList.add(it) }
            ResourcesCompat.getDrawable(resources, R.drawable.mycourses, null)?.let { menuImageList.add(it) }
            ResourcesCompat.getDrawable(resources, R.drawable.team, null)?.let { menuImageList.add(it) }
            ResourcesCompat.getDrawable(resources, R.drawable.business, null)?.let { menuImageList.add(it) }
            ResourcesCompat.getDrawable(resources, R.drawable.community, null)?.let { menuImageList.add(it) }
            ResourcesCompat.getDrawable(resources, R.drawable.survey, null)?.let { menuImageList.add(it) }
            return arrayOf(
                changeUX(R.string.menu_myplanet, menuImageList[0]).withIdentifier(0),
                changeUX(R.string.txt_myLibrary, menuImageList[1]).withIdentifier(1),
                changeUX(R.string.txt_myCourses, menuImageList[2]).withIdentifier(2),
                changeUX(R.string.menu_library, menuImageList[3]),
                changeUX(R.string.menu_courses, menuImageList[4]),
                changeUX(R.string.team, menuImageList[5]),
                changeUX(R.string.menu_community, menuImageList[7]),
                changeUX(R.string.enterprises, menuImageList[6]),
                changeUX(R.string.menu_surveys, menuImageList[8])
            )
        }
    private val drawerItemsFooter: Array<IDrawerItem<*, *>>
        get() {
            val menuImageListFooter = ArrayList<Drawable>()
            ResourcesCompat.getDrawable(resources, R.drawable.logout, null)?.let { menuImageListFooter.add(it) }
            return arrayOf(changeUX(R.string.menu_logout, menuImageListFooter[0]))
        }

    private fun changeUX(iconText: Int, drawable: Drawable?): PrimaryDrawerItem {
        return PrimaryDrawerItem().withName(iconText)
            .withIcon(drawable)
            .withTextColor(ContextCompat.getColor(this, R.color.textColorPrimary))
            .withSelectedTextColor(ContextCompat.getColor(this, R.color.primary_dark))
            .withIconColor(ContextCompat.getColor(this, R.color.textColorPrimary))
            .withSelectedIconColor(ContextCompat.getColor(this, R.color.primary_dark))
            .withSelectedColor(ContextCompat.getColor(this, R.color.textColorPrimary))
            .withIconTintingEnabled(true)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        item.isChecked = true
        when (item.itemId) {
            R.id.menu_library -> {
                openCallFragment(ResourcesFragment())
            }
            R.id.menu_courses -> {
                openCallFragment(CoursesFragment())
            }
            R.id.menu_mycourses -> {
                if (user?.id?.startsWith("guest") == true) {
                    guestDialog(this)
                } else {
                    openMyFragment(CoursesFragment())
                }
            }
            R.id.menu_mylibrary -> {
                if (user?.id?.startsWith("guest") == true) {
                    guestDialog(this)
                } else {
                    openMyFragment(ResourcesFragment())
                }
            }
            R.id.menu_enterprises -> {
                openEnterpriseFragment()
            }
            R.id.menu_home -> {
                openCallFragment(BellDashboardFragment())
            }
        }
        item.isChecked = true
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_bell_dashboard, menu)
        menu.findItem(R.id.menu_goOnline).isVisible = showBetaFeature(Constants.KEY_SYNC, this)
        return super.onCreateOptionsMenu(menu)
    }

    companion object {
        const val MESSAGE_PROGRESS = "message_progress"
    }
}
